package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.*;
import com.ghostcommunity.backend.payload.MessageResponse;
import com.ghostcommunity.backend.repository.*;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final UserRepository userRepository;

    // Lightweight DTO for creating a poll
    public record CreatePollRequest(String question, List<String> options) {}

    // 1. Fetch live poll data with current user vote status
    @GetMapping("/{pollId}")
    public ResponseEntity<?> getPoll(@PathVariable Long pollId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long myId = null;
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl u) {
            myId = u.getId();
        }

        Poll poll = pollRepository.findById(pollId).orElse(null);
        if (poll == null) {
            return ResponseEntity.notFound().build();
        }

        List<PollOption> options = pollOptionRepository.findByPollIdOrderByIdAsc(pollId);
        Long votedOptionId = null;
        boolean hasVoted = false;
        if (myId != null) {
            var voteOpt = pollVoteRepository.findByPollIdAndUserId(pollId, myId);
            if (voteOpt.isPresent()) {
                hasVoted = true;
                votedOptionId = voteOpt.get().getOption() != null ? voteOpt.get().getOption().getId() : null;
            }
        }

        int totalVotes = options.stream().mapToInt(PollOption::getVoteCount).sum();

        List<Map<String, Object>> optionMaps = options.stream().map(o -> {
            Map<String, Object> om = new java.util.HashMap<>();
            om.put("id", o.getId());
            om.put("text", o.getText());
            om.put("voteCount", o.getVoteCount());
            return om;
        }).toList();

        Map<String, Object> res = new java.util.HashMap<>();
        res.put("id", poll.getId());
        res.put("question", poll.getQuestion());
        res.put("options", optionMaps);
        res.put("totalVotes", totalVotes);
        res.put("hasVoted", hasVoted);
        res.put("votedOptionId", votedOptionId);

        return ResponseEntity.ok(res);
    }

    @PostMapping("/create")
    public ResponseEntity<?> createPoll(@RequestBody CreatePollRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User author = userRepository.findById(userDetails.getId()).orElseThrow();

        if (request.options().size() < 2) {
            return ResponseEntity.badRequest().body(new MessageResponse("A poll must have at least 2 options."));
        }

        // 1. Save the Poll Question
        Poll poll = Poll.builder()
                .question(request.question())
                .author(author)
                .build();
        poll = pollRepository.save(poll);

        // 2. Save the Options dynamically
        List<Map<String, Object>> savedOptions = new java.util.ArrayList<>();
        for (String optionText : request.options()) {
            PollOption option = PollOption.builder()
                    .text(optionText)
                    .poll(poll)
                    .voteCount(0)
                    .build();
            option = pollOptionRepository.save(option);
            savedOptions.add(Map.of("id", option.getId(), "text", option.getText(), "voteCount", 0));
        }

        // Return the Poll ID and options to the mobile app
        return ResponseEntity.ok(Map.of(
                "pollId", poll.getId(),
                "question", poll.getQuestion(),
                "options", savedOptions,
                "message", "Poll created successfully"
        ));
    }

    @PostMapping("/{pollId}/vote/{optionId}")
    public ResponseEntity<?> voteOnPoll(@PathVariable Long pollId, @PathVariable Long optionId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new RuntimeException("Poll not found"));

        PollOption option = pollOptionRepository.findById(optionId)
                .orElseThrow(() -> new RuntimeException("Option not found"));

        if (!option.getPoll().getId().equals(poll.getId())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid option for this poll."));
        }

        // WhatsApp Style: Allow changing / switching vote
        var existingVoteOpt = pollVoteRepository.findByPollIdAndUserId(poll.getId(), user.getId());
        if (existingVoteOpt.isPresent()) {
            PollVote existing = existingVoteOpt.get();
            if (existing.getOption().getId().equals(option.getId())) {
                return ResponseEntity.ok(new MessageResponse("Vote already recorded"));
            }
            PollOption prevOpt = existing.getOption();
            prevOpt.setVoteCount(Math.max(0, prevOpt.getVoteCount() - 1));
            pollOptionRepository.save(prevOpt);

            existing.setOption(option);
            pollVoteRepository.save(existing);

            option.setVoteCount(option.getVoteCount() + 1);
            pollOptionRepository.save(option);

            return ResponseEntity.ok(new MessageResponse("Vote switched successfully!"));
        }

        // Record the new vote
        PollVote vote = PollVote.builder()
                .poll(poll)
                .option(option)
                .user(user)
                .build();
        pollVoteRepository.save(vote);

        // Increment the public vote count
        option.setVoteCount(option.getVoteCount() + 1);
        pollOptionRepository.save(option);

        return ResponseEntity.ok(new MessageResponse("Vote cast successfully!"));
    }
}

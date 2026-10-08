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
        for (String optionText : request.options()) {
            PollOption option = PollOption.builder()
                    .text(optionText)
                    .poll(poll)
                    .voteCount(0)
                    .build();
            pollOptionRepository.save(option);
        }

        // Return the Poll ID to the mobile app so it can broadcast it via WebSocket
        return ResponseEntity.ok(Map.of(
                "pollId", poll.getId(), 
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

        // The Ultimate Database Security Check: Has this user already voted?
        if (pollVoteRepository.existsByPollIdAndUserId(poll.getId(), user.getId())) {
            return ResponseEntity.badRequest().body(new MessageResponse("You have already voted on this poll!"));
        }

        // Record the secret vote privately
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

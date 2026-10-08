package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.Block;
import com.ghostcommunity.backend.model.Message;
import com.ghostcommunity.backend.model.Report;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.payload.MessageResponse;
import com.ghostcommunity.backend.repository.BlockRepository;
import com.ghostcommunity.backend.repository.MessageRepository;
import com.ghostcommunity.backend.repository.ReportRepository;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;
    private final BlockRepository blockRepository;

    @PostMapping("/report/message/{messageId}")
    public ResponseEntity<?> reportMessage(@PathVariable Long messageId, @RequestBody Map<String, String> payload) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User reporter = userRepository.findById(userDetails.getId()).orElseThrow();

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Secretly map the report to the actual author of the message
        Report report = Report.builder()
                .reporter(reporter)
                .reportedUser(message.getAuthor())
                .reportedMessage(message)
                .reason(payload.getOrDefault("reason", "Inappropriate content"))
                .status("PENDING")
                .build();

        reportRepository.save(report);

        return ResponseEntity.ok(new MessageResponse("Message reported successfully. Admins will review it."));
    }

    @PostMapping("/block/message/{messageId}")
    public ResponseEntity<?> blockAuthorOfMessage(@PathVariable Long messageId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User blocker = userRepository.findById(userDetails.getId()).orElseThrow();

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        User blockedUser = message.getAuthor();

        if (blocker.getId().equals(blockedUser.getId())) {
            return ResponseEntity.badRequest().body(new MessageResponse("You cannot block yourself."));
        }

        if (!blockRepository.existsByBlockerIdAndBlockedId(blocker.getId(), blockedUser.getId())) {
            Block block = Block.builder()
                    .blocker(blocker)
                    .blocked(blockedUser)
                    .build();
            blockRepository.save(block);
        }

        return ResponseEntity.ok(new MessageResponse("You will no longer see messages from this Ghost."));
    }
}

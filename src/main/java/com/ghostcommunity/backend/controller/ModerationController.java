package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.Block;
import com.ghostcommunity.backend.model.Message;
import com.ghostcommunity.backend.model.Post;
import com.ghostcommunity.backend.model.Report;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.payload.MessageResponse;
import com.ghostcommunity.backend.repository.BlockRepository;
import com.ghostcommunity.backend.repository.DirectMessageRepository;
import com.ghostcommunity.backend.repository.MessageRepository;
import com.ghostcommunity.backend.repository.PostRepository;
import com.ghostcommunity.backend.repository.ReportRepository;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final DirectMessageRepository directMessageRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final BlockRepository blockRepository;

    @PostMapping("/report")
    @Transactional
    public ResponseEntity<?> reportAnyMessage(@RequestBody Map<String, Object> payload) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User reporter = userRepository.findById(userDetails.getId()).orElseThrow();

        String chatType = (String) payload.getOrDefault("chatType", "COMMUNITY");
        String reason = (String) payload.getOrDefault("reason", "Inappropriate content");
        String content = (String) payload.getOrDefault("content", "");
        String mediaUrl = (String) payload.getOrDefault("mediaUrl", null);
        String mediaType = (String) payload.getOrDefault("mediaType", "TEXT");
        String sectionName = (String) payload.getOrDefault("sectionName", null);

        Long messageId = null;
        if (payload.get("messageId") != null) {
            try {
                messageId = Long.valueOf(payload.get("messageId").toString());
            } catch (Exception ignored) {}
        }
        if (messageId == null && payload.get("id") != null) {
            try {
                messageId = Long.valueOf(payload.get("id").toString());
            } catch (Exception ignored) {}
        }

        User reportedUser = null;
        Message reportedMessage = null;
        Long directMessageId = null;

        if ("POST".equalsIgnoreCase(chatType) || "FEED".equalsIgnoreCase(chatType)) {
            chatType = "POST";
            directMessageId = messageId;
            if (messageId != null) {
                var postOpt = postRepository.findById(messageId);
                if (postOpt.isPresent()) {
                    Post post = postOpt.get();
                    reportedUser = post.getAuthor();
                    if (content == null || content.isBlank()) {
                        content = post.getContent();
                    }
                    if (mediaUrl == null) {
                        mediaUrl = post.getImageUrl();
                    }
                    if (mediaUrl != null && !mediaUrl.isBlank()) {
                        mediaType = "IMAGE";
                    }
                    if (sectionName == null && post.getCollege() != null) {
                        sectionName = post.getCollege().getName();
                    }
                }
            }
        } else if ("DIRECT".equalsIgnoreCase(chatType) || "GHOST".equalsIgnoreCase(chatType)) {
            directMessageId = messageId;
            if (messageId != null) {
                var dmOpt = directMessageRepository.findById(messageId);
                if (dmOpt.isPresent()) {
                    var dm = dmOpt.get();
                    reportedUser = dm.getSender();
                    if (content == null || content.isBlank()) {
                        content = dm.getContent();
                    }
                    if (mediaType == null || "TEXT".equalsIgnoreCase(mediaType)) {
                        mediaType = dm.getMessageType();
                    }
                    if (mediaUrl == null && content != null && content.startsWith("http")) {
                        mediaUrl = content;
                    }
                }
            }
        } else {
            // Community message
            if (messageId != null) {
                var msgOpt = messageRepository.findById(messageId);
                if (msgOpt.isPresent()) {
                    reportedMessage = msgOpt.get();
                    reportedUser = msgOpt.get().getAuthor();
                    if (content == null || content.isBlank()) {
                        content = msgOpt.get().getContent();
                    }
                    if (mediaType == null || "TEXT".equalsIgnoreCase(mediaType)) {
                        mediaType = reportedMessage.getMessageType();
                    }
                    if (mediaUrl == null && content != null && content.startsWith("http")) {
                        mediaUrl = content;
                    }
                    if (sectionName == null && reportedMessage.getDestinationSection() != null) {
                        sectionName = reportedMessage.getDestinationSection().getName();
                    }
                }
            }
        }

        // Auto-detect media type and url from content if URL provided
        if (content != null && content.startsWith("http")) {
            if (mediaUrl == null) mediaUrl = content;
            String lower = content.toLowerCase();
            if (lower.contains(".mp4") || lower.contains(".mov") || lower.contains(".webm") || lower.contains("/video/")) {
                mediaType = "VIDEO";
            } else if (lower.contains(".mp3") || lower.contains(".wav") || lower.contains(".m4a") || lower.contains(".ogg") || lower.contains("audio")) {
                mediaType = "AUDIO";
            } else if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") || lower.contains(".webp") || lower.contains(".gif") || lower.contains("/image/")) {
                mediaType = "IMAGE";
            }
        }

        // Fallback: lookup user if senderId or senderGhostName passed
        if (reportedUser == null && payload.get("senderId") != null) {
            try {
                Long senderId = Long.valueOf(payload.get("senderId").toString());
                reportedUser = userRepository.findById(senderId).orElse(null);
            } catch (Exception ignored) {}
        }
        if (reportedUser == null && payload.get("senderGhostName") != null) {
            String gName = (String) payload.get("senderGhostName");
            if (reporter.getCollege() != null) {
                reportedUser = userRepository.findByGhostNameAndCollegeId(gName, reporter.getCollege().getId()).orElse(null);
            }
            if (reportedUser == null) {
                reportedUser = userRepository.findFirstByGhostName(gName).orElse(null);
            }
        }

        if (reportedUser == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Could not identify the message sender."));
        }

        Report report = Report.builder()
                .reporter(reporter)
                .reportedUser(reportedUser)
                .reportedMessage(reportedMessage)
                .directMessageId(directMessageId)
                .chatType(chatType.toUpperCase())
                .messageContent(content)
                .mediaUrl(mediaUrl)
                .mediaType(mediaType)
                .sectionName(sectionName != null ? sectionName : "General")
                .reason(reason)
                .status("PENDING")
                .adminAction("NONE")
                .build();

        report = reportRepository.save(report);
        log.info("Report #{} created by user #{} against user #{} (chatType={})",
                report.getId(), reporter.getId(), reportedUser.getId(), chatType);

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Report submitted successfully. Campus Admins will review the message.");
        resp.put("reportId", report.getId());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/report/message/{messageId}")
    @Transactional
    public ResponseEntity<?> reportMessage(@PathVariable Long messageId, @RequestBody(required = false) Map<String, String> payload) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User reporter = userRepository.findById(userDetails.getId()).orElseThrow();

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        String reason = (payload != null && payload.containsKey("reason")) ? payload.get("reason") : "Inappropriate content";

        Report report = Report.builder()
                .reporter(reporter)
                .reportedUser(message.getAuthor())
                .reportedMessage(message)
                .chatType("COMMUNITY")
                .messageContent(message.getContent())
                .mediaType(message.getMessageType())
                .sectionName(message.getDestinationSection() != null ? message.getDestinationSection().getName() : "All College")
                .reason(reason)
                .status("PENDING")
                .adminAction("NONE")
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

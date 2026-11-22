package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.Message;
import com.ghostcommunity.backend.model.Section;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.payload.ChatResponsePayload;
import com.ghostcommunity.backend.payload.MessageResponse;
import com.ghostcommunity.backend.repository.MessageReactionRepository;
import com.ghostcommunity.backend.repository.MessageRepository;
import com.ghostcommunity.backend.repository.SectionRepository;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.ghostcommunity.backend.repository.SectionReadRepository sectionReadRepository;
    private final MessageReactionRepository messageReactionRepository;

    /**
     * POST /api/messages/section/{sectionId}
     * Universal endpoint to send messages / post shares directly to a section.
     */
    @PostMapping("/section/{sectionId}")
    public ResponseEntity<?> sendMessageToSection(
            @PathVariable Long sectionId,
            @RequestBody java.util.Map<String, String> request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User author = userRepository.findByIdWithSectionAndCollege(userDetails.getId()).orElseThrow();
        Section destinationSection = sectionRepository.findById(sectionId).orElseThrow();

        if (!destinationSection.getCollege().getId().equals(author.getCollege().getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Security Violation"));
        }

        String content = request.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Content cannot be empty"));
        }
        String messageType = request.getOrDefault("messageType", "TEXT");

        Message message = Message.builder()
                .author(author)
                .college(author.getCollege())
                .destinationSection(destinationSection)
                .content(content)
                .messageType(messageType)
                .build();

        message = messageRepository.save(message);

        // Advance sender's read count so sender never sees own message as unread
        try {
            Long messageId = message.getId();
            long newTotal = messageRepository.countByDestinationSectionId(sectionId);
            var readOpt = sectionReadRepository.findByUserIdAndSectionId(author.getId(), sectionId);
            var sr = readOpt.orElseGet(() -> com.ghostcommunity.backend.model.SectionRead.builder().user(author).section(destinationSection).build());
            sr.setLastReadMessageId(messageId);
            sr.setLastReadCount(newTotal);
            sectionReadRepository.save(sr);
        } catch (Exception ignored) {}

        // Add war points for activity
        author.setWarPoints(author.getWarPoints() + 10);
        userRepository.save(author);

        ChatResponsePayload response = ChatResponsePayload.builder()
                .messageId(message.getId())
                .senderId(author.getId())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .ghostName(author.getGhostName())
                .primarySection(author.getPrimarySection() != null ? author.getPrimarySection().getName() : "")
                .gender(author.getGender())
                .createdAt(message.getCreatedAt())
                .isSeen(false)
                .build();

        messagingTemplate.convertAndSend("/topic/section/" + sectionId, response);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/messages/section/{sectionId}/history
     * Returns the last 50 messages for a section so the chat screen can
     * pre-populate history when the user opens the room.
     */
    @GetMapping("/section/{sectionId}/history")
    public ResponseEntity<?> getSectionHistory(@PathVariable Long sectionId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User currentUser = userRepository.findById(userDetails.getId()).orElseThrow();

        List<Message> messages = messageRepository.findByDestinationSectionIdOrderByCreatedAtAsc(sectionId);

        List<Object[]> reactionCounts = messageReactionRepository.countReactionsBySectionId(sectionId);
        Map<Long, Map<String, Long>> reactionMap = new HashMap<>();
        for (Object[] row : reactionCounts) {
            Long msgId = (Long) row[0];
            String emoji = (String) row[1];
            Long count = ((Number) row[2]).longValue();
            reactionMap.computeIfAbsent(msgId, k -> new HashMap<>()).put(emoji, count);
        }

        Long maxOthers = sectionReadRepository.findMaxLastReadMessageIdByOthers(sectionId, currentUser.getId());

        List<ChatResponsePayload> history = messages.stream()
                .filter(m -> m.getAuthor() != null)
                .map(m -> {
                    String secName = (m.getAuthor().getPrimarySection() != null)
                            ? m.getAuthor().getPrimarySection().getName()
                            : (m.getAuthor().isAdmin() ? "System" : "");
                    String ghost = (m.getAuthor().isAdmin())
                            ? "⚠️ ADMIN"
                            : (m.getAuthor().getGhostName() != null ? m.getAuthor().getGhostName() : "Ghost");
                    return ChatResponsePayload.builder()
                            .messageId(m.getId())
                            .senderId(m.getAuthor().getId())
                            .content(m.getContent())
                            .messageType(m.getMessageType())
                            .ghostName(ghost)
                            .primarySection(secName)
                            .gender(m.getAuthor().getGender())
                            .createdAt(m.getCreatedAt())
                            .reactions(reactionMap.getOrDefault(m.getId(), Collections.emptyMap()))
                            .isSeen(m.getAuthor().getId().equals(currentUser.getId()) && maxOthers != null && maxOthers >= m.getId())
                            .build();
                })
                .toList();

        // Mark section read when entering room to view history
        try {
            Long maxId = messageRepository.findMaxIdByDestinationSectionId(sectionId);
            long total = messageRepository.countByDestinationSectionId(sectionId);
            var readOpt = sectionReadRepository.findByUserIdAndSectionId(currentUser.getId(), sectionId);
            Section sec = sectionRepository.findById(sectionId).orElse(null);
            if (sec != null) {
                var sr = readOpt.orElseGet(() -> com.ghostcommunity.backend.model.SectionRead.builder().user(currentUser).section(sec).build());
                sr.setLastReadMessageId(maxId != null ? maxId : 0L);
                sr.setLastReadCount(total);
                sectionReadRepository.save(sr);

                // Broadcast live read event to section so senders' ticks turn pink-blue mix immediately
                if (maxId != null && maxId > 0) {
                    Map<String, Object> readEvent = new HashMap<>();
                    readEvent.put("action", "READ");
                    readEvent.put("sectionId", sectionId);
                    readEvent.put("lastReadMessageId", maxId);
                    readEvent.put("readerId", currentUser.getId());
                    readEvent.put("readerGhostName", currentUser.getGhostName());
                    messagingTemplate.convertAndSend("/topic/section/" + sectionId, (Object) readEvent);
                }
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(history);
    }

    @PostMapping("/section/{sectionId}/read")
    public ResponseEntity<?> markSectionRead(@PathVariable Long sectionId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
                User user = userRepository.findById(userDetails.getId()).orElse(null);
                if (user != null) {
                    Long maxId = messageRepository.findMaxIdByDestinationSectionId(sectionId);
                    long total = messageRepository.countByDestinationSectionId(sectionId);
                    var readOpt = sectionReadRepository.findByUserIdAndSectionId(user.getId(), sectionId);
                    Section sec = sectionRepository.findById(sectionId).orElse(null);
                    if (sec != null) {
                        var sr = readOpt.orElseGet(() -> com.ghostcommunity.backend.model.SectionRead.builder().user(user).section(sec).build());
                        sr.setLastReadMessageId(maxId != null ? maxId : 0L);
                        sr.setLastReadCount(total);
                        sectionReadRepository.save(sr);

                        if (maxId != null && maxId > 0) {
                            Map<String, Object> readEvent = new HashMap<>();
                            readEvent.put("action", "READ");
                            readEvent.put("sectionId", sectionId);
                            readEvent.put("lastReadMessageId", maxId);
                            readEvent.put("readerId", user.getId());
                            readEvent.put("readerGhostName", user.getGhostName());
                            messagingTemplate.convertAndSend("/topic/section/" + sectionId, (Object) readEvent);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok("READ");
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<?> deleteMessage(@PathVariable Long messageId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User currentUser = userRepository.findById(userDetails.getId()).orElseThrow();

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // 1. Strict Security: You can only delete your own message! (Unless you are an Admin)
        if (!message.getAuthor().getId().equals(currentUser.getId()) && !currentUser.isAdmin()) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: You can only delete your own messages."));
        }

        // 2. If it's Media (Image/Video), delete the actual file from the server's hard drive!
        if ("IMAGE".equals(message.getMessageType()) || "VIDEO".equals(message.getMessageType())) {
            try {
                String[] parts = message.getContent().split("/");
                String fileName = parts[parts.length - 1];
                java.nio.file.Path filePath = java.nio.file.Paths.get("uploads").resolve(fileName).normalize();
                java.nio.file.Files.deleteIfExists(filePath);
            } catch (Exception e) {
                System.out.println("Could not delete media file: " + e.getMessage());
            }
        }

        // 3. Delete it permanently from the database
        messageRepository.delete(message);

        // 4. Broadcast a "DELETE" event to the live WebSocket!
        String topic = message.getDestinationSection() != null
                ? "/topic/section/" + message.getDestinationSection().getId()
                : "/topic/college/" + message.getCollege().getId();

        messagingTemplate.convertAndSend(topic, "{\"action\": \"DELETE\", \"messageId\": " + messageId + "}");

        return ResponseEntity.ok(new MessageResponse("Message deleted successfully."));
    }

    // SECRET DEBUG ENDPOINT FOR TESTING GREEN DOTS
    @GetMapping("/simulate/{sectionId}")
    public ResponseEntity<?> simulateIncomingMessage(@PathVariable Long sectionId) {
        // Find a random user to be the author
        User author = userRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No users exist"));
        
        Section section = author.getCollege().getSections().stream()
                .filter(s -> s.getId().equals(sectionId))
                .findFirst()
                .orElse(author.getPrimarySection()); // Fallback to their own section

        Message fakeMessage = Message.builder()
                .author(author)
                .college(author.getCollege())
                .destinationSection(section)
                .content("Hello! This is a test message to trigger the green dot!")
                .messageType("TEXT")
                .build();
        
        messageRepository.save(fakeMessage);
        return ResponseEntity.ok(new MessageResponse("Simulated message inserted. Watch your phone!"));
    }
}



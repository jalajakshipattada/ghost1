package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.Message;
import com.ghostcommunity.backend.model.Section;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.payload.ChatResponsePayload;
import com.ghostcommunity.backend.payload.MessageResponse;
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

import java.util.List;

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
            long newTotal = messageRepository.countByDestinationSectionId(sectionId);
            var readOpt = sectionReadRepository.findByUserIdAndSectionId(author.getId(), sectionId);
            if (readOpt.isPresent()) {
                var sr = readOpt.get();
                sr.setLastReadCount(newTotal);
                sectionReadRepository.save(sr);
            } else {
                sectionReadRepository.save(com.ghostcommunity.backend.model.SectionRead.builder()
                        .user(author)
                        .section(destinationSection)
                        .lastReadCount(newTotal)
                        .build());
            }
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
        User currentUser = userRepository.findByIdWithSectionAndCollege(userDetails.getId()).orElseThrow();

        List<Message> messages = messageRepository.findByDestinationSectionIdOrderByCreatedAtAsc(sectionId);

        List<ChatResponsePayload> history = messages.stream()
                .filter(m -> m.getAuthor() != null && m.getAuthor().getPrimarySection() != null)
                .map(m -> ChatResponsePayload.builder()
                        .messageId(m.getId())
                        .senderId(m.getAuthor().getId())
                        .content(m.getContent())
                        .messageType(m.getMessageType())
                        .ghostName(m.getAuthor().getGhostName())
                        .primarySection(m.getAuthor().getPrimarySection().getName())
                        .gender(m.getAuthor().getGender())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();

        // Mark section read when entering room to view history
        try {
            long total = messageRepository.countByDestinationSectionId(sectionId);
            var readOpt = sectionReadRepository.findByUserIdAndSectionId(currentUser.getId(), sectionId);
            if (readOpt.isPresent()) {
                var sr = readOpt.get();
                sr.setLastReadCount(total);
                sectionReadRepository.save(sr);
            } else {
                Section sec = sectionRepository.findById(sectionId).orElse(null);
                if (sec != null) {
                    sectionReadRepository.save(com.ghostcommunity.backend.model.SectionRead.builder()
                            .user(currentUser)
                            .section(sec)
                            .lastReadCount(total)
                            .build());
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
                    long total = messageRepository.countByDestinationSectionId(sectionId);
                    var readOpt = sectionReadRepository.findByUserIdAndSectionId(user.getId(), sectionId);
                    if (readOpt.isPresent()) {
                        var sr = readOpt.get();
                        sr.setLastReadCount(total);
                        sectionReadRepository.save(sr);
                    } else {
                        Section sec = sectionRepository.findById(sectionId).orElse(null);
                        if (sec != null) {
                            sectionReadRepository.save(com.ghostcommunity.backend.model.SectionRead.builder()
                                    .user(user)
                                    .section(sec)
                                    .lastReadCount(total)
                                    .build());
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



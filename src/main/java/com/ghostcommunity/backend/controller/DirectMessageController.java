package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.DirectMessage;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.payload.ChatResponsePayload;
import com.ghostcommunity.backend.repository.DirectMessageRepository;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/dm")
@RequiredArgsConstructor
public class DirectMessageController {

    private final DirectMessageRepository dmRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // DTO for inbox list items
    record InboxDto(
        Long partnerId,
        String partnerGhostName,
        String partnerRealName,
        String partnerProfileImageUrl,
        String partnerSection,
        String partnerGender,
        String lastMessage,
        java.time.LocalDateTime lastMessageTime,
        String chatMode
    ) {}

    // GET /api/dm/inbox?mode=DIRECT or ?mode=GHOST
    @GetMapping("/inbox")
    public ResponseEntity<?> getInbox(@RequestParam(defaultValue = "DIRECT") String mode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long myId = userDetails.getId();

        List<DirectMessage> allMessages = dmRepository.findAllUserMessagesByMode(myId, mode);

        // Keep only the latest message per partner
        Map<Long, DirectMessage> latestPerPartner = new LinkedHashMap<>();
        for (DirectMessage m : allMessages) {
            Long partnerId = m.getSender().getId().equals(myId) ? m.getReceiver().getId() : m.getSender().getId();
            latestPerPartner.putIfAbsent(partnerId, m);
        }

        List<InboxDto> inbox = new ArrayList<>();
        for (Map.Entry<Long, DirectMessage> entry : latestPerPartner.entrySet()) {
            Long partnerId = entry.getKey();
            DirectMessage m = entry.getValue();
            User partner = m.getSender().getId().equals(partnerId) ? m.getSender() : m.getReceiver();

            boolean isGhostMode = "GHOST".equalsIgnoreCase(mode) || "GHOST".equalsIgnoreCase(m.getChatMode());
            String realName = (partner.getRealName() != null && !partner.getRealName().isBlank() && !partner.getRealName().equalsIgnoreCase("Student"))
                    ? partner.getRealName()
                    : partner.getGhostName();
            String profilePic = partner.getProfileImageUrl() != null ? partner.getProfileImageUrl() : "";

            inbox.add(new InboxDto(
                    partner.getId(),
                    partner.getGhostName(),
                    isGhostMode ? partner.getGhostName() : realName,
                    isGhostMode ? "" : profilePic,
                    partner.getPrimarySection() != null ? partner.getPrimarySection().getName() : "",
                    partner.getGender(),
                    m.getContent(),
                    m.getCreatedAt(),
                    m.getChatMode()
            ));
        }

        return ResponseEntity.ok(inbox);
    }

    // GET /api/dm/{userId}?mode=DIRECT or ?mode=GHOST
    @GetMapping("/{userId}")
    public ResponseEntity<?> getHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "DIRECT") String mode) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long myId = userDetails.getId();

        List<DirectMessage> history = dmRepository.findChatHistoryByMode(myId, userId, mode);

        List<ChatResponsePayload> payloads = history.stream().map(m -> {
            boolean isGhost = "GHOST".equalsIgnoreCase(m.getChatMode()) || "GHOST".equalsIgnoreCase(mode);
            User sender = m.getSender();
            String realName = (sender.getRealName() != null && !sender.getRealName().isBlank() && !sender.getRealName().equalsIgnoreCase("Student"))
                    ? sender.getRealName()
                    : sender.getGhostName();
            String displayName = isGhost ? sender.getGhostName() : realName;
            return ChatResponsePayload.builder()
                    .messageId(m.getId())
                    .senderId(sender.getId())
                    .content(m.getContent())
                    .messageType(m.getMessageType())
                    .ghostName(displayName)
                    .primarySection(sender.getPrimarySection() != null ? sender.getPrimarySection().getName() : "")
                    .gender(sender.getGender())
                    .createdAt(m.getCreatedAt())
                    .build();
        }).toList();

        return ResponseEntity.ok(payloads);
    }

    // POST /api/dm/{userId}?mode=DIRECT or ?mode=GHOST
    @PostMapping("/{userId}")
    public ResponseEntity<?> sendMessage(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "DIRECT") String mode,
            @RequestBody Map<String, String> request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User sender = userRepository.findById(userDetails.getId()).orElseThrow();
        User receiver = userRepository.findById(userId).orElseThrow();

        DirectMessage dm = DirectMessage.builder()
                .sender(sender)
                .receiver(receiver)
                .content(request.get("content"))
                .messageType(request.getOrDefault("messageType", "TEXT"))
                .chatMode(mode)
                .build();

        dmRepository.save(dm);

        boolean isGhost = "GHOST".equalsIgnoreCase(mode);

        String senderDisplayName = isGhost
                ? sender.getGhostName()
                : (sender.getRealName() != null && !sender.getRealName().isBlank() && !sender.getRealName().equalsIgnoreCase("Student")
                        ? sender.getRealName()
                        : sender.getGhostName());

        ChatResponsePayload senderPayload = ChatResponsePayload.builder()
                .messageId(dm.getId())
                .senderId(sender.getId())
                .content(dm.getContent())
                .messageType(dm.getMessageType())
                .ghostName(senderDisplayName)
                .primarySection(sender.getPrimarySection() != null ? sender.getPrimarySection().getName() : "")
                .gender(sender.getGender())
                .createdAt(dm.getCreatedAt())
                .build();

        ChatResponsePayload receiverPayload = ChatResponsePayload.builder()
                .messageId(dm.getId())
                .senderId(sender.getId())
                .content(dm.getContent())
                .messageType(dm.getMessageType())
                .ghostName(senderDisplayName)
                .primarySection(sender.getPrimarySection() != null ? sender.getPrimarySection().getName() : "")
                .gender(sender.getGender())
                .createdAt(dm.getCreatedAt())
                .build();

        // Broadcast to both users' personal topics
        messagingTemplate.convertAndSend("/topic/user/" + receiver.getId(), receiverPayload);
        messagingTemplate.convertAndSend("/topic/user/" + sender.getId(), senderPayload);

        return ResponseEntity.ok(senderPayload);
    }

    // POST /api/dm/{userId}/accept-reveal
    @PostMapping("/{userId}/accept-reveal")
    public ResponseEntity<?> acceptReveal(@PathVariable Long userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User me = userRepository.findById(userDetails.getId()).orElseThrow();
        User partner = userRepository.findById(userId).orElseThrow();

        // 1. Convert all past GHOST messages to DIRECT
        dmRepository.revealIdentities(me.getId(), partner.getId());

        // 2. Insert a SYSTEM message so both users see the milestone
        DirectMessage sysMsg = DirectMessage.builder()
                .sender(me)
                .receiver(partner)
                .content("Identities Revealed! " + me.getRealName() + " and " + partner.getRealName() + " can now see each other.")
                .messageType("SYSTEM")
                .chatMode("DIRECT")
                .build();
        dmRepository.save(sysMsg);

        // 3. Broadcast to WebSockets to force UI update
        ChatResponsePayload payload = ChatResponsePayload.builder()
                .messageId(sysMsg.getId())
                .senderId(me.getId())
                .content(sysMsg.getContent())
                .messageType("SYSTEM")
                .ghostName("SYSTEM")
                .primarySection("")
                .gender("")
                .createdAt(sysMsg.getCreatedAt())
                .build();

        messagingTemplate.convertAndSend("/topic/user/" + partner.getId(), payload);
        messagingTemplate.convertAndSend("/topic/user/" + me.getId(), payload);

        return ResponseEntity.ok(payload);
    }

    // POST /api/dm/{userId}/reject-reveal
    @PostMapping("/{userId}/reject-reveal")
    public ResponseEntity<?> rejectReveal(@PathVariable Long userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User me = userRepository.findById(userDetails.getId()).orElseThrow();
        User partner = userRepository.findById(userId).orElseThrow();

        // 1. Insert a SYSTEM message notifying that the reveal was declined so the REVEAL_REQUEST unread badge disappears
        DirectMessage sysMsg = DirectMessage.builder()
                .sender(me)
                .receiver(partner)
                .content("Identity reveal request was declined.")
                .messageType("SYSTEM")
                .chatMode("GHOST")
                .build();
        dmRepository.save(sysMsg);

        // 2. Broadcast to WebSockets to force UI update for both users
        ChatResponsePayload payload = ChatResponsePayload.builder()
                .messageId(sysMsg.getId())
                .senderId(me.getId())
                .content(sysMsg.getContent())
                .messageType("SYSTEM")
                .ghostName("SYSTEM")
                .primarySection("")
                .gender("")
                .createdAt(sysMsg.getCreatedAt())
                .build();

        messagingTemplate.convertAndSend("/topic/user/" + partner.getId(), payload);
        messagingTemplate.convertAndSend("/topic/user/" + me.getId(), payload);

        return ResponseEntity.ok(payload);
    }

    // DELETE /api/dm/message/{messageId}?forEveryone=true|false
    @DeleteMapping("/message/{messageId}")
    public ResponseEntity<?> deleteMessage(
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "false") boolean forEveryone) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long myId = userDetails.getId();

        DirectMessage m = dmRepository.findById(messageId).orElse(null);
        if (m == null) {
            return ResponseEntity.ok(Map.of("message", "Message already deleted"));
        }

        boolean isSender = m.getSender().getId().equals(myId);
        boolean isReceiver = m.getReceiver().getId().equals(myId);

        if (!isSender && !isReceiver) {
            return ResponseEntity.status(403).body("Error: Not authorized to delete this message.");
        }

        Long partnerId = isSender ? m.getReceiver().getId() : m.getSender().getId();

        if (forEveryone) {
            // WhatsApp rule: Only sender can delete for everyone
            if (!isSender) {
                return ResponseEntity.badRequest().body("Error: Only the sender can delete for everyone.");
            }
            dmRepository.delete(m);

            // Broadcast DELETE event via WebSocket to both sender and receiver
            String deleteJson = "{\"action\":\"DELETE\",\"messageId\":" + messageId + "}";
            messagingTemplate.convertAndSend("/topic/user/" + myId, (Object) deleteJson);
            messagingTemplate.convertAndSend("/topic/user/" + partnerId, (Object) deleteJson);

            return ResponseEntity.ok(Map.of("message", "Deleted for everyone."));
        } else {
            // Delete for me
            if (isSender) {
                m.setDeletedBySender(true);
            }
            if (isReceiver) {
                m.setDeletedByReceiver(true);
            }

            // If both users deleted it, purge it completely from DB
            if (Boolean.TRUE.equals(m.getDeletedBySender()) && Boolean.TRUE.equals(m.getDeletedByReceiver())) {
                dmRepository.delete(m);
            } else {
                dmRepository.save(m);
            }

            return ResponseEntity.ok(Map.of("message", "Deleted for me."));
        }
    }

    // DELETE /api/dm/{partnerId}/clear?mode=DIRECT|GHOST&forEveryone=true|false
    // DELETE /api/dm/{partnerId}/clear?mode=DIRECT|GHOST
    @DeleteMapping("/{partnerId}/clear")
    public ResponseEntity<?> clearChat(
            @PathVariable Long partnerId,
            @RequestParam(defaultValue = "DIRECT") String mode) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long myId = userDetails.getId();

        List<DirectMessage> messages = dmRepository.findAllMessagesBetweenUsers(myId, partnerId, mode);

        // Always Clear for me only: mark all as deleted for current user
        List<DirectMessage> toDelete = new ArrayList<>();
        List<DirectMessage> toUpdate = new ArrayList<>();

        for (DirectMessage m : messages) {
            if (m.getSender().getId().equals(myId)) {
                m.setDeletedBySender(true);
            }
            if (m.getReceiver().getId().equals(myId)) {
                m.setDeletedByReceiver(true);
            }

            if (Boolean.TRUE.equals(m.getDeletedBySender()) && Boolean.TRUE.equals(m.getDeletedByReceiver())) {
                toDelete.add(m);
            } else {
                toUpdate.add(m);
            }
        }

        if (!toDelete.isEmpty()) {
            dmRepository.deleteAll(toDelete);
        }
        if (!toUpdate.isEmpty()) {
            dmRepository.saveAll(toUpdate);
        }

        return ResponseEntity.ok(Map.of("message", "Chat cleared for you successfully."));
    }
}

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

            boolean isGhost = "GHOST".equals(m.getChatMode());
            inbox.add(new InboxDto(
                    partner.getId(),
                    partner.getGhostName(),
                    isGhost ? "" : partner.getRealName(),
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

        User me = userRepository.findById(myId).orElseThrow();

        List<ChatResponsePayload> payloads = history.stream().map(m -> {
            boolean isGhost = "GHOST".equals(m.getChatMode());
            User sender = m.getSender();
            return ChatResponsePayload.builder()
                    .messageId(m.getId())
                    .senderId(sender.getId())
                    .content(m.getContent())
                    .messageType(m.getMessageType())
                    // In GHOST mode show ghost name; in DIRECT mode show real name
                    .ghostName(isGhost ? sender.getGhostName() : sender.getRealName())
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

        boolean isGhost = "GHOST".equals(mode);
        ChatResponsePayload payload = ChatResponsePayload.builder()
                .messageId(dm.getId())
                .senderId(sender.getId())
                .content(dm.getContent())
                .messageType(dm.getMessageType())
                .ghostName(isGhost ? sender.getGhostName() : sender.getRealName())
                .primarySection(sender.getPrimarySection() != null ? sender.getPrimarySection().getName() : "")
                .gender(sender.getGender())
                .createdAt(dm.getCreatedAt())
                .build();

        // Broadcast to both users' personal topics
        messagingTemplate.convertAndSend("/topic/user/" + receiver.getId(), payload);
        messagingTemplate.convertAndSend("/topic/user/" + sender.getId(), payload);

        return ResponseEntity.ok(payload);
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
}

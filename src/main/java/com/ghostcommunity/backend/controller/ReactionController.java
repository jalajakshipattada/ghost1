package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.Message;
import com.ghostcommunity.backend.model.MessageReaction;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.payload.MessageResponse;
import com.ghostcommunity.backend.repository.MessageReactionRepository;
import com.ghostcommunity.backend.repository.MessageRepository;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final MessageReactionRepository reactionRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /** POST /api/reactions/{messageId}  Body: { "emoji": "👍" } — toggles reaction */
    @PostMapping("/{messageId}")
    public ResponseEntity<?> toggleReaction(@PathVariable Long messageId,
                                             @RequestBody Map<String, String> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        String emoji = body.get("emoji");
        if (emoji == null || emoji.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Emoji required"));
        }

        Optional<MessageReaction> existing = reactionRepository.findByMessageIdAndUserId(messageId, user.getId());

        if (existing.isPresent()) {
            if (existing.get().getEmoji().equals(emoji)) {
                reactionRepository.delete(existing.get()); // same emoji = remove
            } else {
                existing.get().setEmoji(emoji);            // different emoji = replace
                reactionRepository.save(existing.get());
            }
        } else {
            reactionRepository.save(MessageReaction.builder()
                    .message(message).user(user).emoji(emoji).build());
        }

        Map<String, Object> payload = buildReactionPayload(messageId);

        // Broadcast live update to everyone in the chat room
        if (message.getDestinationSection() != null) {
            Long sectionId = message.getDestinationSection().getId();
            messagingTemplate.convertAndSend("/topic/section/" + sectionId, (Object) payload);
        }

        return ResponseEntity.ok(payload);
    }

    /** GET /api/reactions/{messageId} — returns reaction counts */
    @GetMapping("/{messageId}")
    public ResponseEntity<?> getReactions(@PathVariable Long messageId) {
        return ResponseEntity.ok(buildReactionPayload(messageId));
    }

    /** GET /api/reactions/{messageId}/details — returns full reactor list with Ghost names only */
    @GetMapping("/{messageId}/details")
    public ResponseEntity<?> getReactionDetails(@PathVariable Long messageId) {
        List<MessageReaction> reactions = reactionRepository.findByMessageIdWithUser(messageId);
        List<Map<String, Object>> userList = new ArrayList<>();
        Map<String, Long> emojiCounts = new HashMap<>();

        for (MessageReaction r : reactions) {
            String emoji = r.getEmoji();
            emojiCounts.put(emoji, emojiCounts.getOrDefault(emoji, 0L) + 1);

            Map<String, Object> u = new HashMap<>();
            u.put("id", r.getId());
            u.put("userId", r.getUser().getId());
            u.put("ghostName", r.getUser().getGhostName());
            u.put("gender", r.getUser().getGender());
            u.put("primarySection", r.getUser().getPrimarySection() != null ? r.getUser().getPrimarySection().getName() : "");
            u.put("emoji", emoji);
            userList.add(u);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("messageId", messageId);
        response.put("total", reactions.size());
        response.put("counts", emojiCounts);
        response.put("users", userList);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> buildReactionPayload(Long messageId) {
        List<Object[]> counts = reactionRepository.countReactionsByEmoji(messageId);
        Map<String, Long> emojiCounts = new HashMap<>();
        for (Object[] row : counts) {
            emojiCounts.put((String) row[0], (Long) row[1]);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("action", "REACTION");
        result.put("messageId", messageId);
        result.put("reactions", emojiCounts);
        return result;
    }
}

package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.Message;
import com.ghostcommunity.backend.model.Section;
import com.ghostcommunity.backend.model.SectionRead;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.payload.ChatMessagePayload;
import com.ghostcommunity.backend.payload.ChatResponsePayload;
import com.ghostcommunity.backend.repository.CampusStructureRepository;
import com.ghostcommunity.backend.repository.MessageRepository;
import com.ghostcommunity.backend.repository.SectionReadRepository;
import com.ghostcommunity.backend.repository.SectionRepository;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;
    private final MessageRepository messageRepository;
    private final CampusStructureRepository campusStructureRepository;
    private final SectionReadRepository sectionReadRepository;

    // Mobile app sends message to: /app/chat.section.1
    @MessageMapping("/chat.section.{sectionId}")
    public void sendMessageToSection(@DestinationVariable Long sectionId, @Payload ChatMessagePayload chatMessage, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User author = userRepository.findByIdWithSectionAndCollege(userDetails.getId()).orElseThrow();
        Section destinationSection = sectionRepository.findById(sectionId).orElseThrow();

        // SECURITY CHECK: You can only chat in rooms that belong to your College!
        if (!destinationSection.getCollege().getId().equals(author.getCollege().getId())) {
            throw new RuntimeException("Security Violation: You cannot chat in another college's room.");
        }

        // 1. Save to Database securely
        Message message = Message.builder()
                .author(author)
                .college(author.getCollege())
                .destinationSection(destinationSection)
                .content(chatMessage.getContent())
                .messageType(chatMessage.getMessageType())
                .build();
        
        message = messageRepository.save(message);

        // Advance sender's read pointer so sender never sees own message as unread
        try {
            Long messageId = message.getId();
            long total = messageRepository.countByDestinationSectionId(sectionId);
            var readOpt = sectionReadRepository.findByUserIdAndSectionId(author.getId(), sectionId);
            SectionRead sr = readOpt.orElseGet(() -> SectionRead.builder().user(author).section(destinationSection).build());
            sr.setLastReadMessageId(messageId);
            sr.setLastReadCount(total);
            sectionReadRepository.save(sr);
        } catch (Exception e) {
            log.error("Failed to update sender's SectionRead: {}", e.getMessage());
        }

        // RULE: Per each msg add 10 points to a particular user (used to attack)
        author.setWarPoints(author.getWarPoints() + 10);
        userRepository.save(author);

        // RULE: Each 5 messages in section used to build a campus brick by brick
        try {
            int newCount = destinationSection.getMessageCount() + 1;
            destinationSection.setMessageCount(newCount);

            if (newCount % 5 == 0) {
                destinationSection.setTotalBricks(destinationSection.getTotalBricks() + 1);
                CampusWarController.advanceSectionBrickConstruction(
                        destinationSection.getId(), 1, campusStructureRepository, sectionRepository
                );
            }
            sectionRepository.save(destinationSection);
        } catch (Exception e) {
            log.error("Failed to advance section message/brick count: {}", e.getMessage());
        }

        // 2. Build the PUBLIC response (Strips Real Name, sends ONLY Ghost Name + Primary Section)
        ChatResponsePayload response = ChatResponsePayload.builder()
                .messageId(message.getId())
                .senderId(author.getId())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .ghostName(author.getGhostName())
                .primarySection(author.getPrimarySection().getName())
                .gender(author.getGender()) 
                .createdAt(message.getCreatedAt())
                .build();

        // 3. Broadcast out to all students currently looking at this section's chat room
        messagingTemplate.convertAndSend("/topic/section/" + sectionId, response);
    }

    // Mobile app sends message to: /app/chat.allcollege
    @MessageMapping("/chat.allcollege")
    public void sendMessageToAllCollege(@Payload ChatMessagePayload chatMessage, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User author = userRepository.findByIdWithSectionAndCollege(userDetails.getId()).orElseThrow();

        // Null destinationSection indicates an "All College" broadcast
        Message message = Message.builder()
                .author(author)
                .college(author.getCollege())
                .destinationSection(null) 
                .content(chatMessage.getContent())
                .messageType(chatMessage.getMessageType())
                .build();
        
        message = messageRepository.save(message);

        // Rule: +10 War Points to author
        author.setWarPoints(author.getWarPoints() + 10);
        userRepository.save(author);

        // Advance primary section's brick construction pool on every 5 msgs
        try {
            if (author.getPrimarySection() != null) {
                Section primSec = author.getPrimarySection();
                int newCount = primSec.getMessageCount() + 1;
                primSec.setMessageCount(newCount);

                if (newCount % 5 == 0) {
                    primSec.setTotalBricks(primSec.getTotalBricks() + 1);
                    CampusWarController.advanceSectionBrickConstruction(
                            primSec.getId(), 1, campusStructureRepository, sectionRepository
                    );
                }
                sectionRepository.save(primSec);
            }
        } catch (Exception e) {
            log.error("Failed to advance allcollege message/brick count: {}", e.getMessage());
        }

        ChatResponsePayload response = ChatResponsePayload.builder()
                .messageId(message.getId())
                .senderId(author.getId())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .ghostName(author.getGhostName())
                .primarySection(author.getPrimarySection().getName())
                .gender(author.getGender())
                .createdAt(message.getCreatedAt())
                .build();

        // Broadcast to everyone in the author's college
        messagingTemplate.convertAndSend("/topic/college/" + author.getCollege().getId(), response);
    }
}

package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.Message;
import com.ghostcommunity.backend.model.Section;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.payload.ChatMessagePayload;
import com.ghostcommunity.backend.payload.ChatResponsePayload;
import com.ghostcommunity.backend.repository.MessageRepository;
import com.ghostcommunity.backend.repository.SectionRepository;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;
    private final MessageRepository messageRepository;

    // Mobile app sends message to: /app/chat.section.1
    @MessageMapping("/chat.section.{sectionId}")
    public void sendMessageToSection(@DestinationVariable Long sectionId, @Payload ChatMessagePayload chatMessage, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        // Use JOIN FETCH query to load primarySection + college in ONE go â€” avoids LazyInitializationException!
        User author = userRepository.findByIdWithSectionAndCollege(userDetails.getId()).orElseThrow();
        Section destinationSection = sectionRepository.findById(sectionId).orElseThrow();

        // SECURITY CHECK: You can only chat in rooms that belong to your College!
        if (!destinationSection.getCollege().getId().equals(author.getCollege().getId())) {
            throw new RuntimeException("Security Violation: You cannot chat in another college's room.");
        }

        // 1. Save to Database securely (Backend privately records the real author)
        Message message = Message.builder()
                .author(author)
                .college(author.getCollege())
                .destinationSection(destinationSection)
                .content(chatMessage.getContent())
                .messageType(chatMessage.getMessageType())
                .build();
        
        message = messageRepository.save(message);

        // Award +10 War Points for every chat message!
        author.setWarPoints(author.getWarPoints() + 10);
        userRepository.save(author);

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

        // Award +10 War Points for every chat message!
        author.setWarPoints(author.getWarPoints() + 10);
        userRepository.save(author);

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



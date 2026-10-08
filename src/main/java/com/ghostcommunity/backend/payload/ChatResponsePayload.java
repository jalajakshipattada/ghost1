package com.ghostcommunity.backend.payload;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder
public class ChatResponsePayload {
    private Long messageId;
    private Long senderId;
    private String content;
    private String messageType;
    
    // The public anonymous identity
    private String ghostName;      // e.g. "GhostRider"
    private String primarySection; // e.g. "CSE-2"
    private String gender;         // e.g. "MALE" or "FEMALE"
    
    private LocalDateTime createdAt;
}


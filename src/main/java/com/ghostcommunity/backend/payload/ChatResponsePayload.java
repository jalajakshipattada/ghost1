package com.ghostcommunity.backend.payload;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

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
    
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;
    private Map<String, Long> reactions;
    private Boolean isSeen;
}


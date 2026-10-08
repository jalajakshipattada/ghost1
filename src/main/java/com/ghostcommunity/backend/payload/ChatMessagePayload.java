package com.ghostcommunity.backend.payload;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ChatMessagePayload {
    private String content;
    private String messageType; // TEXT, IMAGE, POLL, etc.
}

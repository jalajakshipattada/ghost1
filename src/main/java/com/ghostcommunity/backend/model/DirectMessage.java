package com.ghostcommunity.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "direct_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DirectMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private String messageType; // "TEXT", "IMAGE"

    @Column(length = 10)
    private String chatMode; // "DIRECT" (real name shown) or "GHOST" (anonymous)

    @Column(name = "deleted_by_sender")
    @Builder.Default
    private Boolean deletedBySender = false;

    @Column(name = "deleted_by_receiver")
    @Builder.Default
    private Boolean deletedByReceiver = false;

    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (messageType == null) messageType = "TEXT";
        if (chatMode == null || chatMode.isBlank()) {
            chatMode = "DIRECT";
        } else {
            chatMode = chatMode.trim().toUpperCase();
        }
        if (deletedBySender == null) deletedBySender = false;
        if (deletedByReceiver == null) deletedByReceiver = false;
        if (isRead == null) isRead = false;
    }

    @PreUpdate
    protected void onUpdate() {
        if (chatMode == null || chatMode.isBlank()) {
            chatMode = "DIRECT";
        } else {
            chatMode = chatMode.trim().toUpperCase();
        }
    }
}

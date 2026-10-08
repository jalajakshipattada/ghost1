package com.ghostcommunity.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The destination section/room for this message (e.g., ECE-3). If null, it's the "All College" feed.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_section_id")
    private Section destinationSection;

    // The college this message belongs to (Required for filtering the All College feed)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id", nullable = false)
    private College college;

    // The actual author (Private identity, never sent to frontend)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    // The content (text, or URL for media)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Message type (e.g., TEXT, IMAGE, AUDIO, VIDEO, POLL)
    @Column(nullable = false)
    private String messageType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (messageType == null) {
            messageType = "TEXT";
        }
    }
}

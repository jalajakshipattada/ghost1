package com.ghostcommunity.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user who submitted the report
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    // The user being reported (Resolved secretly by the backend)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_user_id", nullable = false)
    private User reportedUser;

    // The specific community message that caused the report (optional for direct/ghost)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_message_id", nullable = true)
    private Message reportedMessage;

    @Column(name = "direct_message_id")
    private Long directMessageId;

    @Column(name = "chat_type", length = 30)
    @Builder.Default
    private String chatType = "COMMUNITY"; // COMMUNITY, DIRECT, GHOST

    @Column(name = "message_content", columnDefinition = "TEXT")
    private String messageContent;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "media_type", length = 20)
    private String mediaType; // TEXT, IMAGE, AUDIO, VIDEO

    @Column(name = "section_name", length = 100)
    private String sectionName;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String status; // e.g., PENDING, RESOLVED, DISMISSED

    @Column(name = "admin_action", length = 50)
    @Builder.Default
    private String adminAction = "NONE"; // NONE, DELETED_MESSAGE, BANNED_USER, DISMISSED

    @Column(name = "admin_notes", length = 500)
    private String adminNotes;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (chatType == null) chatType = "COMMUNITY";
        if (adminAction == null) adminAction = "NONE";
    }
}

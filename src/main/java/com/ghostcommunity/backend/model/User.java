package com.ghostcommunity.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"ghost_name", "college_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    // Private identity - never exposed in APIs unless Admin
    @Column(name = "real_name")
    private String realName;

    // Public identity for messages
    @Column(name = "ghost_name")
    private String ghostName;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(length = 10)
    private String gender; // "MALE" or "FEMALE"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id")
    private College college;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_section_id")
    private Section primarySection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted;

    @Column(nullable = false)
    private boolean isAdmin = false;

    @Column(name = "war_points", nullable = false)
    @Builder.Default
    private int warPoints = 100;

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "token_expiry")
    private LocalDateTime tokenExpiry;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = UserStatus.ONBOARDING_INCOMPLETE;
        }
    }
}

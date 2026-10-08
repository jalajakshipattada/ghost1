package com.ghostcommunity.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "message_reactions",
    // One user can only react with one emoji per message
    uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The emoji string e.g. "👍", "❤️", "😂", "😮", "😢", "🔥"
    @Column(nullable = false)
    private String emoji;
}

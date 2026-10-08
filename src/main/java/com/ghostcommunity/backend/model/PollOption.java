package com.ghostcommunity.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "poll_options")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PollOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String text;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @Column(nullable = false)
    private int voteCount = 0;
}

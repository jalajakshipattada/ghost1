package com.ghostcommunity.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fortresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fortress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "section_id", nullable = false, unique = true)
    private Section section;

    @Column(nullable = false)
    @Builder.Default
    private String name = "Cyber Fortress";

    @Column(nullable = false)
    @Builder.Default
    private int level = 1;

    @Column(nullable = false)
    @Builder.Default
    private int hp = 1000;

    @Column(name = "max_hp", nullable = false)
    @Builder.Default
    private int maxHp = 1000;

    @Column(nullable = false)
    @Builder.Default
    private int shields = 2;

    @Column(name = "max_shields", nullable = false)
    @Builder.Default
    private int maxShields = 3;

    @Column(nullable = false)
    @Builder.Default
    private int bricks = 150;

    @Column(nullable = false)
    @Builder.Default
    private int points = 500;

    @Column(name = "last_attacked_at")
    private LocalDateTime lastAttackedAt;
}

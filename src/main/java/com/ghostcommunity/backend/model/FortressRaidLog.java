package com.ghostcommunity.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fortress_raid_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FortressRaidLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "attacker_user_id")
    private User attacker;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "attacker_section_id")
    private Section attackerSection;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "target_section_id")
    private Section targetSection;

    @Column(nullable = false)
    private String weapon; // "MISSILE", "EMP_SURGE", "CYBER_ORBITAL"

    @Column(name = "damage_dealt", nullable = false)
    private int damageDealt;

    @Column(name = "points_stolen", nullable = false)
    private int pointsStolen;

    @Column(name = "shield_absorbed", nullable = false)
    private boolean shieldAbsorbed;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}

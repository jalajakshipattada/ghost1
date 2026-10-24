package com.ghostcommunity.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "campus_structures", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"section_id", "structure_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampusStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @Column(name = "structure_key", nullable = false, length = 50)
    private String structureKey; // MAIN_HALL, CANTEEN, LIBRARY, LIGHT_POLE_1, LIGHT_POLE_2, GARDEN, LASER_TURRET

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private int hp;

    @Column(name = "max_hp", nullable = false)
    private int maxHp;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "INTACT"; // INTACT, DAMAGED, DESTROYED

    @Column(name = "rebuild_points_required", nullable = false)
    @Builder.Default
    private int rebuildPointsRequired = 100;

    @Column(name = "rebuild_points_contributed", nullable = false)
    @Builder.Default
    private int rebuildPointsContributed = 0;

    @Column(name = "coord_x")
    private double coordX;

    @Column(name = "coord_y")
    private double coordY;

    @Column(name = "coord_z")
    private double coordZ;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

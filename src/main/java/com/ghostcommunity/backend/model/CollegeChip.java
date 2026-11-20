package com.ghostcommunity.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "college_chips", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "college_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CollegeChip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id", nullable = false)
    private College college;
}

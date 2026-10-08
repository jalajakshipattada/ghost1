package com.ghostcommunity.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sections", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "college_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Section {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // e.g., "CSE-2"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id", nullable = false)
    private College college;
}

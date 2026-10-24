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

    @Column(name = "message_count")
    @Builder.Default
    private Integer messageCount = 0;

    @Column(name = "total_bricks")
    @Builder.Default
    private Integer totalBricks = 0;

    @Column(name = "campus_level")
    @Builder.Default
    private Integer campusLevel = 1;

    public int getMessageCount() {
        return messageCount == null ? 0 : messageCount;
    }

    public int getTotalBricks() {
        return totalBricks == null ? 0 : totalBricks;
    }

    public int getCampusLevel() {
        return campusLevel == null ? 1 : campusLevel;
    }
}

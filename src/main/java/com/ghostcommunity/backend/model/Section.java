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

    @Column(name = "chip")
    private String chip;

    public String getChip() {
        if (chip != null && !chip.trim().isEmpty()) {
            return chip.trim().toUpperCase();
        }
        return deriveChip(name);
    }

    public static String deriveChip(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) return "GENERAL";
        String s = rawName.trim().toUpperCase();
        if (s.contains("-")) return s.split("-")[0].trim();
        var digitMatcher = java.util.regex.Pattern.compile("^([A-Z]{2,})\\s*\\d+$").matcher(s);
        if (digitMatcher.find()) return digitMatcher.group(1);
        var spaceLetterMatcher = java.util.regex.Pattern.compile("^([A-Z]{2,})\\s+([A-Z])$").matcher(s);
        if (spaceLetterMatcher.find()) return spaceLetterMatcher.group(1);
        var letterMatcher = java.util.regex.Pattern.compile("^([A-Z]{2,})([A-D])$").matcher(s);
        if (letterMatcher.find()) return letterMatcher.group(1);
        return s;
    }

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

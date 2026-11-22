package com.ghostcommunity.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id", nullable = true)
    private College college;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(length = 500)
    private String imageUrl;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "media_type", length = 20)
    private String mediaType;

    @Column(name = "is_pinned", columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isPinned = false;

    @Column(name = "shares_count", columnDefinition = "integer default 0")
    @Builder.Default
    private Integer sharesCount = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;
}

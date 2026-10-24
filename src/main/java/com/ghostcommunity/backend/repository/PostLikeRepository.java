package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    int countByPostId(Long postId);
    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);
    boolean existsByPostIdAndUserId(Long postId, Long userId);

    @Query("SELECT COALESCE(SUM(COALESCE(pl.voteValue, 1)), 0) FROM PostLike pl WHERE pl.post.id = :postId")
    int getScoreByPostId(@Param("postId") Long postId);
}

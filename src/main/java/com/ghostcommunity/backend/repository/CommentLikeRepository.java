package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    int countByCommentId(Long commentId);
    Optional<CommentLike> findByCommentIdAndUserId(Long commentId, Long userId);
    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM CommentLike cl WHERE cl.comment.post.id = :postId")
    void deleteByPostId(@org.springframework.data.repository.query.Param("postId") Long postId);
}

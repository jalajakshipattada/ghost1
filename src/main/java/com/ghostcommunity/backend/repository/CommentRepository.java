package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    int countByPostId(Long postId);
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM Comment c WHERE c.post.id = :postId")
    void deleteByPostId(@org.springframework.data.repository.query.Param("postId") Long postId);
}

package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Post> findTop200ByOrderByCreatedAtDesc();
    List<Post> findByIsPinnedTrueOrderByCreatedAtDesc();

    Page<Post> findByCollegeIdOrderByCreatedAtDesc(Long collegeId, Pageable pageable);
    List<Post> findTop200ByCollegeIdOrderByCreatedAtDesc(Long collegeId);
    Page<Post> findByCollegeIdAndContentContainingIgnoreCaseOrderByCreatedAtDesc(Long collegeId, String content, Pageable pageable);
    Page<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.college.id = :collegeId AND " +
           "(LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.author.ghostName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY p.createdAt DESC")
    Page<Post> searchByCollegeAndQuery(@Param("collegeId") Long collegeId, @Param("query") String query, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE " +
           "(LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.author.ghostName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY p.createdAt DESC")
    Page<Post> searchAllByQuery(@Param("query") String query, Pageable pageable);
}

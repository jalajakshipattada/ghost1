package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    // Find all reactions for a specific message with User eagerly fetched
    @Query("SELECT r FROM MessageReaction r JOIN FETCH r.user WHERE r.message.id = :messageId")
    List<MessageReaction> findByMessageIdWithUser(@Param("messageId") Long messageId);

    // Find all reactions for a specific message
    List<MessageReaction> findByMessageId(Long messageId);

    // Find a specific user's reaction on a message (for toggle logic)
    Optional<MessageReaction> findByMessageIdAndUserId(Long messageId, Long userId);

    // Get reaction counts grouped by emoji for a message
    @Query("SELECT r.emoji, COUNT(r) FROM MessageReaction r WHERE r.message.id = :messageId GROUP BY r.emoji")
    List<Object[]> countReactionsByEmoji(@Param("messageId") Long messageId);

    // Get reaction counts grouped by messageId and emoji for an entire section
    @Query("SELECT r.message.id, r.emoji, COUNT(r) FROM MessageReaction r WHERE r.message.destinationSection.id = :sectionId GROUP BY r.message.id, r.emoji")
    List<Object[]> countReactionsBySectionId(@Param("sectionId") Long sectionId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM MessageReaction r WHERE r.message.destinationSection.id = :sectionId")
    void deleteBySectionId(@Param("sectionId") Long sectionId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM MessageReaction r WHERE r.message.college.id = :collegeId")
    void deleteByCollegeId(@Param("collegeId") Long collegeId);
}

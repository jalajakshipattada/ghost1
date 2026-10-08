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

    // Find all reactions for a specific message
    List<MessageReaction> findByMessageId(Long messageId);

    // Find a specific user's reaction on a message (for toggle logic)
    Optional<MessageReaction> findByMessageIdAndUserId(Long messageId, Long userId);

    // Get reaction counts grouped by emoji for a message
    @Query("SELECT r.emoji, COUNT(r) FROM MessageReaction r WHERE r.message.id = :messageId GROUP BY r.emoji")
    List<Object[]> countReactionsByEmoji(@Param("messageId") Long messageId);
}

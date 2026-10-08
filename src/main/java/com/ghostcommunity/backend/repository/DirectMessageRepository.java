package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {
    @Query("SELECT m FROM DirectMessage m WHERE ((m.sender.id = :user1 AND m.receiver.id = :user2) OR (m.sender.id = :user2 AND m.receiver.id = :user1)) AND m.chatMode = :mode ORDER BY m.createdAt ASC")
    List<DirectMessage> findChatHistoryByMode(@Param("user1") Long user1, @Param("user2") Long user2, @Param("mode") String mode);

    @Query("SELECT m FROM DirectMessage m WHERE (m.sender.id = :userId OR m.receiver.id = :userId) AND m.chatMode = :mode ORDER BY m.createdAt DESC")
    List<DirectMessage> findAllUserMessagesByMode(@Param("userId") Long userId, @Param("mode") String mode);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE DirectMessage m SET m.chatMode = 'DIRECT' WHERE ((m.sender.id = :user1 AND m.receiver.id = :user2) OR (m.sender.id = :user2 AND m.receiver.id = :user1)) AND m.chatMode = 'GHOST'")
    void revealIdentities(@Param("user1") Long user1, @Param("user2") Long user2);
}

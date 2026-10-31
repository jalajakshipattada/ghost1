package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {
    @Query("SELECT m FROM DirectMessage m WHERE " +
           "(((m.sender.id = :user1 AND m.receiver.id = :user2 AND (m.deletedBySender = false OR m.deletedBySender IS NULL)) OR " +
           "(m.sender.id = :user2 AND m.receiver.id = :user1 AND (m.deletedByReceiver = false OR m.deletedByReceiver IS NULL)))) " +
           "AND m.chatMode = :mode ORDER BY m.createdAt ASC")
    List<DirectMessage> findChatHistoryByMode(@Param("user1") Long user1, @Param("user2") Long user2, @Param("mode") String mode);

    @Query("SELECT m FROM DirectMessage m WHERE " +
           "((m.sender.id = :userId AND (m.deletedBySender = false OR m.deletedBySender IS NULL)) OR " +
           "(m.receiver.id = :userId AND (m.deletedByReceiver = false OR m.deletedByReceiver IS NULL))) " +
           "AND m.chatMode = :mode ORDER BY m.createdAt DESC")
    List<DirectMessage> findAllUserMessagesByMode(@Param("userId") Long userId, @Param("mode") String mode);

    @Query("SELECT m FROM DirectMessage m WHERE " +
           "((m.sender.id = :user1 AND m.receiver.id = :user2) OR " +
           "(m.sender.id = :user2 AND m.receiver.id = :user1)) " +
           "AND m.chatMode = :mode")
    List<DirectMessage> findAllMessagesBetweenUsers(@Param("user1") Long user1, @Param("user2") Long user2, @Param("mode") String mode);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE DirectMessage m SET m.chatMode = 'DIRECT' WHERE ((m.sender.id = :user1 AND m.receiver.id = :user2) OR (m.sender.id = :user2 AND m.receiver.id = :user1)) AND m.chatMode = 'GHOST'")
    void revealIdentities(@Param("user1") Long user1, @Param("user2") Long user2);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM DirectMessage m WHERE " +
           "(m.sender.id = :senderId AND m.receiver.id = :receiverId AND m.chatMode = 'DIRECT') OR " +
           "(((m.sender.id = :senderId AND m.receiver.id = :receiverId) OR (m.sender.id = :receiverId AND m.receiver.id = :senderId)) AND m.messageType = 'SYSTEM' AND m.content LIKE 'Identities Revealed%')")
    boolean hasUserRevealedTo(@Param("senderId") Long senderId, @Param("receiverId") Long receiverId);
}

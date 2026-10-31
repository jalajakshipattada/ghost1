package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    // Get messages for a specific section
    List<Message> findByDestinationSectionIdOrderByCreatedAtAsc(Long sectionId);
    
    // Get messages for the "All College" feed (where section is null)
    List<Message> findByCollegeIdAndDestinationSectionIsNullOrderByCreatedAtAsc(Long collegeId);

    // Get total message count for a section (used for unread counts)
    long countByDestinationSectionId(Long sectionId);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(m.id) FROM Message m WHERE m.destinationSection.id = :sectionId")
    Long findMaxIdByDestinationSectionId(@org.springframework.data.repository.query.Param("sectionId") Long sectionId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(m) FROM Message m WHERE m.destinationSection.id = :sectionId AND (m.author IS NULL OR m.author.id != :userId) AND (:lastReadMessageId IS NULL OR m.id > :lastReadMessageId)")
    long countUnreadMessages(@org.springframework.data.repository.query.Param("sectionId") Long sectionId, @org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("lastReadMessageId") Long lastReadMessageId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(m) FROM Message m WHERE m.destinationSection.id = :sectionId AND (m.author IS NULL OR m.author.id != :userId) AND m.createdAt >= :afterTime")
    long countUnreadSince(@org.springframework.data.repository.query.Param("sectionId") Long sectionId, @org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("afterTime") java.time.LocalDateTime afterTime);
}

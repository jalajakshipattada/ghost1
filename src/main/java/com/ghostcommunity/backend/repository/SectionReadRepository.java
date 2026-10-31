package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.SectionRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SectionReadRepository extends JpaRepository<SectionRead, Long> {
    Optional<SectionRead> findByUserIdAndSectionId(Long userId, Long sectionId);

    @Query("SELECT MAX(sr.lastReadMessageId) FROM SectionRead sr WHERE sr.section.id = :sectionId AND sr.user.id <> :userId")
    Long findMaxLastReadMessageIdByOthers(@Param("sectionId") Long sectionId, @Param("userId") Long userId);
}

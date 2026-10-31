package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.SectionRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SectionReadRepository extends JpaRepository<SectionRead, Long> {
    Optional<SectionRead> findByUserIdAndSectionId(Long userId, Long sectionId);
}

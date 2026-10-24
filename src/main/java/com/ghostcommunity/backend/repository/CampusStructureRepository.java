package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.CampusStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampusStructureRepository extends JpaRepository<CampusStructure, Long> {
    List<CampusStructure> findBySectionId(Long sectionId);
    Optional<CampusStructure> findBySectionIdAndStructureKey(Long sectionId, String structureKey);
}

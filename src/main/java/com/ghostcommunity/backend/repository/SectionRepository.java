package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByCollegeId(Long collegeId);
    List<Section> findByCollegeIdAndNameContainingIgnoreCase(Long collegeId, String name);
    Optional<Section> findByCollegeIdAndName(Long collegeId, String name);
}

package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByCollegeId(Long collegeId);
}

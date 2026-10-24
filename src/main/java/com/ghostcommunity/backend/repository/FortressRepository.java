package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.Fortress;
import com.ghostcommunity.backend.model.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FortressRepository extends JpaRepository<Fortress, Long> {
    Optional<Fortress> findBySection(Section section);
    Optional<Fortress> findBySectionId(Long sectionId);
    List<Fortress> findBySectionCollegeId(Long collegeId);
}

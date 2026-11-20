package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.CollegeChip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollegeChipRepository extends JpaRepository<CollegeChip, Long> {
    List<CollegeChip> findByCollegeIdOrderByNameAsc(Long collegeId);
    Optional<CollegeChip> findByCollegeIdAndNameIgnoreCase(Long collegeId, String name);

    @Modifying
    @Transactional
    @Query("DELETE FROM CollegeChip cc WHERE cc.college.id = :collegeId AND LOWER(cc.name) = LOWER(:name)")
    void deleteByCollegeIdAndNameIgnoreCase(@Param("collegeId") Long collegeId, @Param("name") String name);

    @Modifying
    @Transactional
    @Query("DELETE FROM CollegeChip cc WHERE cc.college.id = :collegeId")
    void deleteByCollegeId(@Param("collegeId") Long collegeId);
}

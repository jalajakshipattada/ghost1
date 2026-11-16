package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByOrderByCreatedAtDesc();
    List<Report> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);

    @Query("SELECT r FROM Report r LEFT JOIN FETCH r.reportedUser LEFT JOIN FETCH r.reporter ORDER BY r.createdAt DESC")
    List<Report> findAllWithUsers();
}

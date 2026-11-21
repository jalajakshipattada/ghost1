package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByOrderByCreatedAtDesc();
    List<Report> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);

    @Query("SELECT r FROM Report r " +
           "LEFT JOIN FETCH r.reportedUser ru " +
           "LEFT JOIN FETCH ru.college " +
           "LEFT JOIN FETCH ru.primarySection " +
           "LEFT JOIN FETCH r.reporter rep " +
           "LEFT JOIN FETCH rep.college " +
           "LEFT JOIN FETCH r.reportedMessage " +
           "ORDER BY r.createdAt DESC")
    List<Report> findAllWithUsersAndColleges();

    @Query("SELECT r FROM Report r " +
           "LEFT JOIN FETCH r.reportedUser ru " +
           "LEFT JOIN FETCH ru.college " +
           "LEFT JOIN FETCH ru.primarySection " +
           "LEFT JOIN FETCH r.reporter rep " +
           "LEFT JOIN FETCH rep.college " +
           "LEFT JOIN FETCH r.reportedMessage " +
           "WHERE UPPER(r.status) = UPPER(:status) " +
           "ORDER BY r.createdAt DESC")
    List<Report> findByStatusWithUsersAndColleges(@Param("status") String status);

    @Query("SELECT r FROM Report r LEFT JOIN FETCH r.reportedUser LEFT JOIN FETCH r.reporter ORDER BY r.createdAt DESC")
    List<Report> findAllWithUsers();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Report r SET r.reportedMessage = null WHERE r.reportedMessage.destinationSection.id = :sectionId")
    void detachReportsForSection(@Param("sectionId") Long sectionId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Report r SET r.reportedMessage = null WHERE r.reportedMessage.college.id = :collegeId")
    void detachReportsForCollege(@Param("collegeId") Long collegeId);
}

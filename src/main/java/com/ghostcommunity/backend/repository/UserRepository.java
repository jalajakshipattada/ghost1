package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByResetToken(String resetToken);
    boolean existsByEmail(String email);
    boolean existsByGhostNameAndCollegeId(String ghostName, Long collegeId);
    Optional<User> findByGhostNameAndCollegeId(String ghostName, Long collegeId);
    
    java.util.List<User> findByPrimarySectionIdAndStatus(Long sectionId, com.ghostcommunity.backend.model.UserStatus status);
    java.util.List<User> findByCollegeIdAndStatus(Long collegeId, com.ghostcommunity.backend.model.UserStatus status);

    long countByPrimarySectionIdAndStatus(Long sectionId, com.ghostcommunity.backend.model.UserStatus status);
    long countByPrimarySectionId(Long sectionId);

    // Eagerly loads primarySection + college in ONE query — prevents LazyInitializationException in WebSocket threads
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u JOIN FETCH u.primarySection JOIN FETCH u.college WHERE u.id = :id")
    Optional<User> findByIdWithSectionAndCollege(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.college.id = :collegeId AND " +
           "(LOWER(COALESCE(u.realName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(u.ghostName, '')) LIKE LOWER(CONCAT('%', :query, '%')))")
    java.util.List<User> searchCollegeUsers(@org.springframework.data.repository.query.Param("collegeId") Long collegeId,
                                           @org.springframework.data.repository.query.Param("query") String query);
}

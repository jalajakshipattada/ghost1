package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByResetToken(String resetToken);
    boolean existsByEmail(String email);
    boolean existsByGhostNameAndCollegeId(String ghostName, Long collegeId);
    Optional<User> findByGhostNameAndCollegeId(String ghostName, Long collegeId);
    
    List<User> findByPrimarySectionIdAndStatus(Long sectionId, UserStatus status);
    List<User> findByCollegeIdAndStatus(Long collegeId, UserStatus status);

    long countByPrimarySectionIdAndStatus(Long sectionId, UserStatus status);
    long countByPrimarySectionId(Long sectionId);
    long countByCollegeId(Long collegeId);
    long countByStatus(UserStatus status);

    @Query("SELECT u FROM User u JOIN FETCH u.primarySection JOIN FETCH u.college WHERE u.id = :id")
    Optional<User> findByIdWithSectionAndCollege(@Param("id") Long id);

    @Query("SELECT u FROM User u WHERE u.college.id = :collegeId AND " +
           "(LOWER(COALESCE(u.realName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(u.ghostName, '')) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<User> searchCollegeUsers(@Param("collegeId") Long collegeId, @Param("query") String query);

    @Query("SELECT u FROM User u WHERE " +
           "LOWER(COALESCE(u.realName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(u.ghostName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchUsers(@Param("query") String query);
}

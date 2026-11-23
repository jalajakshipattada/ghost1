package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // DTO for the user's OWN private profile screen (Contains EVERYTHING)
    record PrivateProfileDto(
            Long id,
            String email,
            String realName,
            String ghostName,
            Long collegeId,
            String collegeName,
            String primarySection,
            String profileImageUrl,
            String gender,
            boolean isAdmin,
            String status,
            String warningMessage,
            Integer warningCount,
            String suspensionReason
    ) {}

    // DTO for when you tap on someone's Ghost Name in the chat (Strictly Anonymous)
    record PublicRealProfileDto(Long id, String realName, String collegeName, String primarySection, String profileImageUrl, String gender) {}

    record PublicGhostProfileDto(Long id, String ghostName, String primarySection, String profileImageUrl, String gender) {}

    // 1. Endpoint for the user's own "Profile" tab in the bottom navigation bar
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        // Safely extract data. If they haven't onboarded yet, college might be null.
        PrivateProfileDto profile = new PrivateProfileDto(
                user.getId(),
                user.getEmail(),
                user.getRealName(),
                user.getGhostName(),
                user.getCollege() != null ? user.getCollege().getId() : null,
                user.getCollege() != null ? user.getCollege().getName() : "Not Selected",
                user.getPrimarySection() != null ? user.getPrimarySection().getName() : "Not Selected",
                user.getProfileImageUrl(),
                user.getGender(),
                user.isAdmin(),
                user.getStatus() != null ? user.getStatus().name() : "ACTIVE",
                user.getWarningMessage(),
                user.getWarningCount() != null ? user.getWarningCount() : 0,
                user.getSuspensionReason()
        );

        return ResponseEntity.ok(profile);
    }

    @PostMapping("/warning/acknowledge")
    public ResponseEntity<?> acknowledgeWarning() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        user.setWarningMessage(null);
        userRepository.save(user);

        return ResponseEntity.ok(java.util.Map.of("message", "Warning acknowledged"));
    }

    @PostMapping("/me/image")
    public ResponseEntity<?> updateProfileImage(@RequestBody java.util.Map<String, String> request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        String imageUrl = request.get("imageUrl");
        user.setProfileImageUrl(imageUrl);
        userRepository.save(user);

        return ResponseEntity.ok("Profile image updated");
    }

    // 1b. Delete user's own account permanently
    @DeleteMapping("/me")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteMyAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Unauthorized"));
        }
        Long userId = userDetails.getId();

        try {
            // 1. Delete message reactions by user
            jdbcTemplate.execute("DELETE FROM message_reactions WHERE user_id = " + userId);

            // 2. Delete comments & comment likes on user's posts, and user's own comments & likes
            jdbcTemplate.execute("DELETE FROM comment_likes WHERE user_id = " + userId + " OR comment_id IN (SELECT id FROM comments WHERE author_id = " + userId + " OR post_id IN (SELECT id FROM posts WHERE author_id = " + userId + "))");
            jdbcTemplate.execute("DELETE FROM comments WHERE author_id = " + userId + " OR post_id IN (SELECT id FROM posts WHERE author_id = " + userId + ")");

            // 3. Delete post likes & saved posts for user's posts or by user
            jdbcTemplate.execute("DELETE FROM post_likes WHERE user_id = " + userId + " OR post_id IN (SELECT id FROM posts WHERE author_id = " + userId + ")");
            jdbcTemplate.execute("DELETE FROM saved_posts WHERE user_id = " + userId + " OR post_id IN (SELECT id FROM posts WHERE author_id = " + userId + ")");

            // 4. Delete user's posts
            jdbcTemplate.execute("DELETE FROM posts WHERE author_id = " + userId);

            // 5. Delete direct messages where user was sender or receiver
            jdbcTemplate.execute("DELETE FROM direct_messages WHERE sender_id = " + userId + " OR receiver_id = " + userId);

            // 6. Delete section messages authored by user (and reactions to those messages)
            jdbcTemplate.execute("DELETE FROM message_reactions WHERE message_id IN (SELECT id FROM messages WHERE author_id = " + userId + ")");
            jdbcTemplate.execute("DELETE FROM messages WHERE author_id = " + userId);

            // 7. Delete polls and poll votes
            jdbcTemplate.execute("DELETE FROM poll_votes WHERE user_id = " + userId);
            jdbcTemplate.execute("DELETE FROM poll_options WHERE poll_id IN (SELECT id FROM polls WHERE author_id = " + userId + ")");
            jdbcTemplate.execute("DELETE FROM polls WHERE author_id = " + userId);

            // 8. Delete reports, blocks, section reads, password reset tokens
            jdbcTemplate.execute("DELETE FROM reports WHERE reporter_id = " + userId + " OR reported_user_id = " + userId);
            jdbcTemplate.execute("DELETE FROM blocks WHERE blocker_id = " + userId + " OR blocked_id = " + userId);
            jdbcTemplate.execute("DELETE FROM section_reads WHERE user_id = " + userId);
            jdbcTemplate.execute("DELETE FROM password_reset_tokens WHERE user_id = " + userId);

            // 9. Delete user record
            jdbcTemplate.execute("DELETE FROM users WHERE id = " + userId);

            return ResponseEntity.ok(java.util.Map.of("message", "Account deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "Failed to delete account: " + e.getMessage()));
        }
    }

    // 2. Endpoint to view someone else's public anonymous profile
    @GetMapping("/ghost/{ghostName}")
    public ResponseEntity<?> getGhostProfile(@PathVariable String ghostName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User currentUser = userRepository.findById(userDetails.getId()).orElseThrow();

        if (currentUser.getCollege() == null) {
            return ResponseEntity.badRequest().body("You are not part of a college yet.");
        }

        return userRepository.findByGhostNameAndCollegeId(ghostName, currentUser.getCollege().getId())
                .map(user -> new PublicGhostProfileDto(
                        user.getId(),
                        user.getGhostName(),
                        user.getPrimarySection() != null ? user.getPrimarySection().getName() : "Unknown Section",
                        user.getProfileImageUrl(),
                        user.getGender()
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 3. Endpoint to view someone's public real profile (from the members list)
    @GetMapping("/user/{id}")
    public ResponseEntity<?> getRealProfile(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User currentUser = userRepository.findById(userDetails.getId()).orElseThrow();

        if (currentUser.getCollege() == null) {
            return ResponseEntity.badRequest().body("You are not part of a college yet.");
        }

        User targetUser = userRepository.findById(id).orElse(null);
        if (targetUser == null || targetUser.getCollege() == null || !targetUser.getCollege().getId().equals(currentUser.getCollege().getId())) {
            return ResponseEntity.notFound().build(); // Prevent looking up users outside college
        }

        PublicRealProfileDto dto = new PublicRealProfileDto(
                targetUser.getId(),
                targetUser.getRealName(),
                targetUser.getCollege().getName(),
                targetUser.getPrimarySection() != null ? targetUser.getPrimarySection().getName() : "Unknown",
                targetUser.getProfileImageUrl(),
                targetUser.getGender()
        );
        return ResponseEntity.ok(dto);
    }
}

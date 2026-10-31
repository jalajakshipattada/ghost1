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

    // DTO for the user's OWN private profile screen (Contains EVERYTHING)
    record PrivateProfileDto(String email, String realName, String ghostName, String collegeName, String primarySection, String profileImageUrl, String gender) {}

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
                user.getEmail(),
                user.getRealName(),
                user.getGhostName(),
                user.getCollege() != null ? user.getCollege().getName() : "Not Selected",
                user.getPrimarySection() != null ? user.getPrimarySection().getName() : "Not Selected",
                user.getProfileImageUrl(),
                user.getGender()
        );

        return ResponseEntity.ok(profile);
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

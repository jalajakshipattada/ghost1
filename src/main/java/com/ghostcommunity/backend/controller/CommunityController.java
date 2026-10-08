package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.Section;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.model.UserStatus;
import com.ghostcommunity.backend.repository.SectionRepository;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;

    // Secure DTO: Intentionally omits the Ghost Name!
    record MemberDto(Long id, String realName, String primarySectionName, String profileImageUrl) {}

    @GetMapping("/section/{sectionId}/members")
    public ResponseEntity<?> getSectionMembers(@PathVariable Long sectionId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User currentUser = userRepository.findById(userDetails.getId()).orElseThrow();

        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new RuntimeException("Section not found"));
                
        // Security Rule: A user can only view members of communities within their own college
        if (!section.getCollege().getId().equals(currentUser.getCollege().getId())) {
            return ResponseEntity.status(403).body("Error: You are not authorized to view members of another college.");
        }

        // Fetch users and map to safe DTO
        List<MemberDto> members = userRepository.findByPrimarySectionIdAndStatus(sectionId, UserStatus.ACTIVE)
                .stream()
                .map(user -> new MemberDto(user.getId(), user.getRealName(), user.getPrimarySection().getName(), user.getProfileImageUrl()))
                .toList();

        return ResponseEntity.ok(members);
    }

    @GetMapping("/all-college/members")
    public ResponseEntity<?> getAllCollegeMembers() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User currentUser = userRepository.findById(userDetails.getId()).orElseThrow();

        // Automatically scoped to the user's own college
        List<MemberDto> members = userRepository.findByCollegeIdAndStatus(currentUser.getCollege().getId(), UserStatus.ACTIVE)
                .stream()
                .map(user -> new MemberDto(user.getId(), user.getRealName(), user.getPrimarySection().getName(), user.getProfileImageUrl()))
                .toList();

        return ResponseEntity.ok(members);
    }
}


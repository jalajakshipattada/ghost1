package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.College;
import com.ghostcommunity.backend.model.Section;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.model.UserStatus;
import com.ghostcommunity.backend.payload.MessageResponse;
import com.ghostcommunity.backend.payload.OnboardingRequest;
import com.ghostcommunity.backend.repository.CollegeRepository;
import com.ghostcommunity.backend.repository.SectionRepository;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final SectionRepository sectionRepository;
    private final com.ghostcommunity.backend.repository.MessageRepository messageRepository;

    // Lightweight DTOs to send to the mobile app
    record CollegeDto(Long id, String name) {}
    record SectionDto(Long id, String name, long totalMessages) {}

    @GetMapping("/colleges")
    public ResponseEntity<List<CollegeDto>> getAllColleges() {
        List<CollegeDto> colleges = collegeRepository.findAll().stream()
                .map(c -> new CollegeDto(c.getId(), c.getName()))
                .toList();
        return ResponseEntity.ok(colleges);
    }

    @GetMapping("/colleges/{collegeId}/sections")
    public ResponseEntity<List<SectionDto>> getSectionsByCollege(@PathVariable Long collegeId) {
        List<SectionDto> sections = sectionRepository.findByCollegeId(collegeId).stream()
                .map(s -> new SectionDto(
                        s.getId(),
                        s.getName(),
                        messageRepository.countByDestinationSectionId(s.getId())
                ))
                .toList();
        return ResponseEntity.ok(sections);
    }

    @PostMapping("/complete")
    public ResponseEntity<?> completeOnboarding(@Valid @RequestBody OnboardingRequest request) {
        // 1. Get currently authenticated user from the JWT Token
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Error: User not found."));

        // 2. Prevent active users from changing their identity (Enforcing permanence)
        if (user.getStatus() != UserStatus.ONBOARDING_INCOMPLETE) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Onboarding already completed. Identity is permanent."));
        }

        // 3. Verify College exists
        College college = collegeRepository.findById(request.getCollegeId())
                .orElseThrow(() -> new RuntimeException("Error: College not found."));

        // 4. Verify Section exists and belongs to this College (or assign default section)
        Section section;
        if (request.getSectionId() != null) {
            section = sectionRepository.findById(request.getSectionId())
                    .orElseThrow(() -> new RuntimeException("Error: Section not found."));
            if (!section.getCollege().getId().equals(college.getId())) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: Security Violation - Section does not belong to the selected college."));
            }
        } else {
            List<Section> sections = sectionRepository.findByCollegeId(college.getId());
            if (sections.isEmpty()) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: No sections found for the college."));
            }
            section = sections.get(0);
        }

        // 5. Ensure Ghost Name is unique within the College
        if (userRepository.existsByGhostNameAndCollegeId(request.getGhostName(), college.getId())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: This Ghost Name is already taken in your college. Please choose another one."));
        }

        // 6. Lock in the identity!
        user.setCollege(college);
        user.setPrimarySection(section);
        user.setGhostName(request.getGhostName());
        if (request.getGender() != null) {
            user.setGender(request.getGender().toUpperCase());
        }
        user.setStatus(UserStatus.ACTIVE); // Unlock the app features

        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Identity locked successfully! Welcome to Ghost Community."));
    }
}

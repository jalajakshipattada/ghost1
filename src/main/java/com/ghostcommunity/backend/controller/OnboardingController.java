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
    private final com.ghostcommunity.backend.service.PresenceService presenceService;
    private final com.ghostcommunity.backend.repository.SectionReadRepository sectionReadRepository;

    // Lightweight DTOs to send to the mobile app
    record CollegeDto(Long id, String name) {}
    record SectionDto(Long id, String name, String chip, Long collegeId, String collegeName, long totalMessages, long memberCount, long onlineCount, long unreadCount) {}

    @GetMapping("/colleges")
    public ResponseEntity<List<CollegeDto>> getAllColleges() {
        List<CollegeDto> colleges = collegeRepository.findAll().stream()
                .map(c -> new CollegeDto(c.getId(), c.getName()))
                .toList();
        return ResponseEntity.ok(colleges);
    }

    @GetMapping("/my-sections")
    public ResponseEntity<?> getMySections() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            return ResponseEntity.status(401).build();
        }
        User currentUser = userRepository.findById(userDetails.getId()).orElse(null);
        if (currentUser == null || currentUser.getCollege() == null) {
            return ResponseEntity.ok(java.util.Map.of(
                    "collegeId", 1L,
                    "collegeName", "Campus",
                    "primarySection", "",
                    "chips", List.of(),
                    "sections", List.of()
            ));
        }
        Long collegeId = currentUser.getCollege().getId();
        String collegeName = currentUser.getCollege().getName();
        String primarySection = currentUser.getPrimarySection() != null ? currentUser.getPrimarySection().getName() : "";
        List<SectionDto> sections = getSectionsByCollege(collegeId).getBody();
        List<String> chips = sections != null ? sections.stream().map(SectionDto::chip).distinct().toList() : List.of();
        return ResponseEntity.ok(java.util.Map.of(
                "collegeId", collegeId,
                "collegeName", collegeName,
                "primarySection", primarySection,
                "chips", chips,
                "sections", sections != null ? sections : List.of()
        ));
    }

    @GetMapping("/colleges/{collegeId}/sections")
    public ResponseEntity<List<SectionDto>> getSectionsByCollege(@PathVariable Long collegeId) {
        User currentUser = null;
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
                currentUser = userRepository.findById(userDetails.getId()).orElse(null);
            }
        } catch (Exception ignored) {}

        final User user = currentUser;

        List<SectionDto> sections = sectionRepository.findByCollegeId(collegeId).stream()
                .map(s -> {
                    long total = messageRepository.countByDestinationSectionId(s.getId());
                    long unread = 0;
                    if (user != null) {
                        var opt = sectionReadRepository.findByUserIdAndSectionId(user.getId(), s.getId());
                        if (opt.isPresent()) {
                            Long lastReadMsgId = opt.get().getLastReadMessageId();
                            if (lastReadMsgId != null) {
                                unread = messageRepository.countUnreadMessages(s.getId(), user.getId(), lastReadMsgId);
                            } else {
                                Long maxId = messageRepository.findMaxIdByDestinationSectionId(s.getId());
                                var sr = opt.get();
                                sr.setLastReadMessageId(maxId != null ? maxId : 0L);
                                sectionReadRepository.save(sr);
                                unread = 0;
                            }
                        } else {
                            java.time.LocalDateTime userCreated = user.getCreatedAt();
                            if (userCreated == null) {
                                userCreated = java.time.LocalDateTime.now().minusDays(1);
                            }
                            unread = messageRepository.countUnreadSince(s.getId(), user.getId(), userCreated.minusMinutes(5));
                        }
                    }
                    return new SectionDto(
                            s.getId(),
                            s.getName(),
                            s.getChip(),
                            s.getCollege() != null ? s.getCollege().getId() : collegeId,
                            s.getCollege() != null ? s.getCollege().getName() : "",
                            total,
                            userRepository.countByPrimarySectionIdAndStatus(s.getId(), UserStatus.ACTIVE),
                            presenceService.getOnlineCountForSection(s.getId()),
                            unread
                    );
                })
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
        presenceService.updateActivity(user.getId(), section.getId());

        return ResponseEntity.ok(new MessageResponse("Identity locked successfully! Welcome to Ghost Community."));
    }
}

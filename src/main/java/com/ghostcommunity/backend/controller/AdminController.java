package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.*;
import com.ghostcommunity.backend.payload.ChatResponsePayload;
import com.ghostcommunity.backend.payload.MessageResponse;
import com.ghostcommunity.backend.repository.*;
import com.ghostcommunity.backend.security.JwtUtils;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final MessageRepository messageRepository;
    private final DirectMessageRepository directMessageRepository;
    private final CollegeRepository collegeRepository;
    private final CollegeChipRepository collegeChipRepository;
    private final SectionRepository sectionRepository;
    private final SectionReadRepository sectionReadRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final PostRepository postRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final JwtUtils jwtUtils;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // Helper method to guarantee ONLY Admins can execute these actions
    private User checkAdminAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            throw new RuntimeException("Unauthorized: Please log in.");
        }
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!user.isAdmin()) {
            throw new RuntimeException("Unauthorized: Admin access required.");
        }
        return user;
    }

    // 0. Check Admin Status
    @GetMapping("/check")
    public ResponseEntity<?> checkStatus() {
        try {
            User admin = checkAdminAccess();
            return ResponseEntity.ok(Map.of(
                    "isAdmin", true,
                    "id", admin.getId(),
                    "realName", admin.getRealName() != null ? admin.getRealName() : "Admin",
                    "ghostName", admin.getGhostName() != null ? admin.getGhostName() : "AdminGhost"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("isAdmin", false));
        }
    }

    // 0b. Claim Admin Access with Master Key (Mobile / Authenticated or by email)
    @PostMapping("/claim")
    public ResponseEntity<?> claimAdminAccess(@RequestBody Map<String, String> request) {
        String key = request.get("key");
        if (key == null || !key.trim().equals("admin123")) {
            return ResponseEntity.status(403).body(Map.of("error", "Invalid admin master key. Use 'admin123'"));
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = null;
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            user = userRepository.findById(userDetails.getId()).orElse(null);
        }
        if (user == null) {
            String email = request.get("email");
            if (email != null && !email.trim().isEmpty()) {
                user = userRepository.findByEmail(email.trim()).orElse(null);
            }
        }
        if (user == null) {
            user = userRepository.findAll().stream().findFirst().orElse(null);
        }
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "No users found in database to promote"));
        }
        user.setAdmin(true);
        userRepository.save(user);
        log.info("User {} ({}) successfully elevated to Admin privileges.", user.getId(), user.getEmail());
        return ResponseEntity.ok(Map.of("success", true, "message", "Admin access granted!"));
    }

    // 0c. Web Admin Instant Login with Master Key
    @PostMapping("/claim-login")
    public ResponseEntity<?> claimLogin(@RequestBody Map<String, String> request) {
        String key = request.get("key");
        if (key == null || !key.trim().equals("admin123")) {
            return ResponseEntity.status(403).body(Map.of("error", "Invalid admin master key. Use 'admin123'"));
        }
        // Find existing admin or elevate the first user
        User admin = userRepository.findAll().stream()
                .filter(User::isAdmin)
                .findFirst()
                .orElseGet(() -> {
                    User first = userRepository.findAll().stream().findFirst().orElse(null);
                    if (first != null) {
                        first.setAdmin(true);
                        return userRepository.save(first);
                    }
                    return null;
                });

        if (admin == null) {
            return ResponseEntity.status(404).body(Map.of("error", "No users found in database"));
        }

        UserDetailsImpl userDetails = UserDetailsImpl.build(admin);
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        String jwt = jwtUtils.generateJwtToken(auth);

        return ResponseEntity.ok(Map.of(
                "token", jwt,
                "id", admin.getId(),
                "isAdmin", true,
                "realName", admin.getRealName() != null ? admin.getRealName() : "Campus Admin",
                "ghostName", admin.getGhostName() != null ? admin.getGhostName() : "AdminGhost"
        ));
    }

    // 0d. Purge All User Data with Master Key
    @PostMapping("/system/purge-all-user-data")
    public ResponseEntity<?> purgeAllUserData(@RequestBody Map<String, String> request) {
        String key = request.get("key");
        if (key == null || !key.trim().equals("admin123")) {
            return ResponseEntity.status(403).body(Map.of("error", "Invalid admin master key. Use 'admin123'"));
        }
        log.warn("ADMIN TRIGGERED FULL DATABASE PURGE - WIPING ALL USERS AND GENERATED DATA!");
        try {
            jdbcTemplate.execute("TRUNCATE TABLE message_reactions, direct_messages, messages, comment_likes, comments, post_likes, saved_posts, reports, poll_votes, poll_options, polls, posts, blocks, password_reset_tokens, section_reads, users CASCADE;");
            try {
                jdbcTemplate.execute("TRUNCATE TABLE campus_structures, fortress_raid_logs, fortresses CASCADE;");
            } catch (Exception ignored) {}
            try {
                jdbcTemplate.execute("UPDATE sections SET message_count = 0;");
            } catch (Exception ignored) {}

            log.info("FULL DATABASE CLEANUP COMPLETED SUCCESSFULLY!");
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "All users, messages, posts, and related data have been completely cleaned up from the database."
            ));
        } catch (Exception e) {
            log.error("Failed to purge database: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // 1. Dashboard Statistics
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        checkAdminAccess();
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long suspendedUsers = userRepository.countByStatus(UserStatus.SUSPENDED);
        long totalColleges = collegeRepository.count();
        long totalSections = sectionRepository.count();
        long totalPosts = postRepository.count();
        long totalReports = reportRepository.count();
        long pendingReports = reportRepository.countByStatus("PENDING");

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("suspendedUsers", suspendedUsers);
        stats.put("totalColleges", totalColleges);
        stats.put("totalSections", totalSections);
        stats.put("totalPosts", totalPosts);
        stats.put("totalReports", totalReports);
        stats.put("pendingReports", pendingReports);

        return ResponseEntity.ok(stats);
    }

    // 2. View all Reports with unmasked real & ghost names
    @GetMapping("/reports")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllReports(@RequestParam(required = false) String status) {
        checkAdminAccess();
        List<Report> reports;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            reports = reportRepository.findByStatusWithUsersAndColleges(status.toUpperCase());
        } else {
            reports = reportRepository.findAllWithUsersAndColleges();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Report r : reports) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("chatType", r.getChatType() != null ? r.getChatType() : "COMMUNITY");
            map.put("messageContent", r.getMessageContent() != null ? r.getMessageContent() : "");
            map.put("mediaUrl", r.getMediaUrl());
            map.put("mediaType", r.getMediaType() != null ? r.getMediaType() : "TEXT");
            map.put("sectionName", r.getSectionName() != null ? r.getSectionName() : "N/A");
            map.put("reason", r.getReason());
            map.put("status", r.getStatus());
            map.put("adminAction", r.getAdminAction() != null ? r.getAdminAction() : "NONE");
            map.put("adminNotes", r.getAdminNotes());
            map.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            map.put("resolvedAt", r.getResolvedAt() != null ? r.getResolvedAt().toString() : "");

            // Unmask Reported User
            if (r.getReportedUser() != null) {
                User u = r.getReportedUser();
                Map<String, Object> reportedMap = new HashMap<>();
                reportedMap.put("id", u.getId());
                reportedMap.put("realName", u.getRealName() != null ? u.getRealName() : "Unknown");
                reportedMap.put("ghostName", u.getGhostName() != null ? u.getGhostName() : "Unknown");
                reportedMap.put("email", u.getEmail() != null ? u.getEmail() : "");
                reportedMap.put("status", u.getStatus() != null ? u.getStatus().name() : "ACTIVE");
                reportedMap.put("collegeName", u.getCollege() != null ? u.getCollege().getName() : "N/A");
                reportedMap.put("sectionName", u.getPrimarySection() != null ? u.getPrimarySection().getName() : "N/A");
                map.put("reportedUser", reportedMap);
            }

            // Unmask Reporter
            if (r.getReporter() != null) {
                User rep = r.getReporter();
                Map<String, Object> reporterMap = new HashMap<>();
                reporterMap.put("id", rep.getId());
                reporterMap.put("realName", rep.getRealName() != null ? rep.getRealName() : "Anonymous");
                reporterMap.put("ghostName", rep.getGhostName() != null ? rep.getGhostName() : "Ghost");
                reporterMap.put("email", rep.getEmail() != null ? rep.getEmail() : "");
                map.put("reporter", reporterMap);
            }

            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    // 3. Action on a Report (Dismiss, Delete Message, Warn User, Ban User, Revert Suspension)
    @PostMapping("/reports/{reportId}/action")
    @Transactional
    public ResponseEntity<?> actionReport(@PathVariable Long reportId, @RequestBody Map<String, String> body) {
        checkAdminAccess();
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        String action = body.getOrDefault("action", "DISMISS").toUpperCase();
        String notes = body.getOrDefault("notes", "");

        report.setAdminNotes(notes);
        report.setResolvedAt(LocalDateTime.now());

        if ("WARN_USER".equalsIgnoreCase(action)) {
            User reportedUser = report.getReportedUser();
            if (reportedUser != null) {
                String warningText = body.getOrDefault("warningMessage", notes);
                if (warningText == null || warningText.isBlank()) {
                    warningText = "Your content was reported and removed for violating campus community guidelines. Further violations will result in account suspension.";
                }
                reportedUser.setWarningMessage(warningText);
                reportedUser.setWarningCount((reportedUser.getWarningCount() != null ? reportedUser.getWarningCount() : 0) + 1);
                userRepository.save(reportedUser);
            }
            maskOrDeleteReportedContent(report);
            report.setStatus("RESOLVED");
            report.setAdminAction("WARNED_USER");

        } else if ("DELETE_MESSAGE".equalsIgnoreCase(action) || "DELETE_CONTENT".equalsIgnoreCase(action)) {
            maskOrDeleteReportedContent(report);
            report.setStatus("RESOLVED");
            report.setAdminAction("DELETED_MESSAGE");

        } else if ("BAN_USER".equalsIgnoreCase(action) || "SUSPEND_USER".equalsIgnoreCase(action)) {
            User reportedUser = report.getReportedUser();
            if (reportedUser != null) {
                reportedUser.setStatus(UserStatus.SUSPENDED);
                String reason = notes != null && !notes.isBlank() ? notes : "Suspended by campus moderation for community guideline violations.";
                reportedUser.setSuspensionReason(reason);
                userRepository.save(reportedUser);
            }
            maskOrDeleteReportedContent(report);
            report.setStatus("RESOLVED");
            report.setAdminAction("BANNED_USER");

        } else if ("UNBAN_USER".equalsIgnoreCase(action) || "UNSUSPEND".equalsIgnoreCase(action) || "REVERT_SUSPENSION".equalsIgnoreCase(action)) {
            User reportedUser = report.getReportedUser();
            if (reportedUser != null) {
                reportedUser.setStatus(UserStatus.ACTIVE);
                reportedUser.setSuspensionReason(null);
                userRepository.save(reportedUser);
            }
            report.setStatus("RESOLVED");
            report.setAdminAction("UNBANNED_USER");

        } else {
            // Dismiss
            report.setStatus("DISMISSED");
            report.setAdminAction("DISMISSED");
        }

        reportRepository.save(report);

        return ResponseEntity.ok(Map.of(
                "message", "Report processed with action: " + action,
                "status", report.getStatus(),
                "adminAction", report.getAdminAction()
        ));
    }

    private void maskOrDeleteReportedContent(Report report) {
        if (report.getReportedMessage() != null) {
            try {
                Message m = report.getReportedMessage();
                m.setContent("[This message was removed by Campus Moderation]");
                m.setMessageType("TEXT");
                messageRepository.save(m);
            } catch (Exception e) {
                log.error("Failed to mask reported message: {}", e.getMessage());
            }
        } else if ("POST".equalsIgnoreCase(report.getChatType()) && report.getDirectMessageId() != null) {
            try {
                postRepository.deleteById(report.getDirectMessageId());
            } catch (Exception e) {
                log.error("Failed to delete reported post: {}", e.getMessage());
            }
        } else if (report.getDirectMessageId() != null) {
            try {
                directMessageRepository.findById(report.getDirectMessageId()).ifPresent(dm -> {
                    dm.setContent("[This message was removed by Campus Moderation]");
                    dm.setMessageType("TEXT");
                    directMessageRepository.save(dm);
                });
            } catch (Exception e) {
                log.error("Failed to mask reported DM: {}", e.getMessage());
            }
        }
    }

    // 4. Colleges & Sections Management
    @GetMapping("/colleges")
    public ResponseEntity<?> getCollegesList() {
        checkAdminAccess();
        List<College> colleges = collegeRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (College col : colleges) {
            Map<String, Object> cMap = new HashMap<>();
            cMap.put("id", col.getId());
            cMap.put("name", col.getName());

            List<Section> sections = sectionRepository.findByCollegeId(col.getId());
            List<Map<String, Object>> secList = sections.stream().map(s -> {
                Map<String, Object> sm = new HashMap<>();
                sm.put("id", s.getId());
                sm.put("name", s.getName());
                sm.put("chip", s.getChip());
                sm.put("messageCount", s.getMessageCount());
                return sm;
            }).collect(Collectors.toList());

            long studentCount = userRepository.countByCollegeId(col.getId());

            List<CollegeChip> dbChips = collegeChipRepository.findByCollegeIdOrderByNameAsc(col.getId());
            Set<String> chipNames = new LinkedHashSet<>();
            for (CollegeChip cc : dbChips) {
                if (cc.getName() != null && !cc.getName().trim().isEmpty()) {
                    chipNames.add(cc.getName().trim().toUpperCase());
                }
            }
            for (Section s : sections) {
                if (s.getChip() != null && !s.getChip().trim().isEmpty()) {
                    chipNames.add(s.getChip().trim().toUpperCase());
                }
            }

            cMap.put("chips", new ArrayList<>(chipNames));
            cMap.put("sections", secList);
            cMap.put("sectionCount", secList.size());
            cMap.put("studentCount", studentCount);
            result.add(cMap);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/colleges")
    public ResponseEntity<?> addCollege(@RequestBody Map<String, String> body) {
        checkAdminAccess();
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "College name is required"));
        }
        name = name.trim();
        if (collegeRepository.findByName(name).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "A college with this name already exists."));
        }

        College college = College.builder().name(name).build();
        college = collegeRepository.save(college);

        return ResponseEntity.ok(Map.of(
                "message", "College created successfully!",
                "id", college.getId(),
                "name", college.getName()
        ));
    }

    @PostMapping("/colleges/{collegeId}/sections")
    public ResponseEntity<?> addSection(@PathVariable Long collegeId, @RequestBody Map<String, String> body) {
        checkAdminAccess();
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new RuntimeException("College not found"));

        String rawInput = body.get("name");
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Section name is required (e.g. CSD, CSE-1, AIML)"));
        }

        String rawChip = body.get("chip");
        String assignedChip = (rawChip != null && !rawChip.trim().isEmpty()) ? rawChip.trim().toUpperCase() : null;

        if (assignedChip != null && collegeChipRepository.findByCollegeIdAndNameIgnoreCase(collegeId, assignedChip).isEmpty()) {
            collegeChipRepository.save(CollegeChip.builder().name(assignedChip).college(college).build());
        }

        String[] parts = rawInput.split("[,\\n]+");
        java.util.List<String> createdNames = new java.util.ArrayList<>();

        for (String part : parts) {
            String clean = part.trim().toUpperCase();
            if (clean.isEmpty()) continue;

            if (assignedChip != null) {
                // If user entered just a number or letter under this chip (e.g. "1" or "A")
                if (clean.matches("^\\d+$") || clean.matches("^[A-Z]$")) {
                    clean = assignedChip + " " + clean;
                }
            } else {
                clean = clean.replaceAll("^([A-Z]+)\\s+(\\d+)$", "$1-$2");
                clean = clean.replaceAll("^([A-Z]+)(\\d+)$", "$1-$2");
            }

            String finalChip = assignedChip != null ? assignedChip : Section.deriveChip(clean);

            if (sectionRepository.findByCollegeIdAndName(collegeId, clean).isEmpty()) {
                Section section = Section.builder()
                        .name(clean)
                        .chip(finalChip)
                        .college(college)
                        .messageCount(0)
                        .build();
                sectionRepository.save(section);
                createdNames.add(clean);
            }
        }

        if (createdNames.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Section(s) already exist in this college."));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Section(s) " + String.join(", ", createdNames) + " added to " + college.getName(),
                "created", createdNames,
                "collegeId", college.getId()
        ));
    }

    @PostMapping("/colleges/{collegeId}/chips")
    @Transactional
    public ResponseEntity<?> addChip(@PathVariable Long collegeId, @RequestBody Map<String, String> body) {
        checkAdminAccess();
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new RuntimeException("College not found"));

        String rawChip = body.get("chip");
        if (rawChip == null || rawChip.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Chip name is required (e.g. CSE, CSD, ECE)"));
        }
        String chip = rawChip.trim().toUpperCase();

        // 1. Ensure Chip exists in college_chips table
        if (collegeChipRepository.findByCollegeIdAndNameIgnoreCase(collegeId, chip).isEmpty()) {
            CollegeChip cc = CollegeChip.builder()
                    .name(chip)
                    .college(college)
                    .build();
            collegeChipRepository.save(cc);
        }

        // 2. ONLY create sections if explicitly provided (do NOT default to chip name)
        String rawSections = body.get("sections");
        java.util.List<String> createdNames = new java.util.ArrayList<>();

        if (rawSections != null && !rawSections.trim().isEmpty()) {
            String[] parts = rawSections.split("[,\\n]+");
            for (String part : parts) {
                String clean = part.trim().toUpperCase();
                if (clean.isEmpty()) continue;

                if (clean.matches("^\\d+$") || clean.matches("^[A-Z]$")) {
                    clean = chip + " " + clean;
                }

                if (sectionRepository.findByCollegeIdAndName(collegeId, clean).isEmpty()) {
                    Section section = Section.builder()
                            .name(clean)
                            .chip(chip)
                            .college(college)
                            .messageCount(0)
                            .build();
                    sectionRepository.save(section);
                    createdNames.add(clean);
                }
            }
        }

        String msg = createdNames.isEmpty()
                ? "Chip " + chip + " created! Tap '+ Add Section under " + chip + "' to add sections."
                : "Chip " + chip + " created with section(s): " + String.join(", ", createdNames);

        return ResponseEntity.ok(Map.of(
                "message", msg,
                "chip", chip,
                "sections", createdNames,
                "collegeId", college.getId()
        ));
    }

    // Cascade delete a single section cleanly
    private void deleteSingleSection(Section section) {
        Long sectionId = section.getId();

        // 1. Unlink users whose primary section is this section
        try {
            List<User> affectedUsers = userRepository.findByPrimarySectionId(sectionId);
            for (User u : affectedUsers) {
                u.setPrimarySection(null);
                userRepository.save(u);
            }
        } catch (Exception e) {
            log.warn("Unlink primarySection error: {}", e.getMessage());
        }

        // 2. Detach reported messages in this section
        try {
            reportRepository.detachReportsForSection(sectionId);
        } catch (Exception e) {
            log.warn("detachReportsForSection error: {}", e.getMessage());
        }

        // 3. Delete message reactions in this section
        try {
            messageReactionRepository.deleteBySectionId(sectionId);
        } catch (Exception e) {
            log.warn("delete reactions error: {}", e.getMessage());
        }

        // 4. Delete messages in this section
        try {
            messageRepository.deleteByDestinationSectionId(sectionId);
        } catch (Exception e) {
            log.warn("delete messages error: {}", e.getMessage());
        }

        // 5. Delete section read states
        try {
            sectionReadRepository.deleteBySectionId(sectionId);
        } catch (Exception e) {
            log.warn("delete sectionRead error: {}", e.getMessage());
        }

        // 6. Delete section
        sectionRepository.delete(section);
    }

    @DeleteMapping("/sections/{sectionId}")
    @Transactional
    public ResponseEntity<?> deleteSection(@PathVariable Long sectionId) {
        checkAdminAccess();
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new RuntimeException("Section not found"));

        deleteSingleSection(section);
        return ResponseEntity.ok(Map.of("message", "Section " + section.getName() + " deleted successfully."));
    }

    @DeleteMapping("/colleges/{collegeId}/chips/{chipName}")
    @Transactional
    public ResponseEntity<?> deleteChip(@PathVariable Long collegeId, @PathVariable String chipName) {
        checkAdminAccess();
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new RuntimeException("College not found"));

        String targetChip = chipName.trim().toUpperCase();
        List<Section> allSections = sectionRepository.findByCollegeId(collegeId);
        List<Section> chipSections = allSections.stream()
                .filter(s -> targetChip.equalsIgnoreCase(s.getChip()))
                .collect(Collectors.toList());

        int count = chipSections.size();
        for (Section s : chipSections) {
            deleteSingleSection(s);
        }

        try {
            collegeChipRepository.deleteByCollegeIdAndNameIgnoreCase(collegeId, targetChip);
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
                "message", "Chip " + targetChip + " deleted successfully.",
                "deletedCount", count,
                "collegeId", collegeId
        ));
    }

    @DeleteMapping("/colleges/{collegeId}")
    @Transactional
    public ResponseEntity<?> deleteCollege(@PathVariable Long collegeId) {
        checkAdminAccess();
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new RuntimeException("College not found"));

        // 1. Delete all sections under this college
        List<Section> sections = sectionRepository.findByCollegeId(collegeId);
        for (Section s : sections) {
            deleteSingleSection(s);
        }

        // 2. Delete chips for this college
        try {
            collegeChipRepository.deleteByCollegeId(collegeId);
        } catch (Exception ignored) {}

        // 3. Unlink users belonging to this college
        try {
            List<User> collegeUsers = userRepository.findByCollegeId(collegeId);
            for (User u : collegeUsers) {
                u.setCollege(null);
                u.setPrimarySection(null);
                userRepository.save(u);
            }
        } catch (Exception e) {
            log.warn("Unlink college users error: {}", e.getMessage());
        }

        // 4. Detach reports on college messages
        try {
            reportRepository.detachReportsForCollege(collegeId);
        } catch (Exception ignored) {}

        // 5. Delete reactions and messages for this college
        try {
            messageReactionRepository.deleteByCollegeId(collegeId);
            messageRepository.deleteByCollegeId(collegeId);
        } catch (Exception ignored) {}

        // 6. Delete posts for this college
        try {
            List<Post> posts = postRepository.findByCollegeIdOrderByCreatedAtDesc(collegeId, org.springframework.data.domain.Pageable.unpaged()).getContent();
            for (Post p : posts) {
                postRepository.delete(p);
            }
        } catch (Exception ignored) {}

        // 7. Delete college
        collegeRepository.delete(college);
        return ResponseEntity.ok(Map.of("message", "College " + college.getName() + " and all associated data deleted successfully."));
    }

    // 5. User Management (Search, Suspend/Ban, Toggle Admin)
    @GetMapping("/users")
    public ResponseEntity<?> getUsers(@RequestParam(required = false) String query) {
        checkAdminAccess();
        List<User> users;
        if (query != null && !query.trim().isEmpty()) {
            String q = query.trim();
            users = userRepository.searchUsers(q);
        } else {
            users = userRepository.findAll();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("realName", u.getRealName());
            map.put("ghostName", u.getGhostName());
            map.put("email", u.getEmail());
            map.put("gender", u.getGender());
            map.put("status", u.getStatus() != null ? u.getStatus().name() : "ACTIVE");
            map.put("isAdmin", u.isAdmin());
            map.put("warPoints", u.getWarPoints());
            map.put("collegeName", u.getCollege() != null ? u.getCollege().getName() : "N/A");
            map.put("sectionName", u.getPrimarySection() != null ? u.getPrimarySection().getName() : "N/A");
            map.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/users/{userId}/toggle-status")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long userId) {
        User admin = checkAdminAccess();
        if (admin.getId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot suspend your own admin account."));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.BANNED) {
            user.setStatus(UserStatus.ACTIVE);
            user.setSuspensionReason(null);
        } else {
            user.setStatus(UserStatus.SUSPENDED);
            user.setSuspensionReason("Suspended by campus administration.");
        }
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "User status updated to " + user.getStatus(),
                "status", user.getStatus().name(),
                "userId", user.getId()
        ));
    }

    @PostMapping("/users/{userId}/toggle-admin")
    public ResponseEntity<?> toggleAdmin(@PathVariable Long userId) {
        User admin = checkAdminAccess();
        if (admin.getId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot revoke your own admin rights."));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setAdmin(!user.isAdmin());
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", user.isAdmin() ? "User granted Admin privileges." : "User Admin privileges revoked.",
                "isAdmin", user.isAdmin(),
                "userId", user.getId()
        ));
    }

    // 5c. Delete User Account permanently (same cascade as user's own delete account)
    @DeleteMapping("/users/{userId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteUserAccount(@PathVariable Long userId) {
        User admin = checkAdminAccess();
        if (admin.getId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot delete your own admin account."));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        try {
            // 1. Delete message reactions by user OR on user's messages
            jdbcTemplate.execute("DELETE FROM message_reactions WHERE user_id = " + userId + " OR message_id IN (SELECT id FROM messages WHERE author_id = " + userId + ")");

            // 2. Unlink reports pointing to user's messages, and delete reports involving user
            jdbcTemplate.execute("UPDATE reports SET reported_message_id = NULL WHERE reported_message_id IN (SELECT id FROM messages WHERE author_id = " + userId + ")");
            jdbcTemplate.execute("DELETE FROM reports WHERE reporter_id = " + userId + " OR reported_user_id = " + userId);

            // 3. Delete comments & comment likes on user's posts, and user's own comments & likes (unlink parent comments first)
            jdbcTemplate.execute("DELETE FROM comment_likes WHERE user_id = " + userId + " OR comment_id IN (SELECT id FROM comments WHERE author_id = " + userId + " OR post_id IN (SELECT id FROM posts WHERE author_id = " + userId + "))");
            jdbcTemplate.execute("UPDATE comments SET parent_comment_id = NULL WHERE parent_comment_id IN (SELECT id FROM comments WHERE author_id = " + userId + " OR post_id IN (SELECT id FROM posts WHERE author_id = " + userId + "))");
            jdbcTemplate.execute("DELETE FROM comments WHERE author_id = " + userId + " OR post_id IN (SELECT id FROM posts WHERE author_id = " + userId + ")");

            // 4. Delete post likes & saved posts for user's posts or by user
            jdbcTemplate.execute("DELETE FROM post_likes WHERE user_id = " + userId + " OR post_id IN (SELECT id FROM posts WHERE author_id = " + userId + ")");
            jdbcTemplate.execute("DELETE FROM saved_posts WHERE user_id = " + userId + " OR post_id IN (SELECT id FROM posts WHERE author_id = " + userId + ")");

            // 5. Delete user's posts
            jdbcTemplate.execute("DELETE FROM posts WHERE author_id = " + userId);

            // 6. Delete direct messages where user was sender or receiver
            jdbcTemplate.execute("DELETE FROM direct_messages WHERE sender_id = " + userId + " OR receiver_id = " + userId);

            // 7. Delete section messages authored by user
            jdbcTemplate.execute("DELETE FROM messages WHERE author_id = " + userId);

            // 8. Delete poll votes on user's polls OR cast by user, then options, then polls
            jdbcTemplate.execute("DELETE FROM poll_votes WHERE user_id = " + userId + " OR poll_id IN (SELECT id FROM polls WHERE author_id = " + userId + ")");
            jdbcTemplate.execute("DELETE FROM poll_options WHERE poll_id IN (SELECT id FROM polls WHERE author_id = " + userId + ")");
            jdbcTemplate.execute("DELETE FROM polls WHERE author_id = " + userId);

            // 9. Delete blocks, section reads, password reset tokens
            jdbcTemplate.execute("DELETE FROM blocks WHERE blocker_id = " + userId + " OR blocked_id = " + userId);
            jdbcTemplate.execute("DELETE FROM section_reads WHERE user_id = " + userId);
            jdbcTemplate.execute("DELETE FROM password_reset_tokens WHERE user_id = " + userId);

            // 10. Delete user record
            jdbcTemplate.execute("DELETE FROM users WHERE id = " + userId);

            return ResponseEntity.ok(Map.of(
                    "message", "User account deleted successfully",
                    "userId", userId,
                    "deletedUser", user.getRealName() != null ? user.getRealName() : user.getGhostName()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to delete user account: " + e.getMessage()));
        }
    }

    // 6. Broadcast Feed Announcement / Poster / Video (Pinned to TOP of Feed)
    @PostMapping("/broadcast-feed")
    public ResponseEntity<?> broadcastToFeed(@RequestBody Map<String, String> body) {
        User admin = checkAdminAccess();
        String title = body.getOrDefault("title", "CAMPUS ANNOUNCEMENT").trim();
        String content = body.getOrDefault("content", "").trim();
        String imageUrl = body.get("imageUrl");
        String videoUrl = body.get("videoUrl");
        String mediaType = body.getOrDefault("mediaType", "NONE").trim();
        String actionUrl = body.get("actionUrl");
        Long collegeId = null;
        if (body.get("collegeId") != null && !body.get("collegeId").isBlank()) {
            try {
                collegeId = Long.valueOf(body.get("collegeId"));
            } catch (Exception ignored) {}
        }

        if (videoUrl != null && !videoUrl.isBlank()) {
            mediaType = "VIDEO";
        } else if (imageUrl != null && !imageUrl.isBlank()) {
            if (imageUrl.contains("/video/") || imageUrl.toLowerCase().endsWith(".mp4") || imageUrl.toLowerCase().endsWith(".mov") || imageUrl.toLowerCase().endsWith(".webm") || imageUrl.toLowerCase().endsWith(".mkv")) {
                mediaType = "VIDEO";
                videoUrl = imageUrl;
                imageUrl = null;
            } else {
                mediaType = "IMAGE";
            }
        }

        if ("VIDEO".equalsIgnoreCase(mediaType) && (videoUrl == null || videoUrl.isBlank()) && (imageUrl != null && !imageUrl.isBlank())) {
            videoUrl = imageUrl;
            imageUrl = null;
        }

        // Prepare clean post text (no clunky [OFFICIAL ADMIN] tags)
        String fullContent = "";
        if (!content.isBlank()) {
            if (!title.isBlank() && !"CAMPUS ANNOUNCEMENT".equalsIgnoreCase(title)) {
                fullContent = title + "\n\n" + content;
            } else {
                fullContent = content;
            }
        } else if (!title.isBlank() && !"CAMPUS ANNOUNCEMENT".equalsIgnoreCase(title)) {
            fullContent = title;
        }
        if (actionUrl != null && !actionUrl.isBlank()) {
            fullContent += (fullContent.isBlank() ? "" : "\n\n") + "🔗 " + actionUrl.trim();
        }

        List<College> targetColleges;
        if (collegeId != null) {
            targetColleges = collegeRepository.findById(collegeId).map(List::of).orElse(List.of());
        } else {
            targetColleges = collegeRepository.findAll();
        }

        List<Long> postIds = new ArrayList<>();
        // If collegeId is null, create one universal post with college = null so it displays equally for ALL colleges
        if (collegeId == null) {
            Post post = Post.builder()
                    .author(admin)
                    .college(null)
                    .content(fullContent)
                    .imageUrl(imageUrl != null && !imageUrl.isBlank() ? imageUrl.trim() : null)
                    .videoUrl(videoUrl != null && !videoUrl.isBlank() ? videoUrl.trim() : null)
                    .actionUrl(actionUrl != null && !actionUrl.isBlank() ? actionUrl.trim() : null)
                    .mediaType(mediaType)
                    .isPinned(true)
                    .sharesCount(0)
                    .build();
            post = postRepository.save(post);
            postIds.add(post.getId());

            for (College col : targetColleges) {
                messagingTemplate.convertAndSend("/topic/feed/" + col.getId(), (Object) Map.of(
                        "type", "NEW_POST",
                        "postId", post.getId(),
                        "title", title,
                        "isAdmin", true
                ));
            }
        } else {
            for (College col : targetColleges) {
                Post post = Post.builder()
                        .author(admin)
                        .college(col)
                        .content(fullContent)
                        .imageUrl(imageUrl != null && !imageUrl.isBlank() ? imageUrl.trim() : null)
                        .videoUrl(videoUrl != null && !videoUrl.isBlank() ? videoUrl.trim() : null)
                        .actionUrl(actionUrl != null && !actionUrl.isBlank() ? actionUrl.trim() : null)
                        .mediaType(mediaType)
                        .isPinned(true)
                        .sharesCount(0)
                        .build();
                post = postRepository.save(post);
                postIds.add(post.getId());

                messagingTemplate.convertAndSend("/topic/feed/" + col.getId(), (Object) Map.of(
                        "type", "NEW_POST",
                        "postId", post.getId(),
                        "title", title,
                        "isAdmin", true
                ));
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Broadcasted to feed successfully!",
                "postIds", postIds
        ));
    }

    // 7. Broadcast Announcement to a Specific Section Chat (Normal chat message)
    @PostMapping("/broadcast-section")
    public ResponseEntity<?> broadcastToSection(@RequestBody Map<String, Object> body) {
        User admin = checkAdminAccess();
        Long sectionId = Long.valueOf(body.get("sectionId").toString());
        String content = body.getOrDefault("content", "").toString().trim();
        String mediaUrl = body.get("mediaUrl") != null ? body.get("mediaUrl").toString().trim() : null;
        String messageType = body.getOrDefault("messageType", "TEXT").toString().trim();

        if (mediaUrl != null && !mediaUrl.isBlank()) {
            boolean isVid = "VIDEO".equalsIgnoreCase(messageType)
                    || mediaUrl.contains("/video/")
                    || mediaUrl.toLowerCase().endsWith(".mp4")
                    || mediaUrl.toLowerCase().endsWith(".mov")
                    || mediaUrl.toLowerCase().endsWith(".webm")
                    || mediaUrl.toLowerCase().endsWith(".mkv");
            if (isVid) {
                messageType = "VIDEO";
            } else if ("IMAGE".equalsIgnoreCase(messageType) || "NONE".equalsIgnoreCase(messageType) || "TEXT".equalsIgnoreCase(messageType)) {
                messageType = "IMAGE";
            }
        }

        String finalContent = content;
        if (mediaUrl != null && !mediaUrl.isBlank()) {
            finalContent = content.isBlank() ? mediaUrl : (mediaUrl + "\n" + content);
        } else if (finalContent.isBlank()) {
            finalContent = "[OFFICIAL ANNOUNCEMENT]";
        }

        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new RuntimeException("Section not found"));

        try {
            section.setMessageCount(section.getMessageCount() + 1);
            sectionRepository.save(section);
        } catch (Exception ignored) {}

        Message message = Message.builder()
                .author(admin)
                .college(section.getCollege())
                .destinationSection(section)
                .content(finalContent)
                .messageType(messageType)
                .build();
        message = messageRepository.save(message);

        ChatResponsePayload response = ChatResponsePayload.builder()
                .messageId(message.getId())
                .senderId(admin.getId())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .ghostName("⚠️ ADMIN")
                .primarySection("System")
                .isAdmin(true)
                .createdAt(message.getCreatedAt())
                .build();

        messagingTemplate.convertAndSend("/topic/section/" + section.getId(), response);

        return ResponseEntity.ok(Map.of(
                "message", "Section announcement broadcasted to " + section.getName(),
                "messageId", message.getId()
        ));
    }

    // 8. List all Admin Broadcast Posts for management & deletion
    @GetMapping("/broadcast-posts")
    public ResponseEntity<?> getBroadcastPosts() {
        checkAdminAccess();
        List<Post> posts = postRepository.findByIsPinnedTrueOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Post p : posts) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("content", p.getContent());
            m.put("imageUrl", p.getImageUrl());
            m.put("videoUrl", p.getVideoUrl());
            m.put("mediaType", p.getMediaType());
            m.put("isPinned", Boolean.TRUE.equals(p.getIsPinned()));
            m.put("collegeName", p.getCollege() != null ? p.getCollege().getName() : "All Colleges");
            m.put("createdAt", p.getCreatedAt());
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    // 9. Delete Admin Broadcast Post
    @DeleteMapping("/broadcast-posts/{id}")
    public ResponseEntity<?> deleteBroadcastPost(@PathVariable Long id) {
        checkAdminAccess();
        postRepository.findById(id).ifPresent(post -> {
            postRepository.delete(post);
        });
        return ResponseEntity.ok(Map.of("message", "Post deleted successfully", "id", id));
    }

    // 10. Delete Section Message
    @DeleteMapping("/messages/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable Long id) {
        checkAdminAccess();
        messageRepository.findById(id).ifPresent(msg -> {
            messageRepository.delete(msg);
        });
        return ResponseEntity.ok(Map.of("message", "Message deleted successfully", "id", id));
    }
}

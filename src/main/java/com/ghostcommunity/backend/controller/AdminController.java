package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.*;
import com.ghostcommunity.backend.payload.ChatResponsePayload;
import com.ghostcommunity.backend.payload.MessageResponse;
import com.ghostcommunity.backend.repository.*;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final MessageRepository messageRepository;
    private final CollegeRepository collegeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Helper method to guarantee ONLY Admins can execute these actions
    private User checkAdminAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        if (!user.isAdmin()) {
            throw new RuntimeException("Error: Unauthorized. Admin access required.");
        }
        return user;
    }

    // 1. View all Pending Reports (This is where the REAL NAME is exposed to the Admin)
    record AdminReportDto(Long reportId, String reason, String reportedRealName, String reportedGhostName, String messageContent) {}

    @GetMapping("/reports")
    public ResponseEntity<List<AdminReportDto>> getPendingReports() {
        checkAdminAccess();
        List<AdminReportDto> reports = reportRepository.findAll().stream()
                .filter(r -> r.getStatus().equals("PENDING"))
                .map(r -> new AdminReportDto(
                        r.getId(),
                        r.getReason(),
                        r.getReportedUser().getRealName(), // Exposed to admin!
                        r.getReportedUser().getGhostName(),
                        r.getReportedMessage() != null ? r.getReportedMessage().getContent() : "N/A"
                ))
                .toList();
        return ResponseEntity.ok(reports);
    }

    // 2. Resolve a Report
    @PutMapping("/reports/{reportId}/resolve")
    public ResponseEntity<?> resolveReport(@PathVariable Long reportId) {
        checkAdminAccess();
        Report report = reportRepository.findById(reportId).orElseThrow();
        report.setStatus("RESOLVED");
        reportRepository.save(report);
        return ResponseEntity.ok(new MessageResponse("Report marked as resolved."));
    }

    // 3. Suspend a Toxic User
    @PutMapping("/users/{userId}/suspend")
    public ResponseEntity<?> suspendUser(@PathVariable Long userId) {
        checkAdminAccess();
        User user = userRepository.findById(userId).orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse(user.getRealName() + " has been suspended and locked out of the app."));
    }

    // 4. Broadcast Global Announcement (Text, Image, Video)
    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcastAnnouncement(@RequestBody Map<String, String> payload) {
        User admin = checkAdminAccess();
        String content = payload.get("content");
        String messageType = payload.getOrDefault("messageType", "TEXT"); // Can be IMAGE or VIDEO

        // Loop through all colleges and broadcast the Admin message
        List<College> colleges = collegeRepository.findAll();
        for (College college : colleges) {
            Message message = Message.builder()
                    .author(admin)
                    .college(college)
                    .destinationSection(null) // Broadcast to All College feeds
                    .content(content)
                    .messageType(messageType) // Allows sending an Image URL or Video URL!
                    .build();
            message = messageRepository.save(message);

            // Create the special Admin Identity packet
            ChatResponsePayload response = ChatResponsePayload.builder()
                    .messageId(message.getId())
                    .content(message.getContent())
                    .messageType(message.getMessageType())
                    .ghostName("⚠️ ADMIN ANNOUNCEMENT") // Overrides normal ghost logic
                    .primarySection("System")
                    .createdAt(message.getCreatedAt())
                    .build();

            // Push it live to everyone's phone in this college!
            messagingTemplate.convertAndSend("/topic/college/" + college.getId(), response);
        }

        return ResponseEntity.ok(new MessageResponse("Announcement broadcasted successfully to all students!"));
    }
}

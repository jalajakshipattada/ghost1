package com.ghostcommunity.backend;

import com.ghostcommunity.backend.controller.AdminController;
import com.ghostcommunity.backend.controller.ModerationController;
import com.ghostcommunity.backend.model.*;
import com.ghostcommunity.backend.repository.*;
import com.ghostcommunity.backend.security.JwtUtils;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportModerationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private DirectMessageRepository directMessageRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private CollegeRepository collegeRepository;

    @Mock
    private CollegeChipRepository collegeChipRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private SectionReadRepository sectionReadRepository;

    @Mock
    private MessageReactionRepository messageReactionRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private JwtUtils jwtUtils;

    private ModerationController moderationController;
    private AdminController adminController;

    private User reporter;
    private User reportedUser;
    private College testCollege;
    private Section testSection;

    @BeforeEach
    void setUp() {
        moderationController = new ModerationController(
                userRepository,
                messageRepository,
                directMessageRepository,
                postRepository,
                reportRepository,
                blockRepository
        );

        adminController = new AdminController(
                userRepository,
                reportRepository,
                messageRepository,
                directMessageRepository,
                collegeRepository,
                collegeChipRepository,
                sectionRepository,
                sectionReadRepository,
                messageReactionRepository,
                postRepository,
                messagingTemplate,
                jwtUtils
        );

        testCollege = College.builder().id(1L).name("Test University").build();
        testSection = Section.builder().id(10L).name("CSD-A").college(testCollege).build();

        reporter = User.builder()
                .id(1L)
                .realName("Alice Reporter")
                .ghostName("GhostAlice")
                .email("alice@campus.edu")
                .passwordHash("hashedpwd")
                .college(testCollege)
                .primarySection(testSection)
                .isAdmin(true)
                .status(UserStatus.ACTIVE)
                .build();

        reportedUser = User.builder()
                .id(2L)
                .realName("Bob Offender")
                .ghostName("GhostBob")
                .email("bob@campus.edu")
                .passwordHash("hashedpwd")
                .college(testCollege)
                .primarySection(testSection)
                .isAdmin(false)
                .status(UserStatus.ACTIVE)
                .build();

        // Setup default mock for reportRepository.save to set ID
        lenient().when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report r = invocation.getArgument(0);
            if (r.getId() == null) {
                r.setId(99L);
            }
            return r;
        });

        // Setup Security Context with reporter as authenticated principal
        UserDetailsImpl userDetails = UserDetailsImpl.build(reporter);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    void testReportCommunityMessage_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        Message message = Message.builder()
                .id(100L)
                .author(reportedUser)
                .college(testCollege)
                .destinationSection(testSection)
                .content("Inappropriate message content")
                .messageType("TEXT")
                .build();
        when(messageRepository.findById(100L)).thenReturn(Optional.of(message));

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", 100);
        payload.put("chatType", "COMMUNITY");
        payload.put("reason", "Harassment / Bullying");

        ResponseEntity<?> response = moderationController.reportAnyMessage(payload);

        assertEquals(200, response.getStatusCode().value());

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository, times(1)).save(captor.capture());

        Report savedReport = captor.getValue();
        assertNotNull(savedReport);
        assertEquals(reporter, savedReport.getReporter());
        assertEquals(reportedUser, savedReport.getReportedUser());
        assertEquals("COMMUNITY", savedReport.getChatType());
        assertEquals("Inappropriate message content", savedReport.getMessageContent());
        assertEquals("Harassment / Bullying", savedReport.getReason());
        assertEquals("PENDING", savedReport.getStatus());
        assertEquals("CSD-A", savedReport.getSectionName());
    }

    @Test
    void testReportFeedPost_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        Post post = Post.builder()
                .id(200L)
                .author(reportedUser)
                .college(testCollege)
                .content("Offensive feed post")
                .imageUrl("https://res.cloudinary.com/demo/image.png")
                .build();
        when(postRepository.findById(200L)).thenReturn(Optional.of(post));

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", 200);
        payload.put("chatType", "POST");
        payload.put("reason", "Hate Speech");

        ResponseEntity<?> response = moderationController.reportAnyMessage(payload);

        assertEquals(200, response.getStatusCode().value());

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository, times(1)).save(captor.capture());

        Report savedReport = captor.getValue();
        assertNotNull(savedReport);
        assertEquals("POST", savedReport.getChatType());
        assertEquals("Offensive feed post", savedReport.getMessageContent());
        assertEquals("https://res.cloudinary.com/demo/image.png", savedReport.getMediaUrl());
        assertEquals("IMAGE", savedReport.getMediaType());
        assertEquals("Hate Speech", savedReport.getReason());
        assertEquals(reportedUser, savedReport.getReportedUser());
    }

    @Test
    void testReportDirectMessage_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        DirectMessage dm = DirectMessage.builder()
                .id(300L)
                .sender(reportedUser)
                .receiver(reporter)
                .content("Inappropriate direct message")
                .messageType("TEXT")
                .build();
        when(directMessageRepository.findById(300L)).thenReturn(Optional.of(dm));

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", 300);
        payload.put("chatType", "DIRECT");
        payload.put("reason", "Spam");

        ResponseEntity<?> response = moderationController.reportAnyMessage(payload);

        assertEquals(200, response.getStatusCode().value());

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository, times(1)).save(captor.capture());

        Report savedReport = captor.getValue();
        assertNotNull(savedReport);
        assertEquals("DIRECT", savedReport.getChatType());
        assertEquals("Inappropriate direct message", savedReport.getMessageContent());
        assertEquals(reportedUser, savedReport.getReportedUser());
    }

    @Test
    void testAdminGetAllReports_UnmasksIdentitiesCorrectly() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        Report r1 = Report.builder()
                .id(1L)
                .reporter(reporter)
                .reportedUser(reportedUser)
                .chatType("COMMUNITY")
                .messageContent("Spam link http://spam.com")
                .sectionName("CSD-A")
                .reason("Spam / Misleading")
                .status("PENDING")
                .adminAction("NONE")
                .createdAt(LocalDateTime.now())
                .build();

        when(reportRepository.findAllWithUsersAndColleges()).thenReturn(Collections.singletonList(r1));

        ResponseEntity<?> response = adminController.getAllReports(null);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof List);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reportsList = (List<Map<String, Object>>) response.getBody();
        assertEquals(1, reportsList.size());

        Map<String, Object> reportMap = reportsList.get(0);
        assertEquals(1L, reportMap.get("id"));
        assertEquals("COMMUNITY", reportMap.get("chatType"));
        assertEquals("Spam link http://spam.com", reportMap.get("messageContent"));
        assertEquals("Spam / Misleading", reportMap.get("reason"));
        assertEquals("PENDING", reportMap.get("status"));

        // Verify unmasked reportedUser details
        @SuppressWarnings("unchecked")
        Map<String, Object> unmaskedUser = (Map<String, Object>) reportMap.get("reportedUser");
        assertNotNull(unmaskedUser);
        assertEquals("Bob Offender", unmaskedUser.get("realName"));
        assertEquals("GhostBob", unmaskedUser.get("ghostName"));
        assertEquals("Test University", unmaskedUser.get("collegeName"));
        assertEquals("CSD-A", unmaskedUser.get("sectionName"));

        // Verify unmasked reporter details
        @SuppressWarnings("unchecked")
        Map<String, Object> unmaskedReporter = (Map<String, Object>) reportMap.get("reporter");
        assertNotNull(unmaskedReporter);
        assertEquals("Alice Reporter", unmaskedReporter.get("realName"));
        assertEquals("GhostAlice", unmaskedReporter.get("ghostName"));
    }

    @Test
    void testAdminGetAllReports_FilteredByStatus() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByStatusWithUsersAndColleges("PENDING")).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = adminController.getAllReports("PENDING");

        assertEquals(200, response.getStatusCode().value());
        verify(reportRepository, times(1)).findByStatusWithUsersAndColleges("PENDING");
        verify(reportRepository, never()).findAllWithUsersAndColleges();
    }

    @Test
    void testAdminActionReport_Dismiss() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        Report r = Report.builder()
                .id(5L)
                .status("PENDING")
                .adminAction("NONE")
                .build();
        when(reportRepository.findById(5L)).thenReturn(Optional.of(r));

        ResponseEntity<?> response = adminController.actionReport(5L, Map.of("action", "DISMISS"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("DISMISSED", r.getStatus());
        assertEquals("DISMISSED", r.getAdminAction());
        verify(reportRepository, times(1)).save(r);
    }

    @Test
    void testAdminActionReport_DeleteCommunityMessage() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        Message msg = Message.builder()
                .id(100L)
                .content("Nasty message")
                .messageType("TEXT")
                .build();

        Report r = Report.builder()
                .id(6L)
                .chatType("COMMUNITY")
                .reportedMessage(msg)
                .status("PENDING")
                .adminAction("NONE")
                .build();
        when(reportRepository.findById(6L)).thenReturn(Optional.of(r));

        ResponseEntity<?> response = adminController.actionReport(6L, Map.of("action", "DELETE_MESSAGE"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("RESOLVED", r.getStatus());
        assertEquals("DELETED_MESSAGE", r.getAdminAction());
        assertEquals("[This message was removed by Campus Moderation]", msg.getContent());
        verify(messageRepository, times(1)).save(msg);
        verify(reportRepository, times(1)).save(r);
    }

    @Test
    void testAdminActionReport_DeletePost() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        Report r = Report.builder()
                .id(7L)
                .chatType("POST")
                .directMessageId(200L)
                .status("PENDING")
                .adminAction("NONE")
                .build();
        when(reportRepository.findById(7L)).thenReturn(Optional.of(r));

        ResponseEntity<?> response = adminController.actionReport(7L, Map.of("action", "DELETE_MESSAGE"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("RESOLVED", r.getStatus());
        assertEquals("DELETED_MESSAGE", r.getAdminAction());
        verify(postRepository, times(1)).deleteById(200L);
        verify(reportRepository, times(1)).save(r);
    }

    @Test
    void testAdminActionReport_BanUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        Report r = Report.builder()
                .id(8L)
                .reportedUser(reportedUser)
                .status("PENDING")
                .adminAction("NONE")
                .build();
        when(reportRepository.findById(8L)).thenReturn(Optional.of(r));

        ResponseEntity<?> response = adminController.actionReport(8L, Map.of("action", "BAN_USER"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("RESOLVED", r.getStatus());
        assertEquals("BANNED_USER", r.getAdminAction());
        assertEquals(UserStatus.SUSPENDED, reportedUser.getStatus());
        verify(userRepository, times(1)).save(reportedUser);
        verify(reportRepository, times(1)).save(r);
    }
}

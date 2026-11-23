package com.ghostcommunity.backend;

import com.ghostcommunity.backend.controller.AdminController;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.repository.*;
import com.ghostcommunity.backend.security.JwtUtils;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class AdminDeleteUserAccountTest {

    @Mock private UserRepository userRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private DirectMessageRepository directMessageRepository;
    @Mock private CollegeRepository collegeRepository;
    @Mock private CollegeChipRepository collegeChipRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private SectionReadRepository sectionReadRepository;
    @Mock private MessageReactionRepository messageReactionRepository;
    @Mock private PostRepository postRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private JwtUtils jwtUtils;
    @Mock private JdbcTemplate jdbcTemplate;

    @Mock private Authentication authentication;
    @Mock private SecurityContext securityContext;

    private AdminController adminController;
    private User adminUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

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
                jwtUtils,
                jdbcTemplate
        );

        adminUser = User.builder()
                .id(1L)
                .email("admin@campus.edu")
                .realName("Super Admin")
                .ghostName("AdminGhost")
                .isAdmin(true)
                .build();

        targetUser = User.builder()
                .id(200L)
                .email("student@campus.edu")
                .realName("Bad Actor")
                .ghostName("TrollGhost")
                .isAdmin(false)
                .build();

        UserDetailsImpl adminDetails = UserDetailsImpl.build(adminUser);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(adminDetails);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(200L)).thenReturn(Optional.of(targetUser));
    }

    @Test
    void testAdminDeleteUserAccount_Success() {
        ResponseEntity<?> response = adminController.deleteUserAccount(200L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("User account deleted successfully", body.get("message"));
        assertEquals(200L, body.get("userId"));

        // Verify full cascade execution matching user's delete account feature
        verify(jdbcTemplate, atLeastOnce()).execute(contains("DELETE FROM message_reactions WHERE user_id = 200"));
        verify(jdbcTemplate, atLeastOnce()).execute(contains("DELETE FROM comments WHERE author_id = 200"));
        verify(jdbcTemplate, atLeastOnce()).execute(contains("DELETE FROM posts WHERE author_id = 200"));
        verify(jdbcTemplate, atLeastOnce()).execute(contains("DELETE FROM direct_messages WHERE sender_id = 200 OR receiver_id = 200"));
        verify(jdbcTemplate, atLeastOnce()).execute(contains("DELETE FROM messages WHERE author_id = 200"));
        verify(jdbcTemplate, atLeastOnce()).execute(contains("DELETE FROM polls WHERE author_id = 200"));
        verify(jdbcTemplate, atLeastOnce()).execute(contains("DELETE FROM users WHERE id = 200"));
    }

    @Test
    void testAdminDeleteSelf_ThrowsBadRequest() {
        ResponseEntity<?> response = adminController.deleteUserAccount(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("You cannot delete your own admin account.", body.get("error"));

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void testAdminDeleteUser_NotFound_ThrowsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> adminController.deleteUserAccount(999L));
        verify(jdbcTemplate, never()).execute(anyString());
    }
}

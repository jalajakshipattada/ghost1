package com.ghostcommunity.backend;

import com.ghostcommunity.backend.controller.ProfileController;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class ProfileAccountTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private ProfileController profileController;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        testUser = User.builder()
                .id(999L)
                .email("testuser@college.edu")
                .realName("Test User")
                .ghostName("TestGhost")
                .build();

        UserDetailsImpl userDetails = UserDetailsImpl.build(testUser);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testDeleteMyAccount_Success() {
        ResponseEntity<?> response = profileController.deleteMyAccount();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Account deleted successfully", body.get("message"));

        // Verify key cascades were executed
        verify(jdbcTemplate, atLeastOnce()).execute(contains("DELETE FROM message_reactions WHERE user_id = 999"));
        verify(jdbcTemplate, atLeastOnce()).execute(contains("DELETE FROM posts WHERE author_id = 999"));
        verify(jdbcTemplate, atLeastOnce()).execute(contains("DELETE FROM direct_messages WHERE sender_id = 999 OR receiver_id = 999"));
        verify(jdbcTemplate, atLeastOnce()).execute(contains("DELETE FROM users WHERE id = 999"));
    }

    @Test
    void testDeleteMyAccount_Unauthorized() {
        when(securityContext.getAuthentication()).thenReturn(null);
        ResponseEntity<?> response = profileController.deleteMyAccount();
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(jdbcTemplate, never()).execute(anyString());
    }
}

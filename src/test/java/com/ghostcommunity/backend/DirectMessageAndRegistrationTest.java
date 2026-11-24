package com.ghostcommunity.backend;

import com.ghostcommunity.backend.controller.AuthController;
import com.ghostcommunity.backend.controller.DirectMessageController;
import com.ghostcommunity.backend.controller.ProfileController;
import com.ghostcommunity.backend.model.DirectMessage;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.model.UserStatus;
import com.ghostcommunity.backend.payload.ChatResponsePayload;
import com.ghostcommunity.backend.payload.SignupRequest;
import com.ghostcommunity.backend.repository.DirectMessageRepository;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DirectMessageAndRegistrationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DirectMessageRepository dmRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private AuthController authController;

    @InjectMocks
    private DirectMessageController dmController;

    @InjectMocks
    private ProfileController profileController;

    private User currentUser;
    private User partnerUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        currentUser = User.builder()
                .id(1L)
                .email("me@college.edu")
                .realName("Alice Smith")
                .ghostName("ShadowAlice")
                .status(UserStatus.ACTIVE)
                .build();

        partnerUser = User.builder()
                .id(2L)
                .email("dhanush646452@gmail.com")
                .realName("Dhanush R")
                .ghostName("Jannu")
                .profileImageUrl("https://cdn.example.com/dhanush.jpg")
                .status(UserStatus.ACTIVE)
                .build();

        UserDetailsImpl userDetails = UserDetailsImpl.build(currentUser);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(partnerUser));
    }

    @Test
    void testRegistration_SavesExplicitRealName() {
        SignupRequest request = new SignupRequest();
        request.setEmail("newstudent@college.edu");
        request.setPassword("securePassword123");
        request.setRealName("Dhanush R");

        when(userRepository.existsByEmail("newstudent@college.edu")).thenReturn(false);
        when(passwordEncoder.encode("securePassword123")).thenReturn("encodedHash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = authController.registerUser(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("Dhanush R", savedUser.getRealName());
        assertEquals("newstudent@college.edu", savedUser.getEmail());
    }

    @Test
    void testRegistration_FallsBackGracefullyWhenRealNameMissing() {
        SignupRequest request = new SignupRequest();
        request.setEmail("chirag02@college.edu");
        request.setPassword("securePassword123");
        request.setRealName("   "); // empty / blank

        when(userRepository.existsByEmail("chirag02@college.edu")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = authController.registerUser(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("Chirag02", savedUser.getRealName());
    }

    @Test
    void testDirectInbox_ReturnsPartnerRealNameAndProfilePhoto() {
        DirectMessage dm = DirectMessage.builder()
                .id(100L)
                .sender(currentUser)
                .receiver(partnerUser)
                .content("Hey Dhanush, see you in lab!")
                .chatMode("DIRECT")
                .createdAt(LocalDateTime.now())
                .build();

        when(dmRepository.findAllUserMessagesByMode(1L, "DIRECT")).thenReturn(List.of(dm));

        ResponseEntity<?> response = dmController.getInbox("DIRECT");
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<?> list = (List<?>) response.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());

        // Cast to InboxDto record
        Object item = list.get(0);
        assertTrue(item.toString().contains("Dhanush R"));
        assertTrue(item.toString().contains("https://cdn.example.com/dhanush.jpg"));
        // Ensure Ghost handle Jannu is NOT used as the real name
        assertFalse(item.toString().contains("partnerRealName=Jannu"));
    }

    @Test
    void testGhostInbox_KeepsRealIdentityAnonymous() {
        DirectMessage dm = DirectMessage.builder()
                .id(200L)
                .sender(partnerUser)
                .receiver(currentUser)
                .content("Ghost whisper message")
                .chatMode("GHOST")
                .createdAt(LocalDateTime.now())
                .build();

        when(dmRepository.findAllUserMessagesByMode(1L, "GHOST")).thenReturn(List.of(dm));

        ResponseEntity<?> response = dmController.getInbox("GHOST");
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<?> list = (List<?>) response.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());

        Object item = list.get(0);
        assertTrue(item.toString().contains("Jannu"));
        // Ghost inbox hides real name & profile pic
        assertFalse(item.toString().contains("partnerRealName=Dhanush R"));
        assertFalse(item.toString().contains("partnerProfileImageUrl=https://cdn.example.com/dhanush.jpg"));
    }

    @Test
    void testDirectChatHistory_DisplaysRealNameForSender() {
        DirectMessage dm = DirectMessage.builder()
                .id(300L)
                .sender(partnerUser)
                .receiver(currentUser)
                .content("Hello direct from Dhanush")
                .messageType("TEXT")
                .chatMode("DIRECT")
                .createdAt(LocalDateTime.now())
                .build();

        when(dmRepository.findChatHistoryByMode(1L, 2L, "DIRECT")).thenReturn(List.of(dm));

        ResponseEntity<?> response = dmController.getHistory(2L, "DIRECT");
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<ChatResponsePayload> payloads = (List<ChatResponsePayload>) response.getBody();
        assertNotNull(payloads);
        assertEquals(1, payloads.size());

        ChatResponsePayload payload = payloads.get(0);
        assertEquals("Dhanush R", payload.getGhostName()); // In DIRECT mode, ghostName field holds real name
        assertNotEquals("Jannu", payload.getGhostName());
    }

    @Test
    void testUpdateProfile_AllowsUserToUpdateRealName() {
        ResponseEntity<?> response = profileController.updateProfile(Map.of("realName", "Dhanush Raj"));
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("Dhanush Raj", captor.getValue().getRealName());
    }

    @Test
    void testGetUnreadCounts_ReturnsDirectGhostAndTotalCounts() {
        when(dmRepository.countTotalUnread(1L)).thenReturn(6L);
        when(dmRepository.countTotalUnreadByMode(1L, "DIRECT")).thenReturn(4L);
        when(dmRepository.countTotalUnreadByMode(1L, "GHOST")).thenReturn(2L);

        ResponseEntity<?> response = dmController.getUnreadCounts();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<?, ?> map = (Map<?, ?>) response.getBody();
        assertNotNull(map);
        assertEquals(6L, map.get("totalUnread"));
        assertEquals(4L, map.get("directUnread"));
        assertEquals(2L, map.get("ghostUnread"));
    }

    @Test
    void testMarkAsRead_MarksPartnerMessagesRead() {
        when(dmRepository.markMessagesAsRead(1L, 2L, "DIRECT")).thenReturn(3);

        ResponseEntity<?> response = dmController.markAsRead(2L, "DIRECT");
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<?, ?> map = (Map<?, ?>) response.getBody();
        assertNotNull(map);
        assertEquals(true, map.get("success"));
        assertEquals(3, map.get("markedCount"));
        verify(dmRepository).markMessagesAsRead(1L, 2L, "DIRECT");
    }

    @Test
    void testGetInbox_IncludesUnreadCountPerPartner() {
        DirectMessage dm = DirectMessage.builder()
                .id(400L)
                .sender(partnerUser)
                .receiver(currentUser)
                .content("Hey there, unread message!")
                .chatMode("DIRECT")
                .createdAt(LocalDateTime.now())
                .build();

        when(dmRepository.findAllUserMessagesByMode(1L, "DIRECT")).thenReturn(List.of(dm));
        when(dmRepository.countUnreadMessagesFromPartner(1L, 2L, "DIRECT")).thenReturn(5L);

        ResponseEntity<?> response = dmController.getInbox("DIRECT");
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<?> list = (List<?>) response.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());
        assertTrue(list.get(0).toString().contains("unreadCount=5"));
    }
}

package com.ghostcommunity.backend;

import com.ghostcommunity.backend.controller.FeedController;
import com.ghostcommunity.backend.controller.MessageController;
import com.ghostcommunity.backend.model.*;
import com.ghostcommunity.backend.payload.ChatResponsePayload;
import com.ghostcommunity.backend.repository.*;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PostShareFlowTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SectionReadRepository sectionReadRepository;

    @Mock
    private MessageReactionRepository messageReactionRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentLikeRepository commentLikeRepository;

    @Mock
    private SavedPostRepository savedPostRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private FeedController feedController;

    @InjectMocks
    private MessageController messageController;

    private College testCollege;
    private Section testSection;
    private User authorA;
    private User sharerB;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        testCollege = College.builder().id(1L).name("Test College").build();
        testSection = Section.builder().id(10L).name("CIV-1").college(testCollege).build();

        authorA = User.builder()
                .id(101L)
                .realName("Alice")
                .ghostName("GhostAlice")
                .college(testCollege)
                .primarySection(testSection)
                .build();

        sharerB = User.builder()
                .id(102L)
                .realName("Bob")
                .ghostName("GhostBob")
                .email("bob.student@college.edu")
                .college(testCollege)
                .primarySection(testSection)
                .build();

        UserDetailsImpl userDetails = UserDetailsImpl.build(sharerB);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findById(102L)).thenReturn(Optional.of(sharerB));
        when(userRepository.findByIdWithSectionAndCollege(102L)).thenReturn(Optional.of(sharerB));
        when(sectionRepository.findById(10L)).thenReturn(Optional.of(testSection));
    }

    @Test
    void testDirectPostSharedByDirectShowsRealName() {
        Post post = Post.builder()
                .id(1L)
                .author(authorA)
                .college(testCollege)
                .content("[DIRECT|Alice] Excited about tomorrow's campus event!")
                .build();

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(501L);
            return m;
        });

        Map<String, Object> shareReq = new HashMap<>();
        shareReq.put("sectionId", 10L);
        shareReq.put("asDirect", true);
        shareReq.put("chatMode", "DIRECT");
        shareReq.put("sharedByName", "Bob");

        ResponseEntity<?> response = feedController.sharePost(1L, shareReq);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        Message saved = messageCaptor.getValue();

        assertNotNull(saved);
        assertEquals("POST_SHARE", saved.getMessageType());
        assertTrue(saved.getContent().contains("\"sharedByDirect\":true"));
        assertTrue(saved.getContent().contains("\"sharedByName\":\"Bob\""));
        assertTrue(saved.getContent().contains("\"authorName\":\"Alice\""));
        assertTrue(saved.getContent().contains("\"isDirect\":true"));
        assertFalse(saved.getContent().contains("⚠️ ADMIN"));
        assertFalse(saved.getContent().contains("GhostBob"));
    }

    @Test
    void testDirectShareWhenSharerRealNameIsStudentFallsBackToEmailPrefix() {
        sharerB.setRealName("Student");

        Post post = Post.builder()
                .id(2L)
                .author(authorA)
                .college(testCollege)
                .content("[DIRECT|Alice] Event update")
                .build();

        when(postRepository.findById(2L)).thenReturn(Optional.of(post));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(502L);
            return m;
        });

        Map<String, Object> shareReq = new HashMap<>();
        shareReq.put("sectionId", 10L);
        shareReq.put("asDirect", true);
        shareReq.put("chatMode", "DIRECT");
        shareReq.put("sharedByName", "Student"); // Even if client sent Student

        ResponseEntity<?> response = feedController.sharePost(2L, shareReq);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        Message saved = messageCaptor.getValue();

        // bob.student@college.edu -> Bob.student
        assertTrue(saved.getContent().contains("\"sharedByDirect\":true"));
        assertTrue(saved.getContent().contains("\"sharedByName\":\"Bob.student\""));
        assertFalse(saved.getContent().contains("GhostBob"));
    }

    @Test
    void testGhostShareUsesGhostName() {
        Post post = Post.builder()
                .id(3L)
                .author(authorA)
                .college(testCollege)
                .content("Anonymous post by author")
                .build();

        when(postRepository.findById(3L)).thenReturn(Optional.of(post));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(503L);
            return m;
        });

        Map<String, Object> shareReq = new HashMap<>();
        shareReq.put("sectionId", 10L);
        shareReq.put("asDirect", false);
        shareReq.put("chatMode", "GHOST");
        shareReq.put("sharedByName", "GhostBob");

        ResponseEntity<?> response = feedController.sharePost(3L, shareReq);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        Message saved = messageCaptor.getValue();

        assertTrue(saved.getContent().contains("\"sharedByDirect\":false"));
        assertTrue(saved.getContent().contains("\"sharedByName\":\"GhostBob\""));
        assertTrue(saved.getContent().contains("\"authorName\":\"GhostAlice\""));
    }

    @Test
    void testAdOrSpotlightPostAlwaysUsesCampusSpotlight() {
        Post adPost = Post.builder()
                .id(4L)
                .author(authorA)
                .college(testCollege)
                .content("Campus Tech Fest Announcement")
                .actionUrl("https://college.edu/fest")
                .mediaType("AD")
                .build();

        when(postRepository.findById(4L)).thenReturn(Optional.of(adPost));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(504L);
            return m;
        });

        Map<String, Object> shareReq = new HashMap<>();
        shareReq.put("sectionId", 10L);
        shareReq.put("asDirect", true);
        shareReq.put("chatMode", "DIRECT");
        shareReq.put("sharedByName", "Bob");

        ResponseEntity<?> response = feedController.sharePost(4L, shareReq);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        Message saved = messageCaptor.getValue();

        assertTrue(saved.getContent().contains("\"authorName\":\"Campus Spotlight\""));
        assertTrue(saved.getContent().contains("\"isPinned\":true"));
    }

    @Test
    void testGetSectionHistoryReturnsCorrectIsMeAndSharerName() {
        String directShareContent = "[POST_SHARE]{\"postId\":1,\"authorName\":\"Alice\",\"imageUrl\":\"\",\"videoUrl\":\"\",\"mediaType\":\"NONE\",\"caption\":\"Hello\",\"isPinned\":false,\"sharedByDirect\":true,\"sharedByName\":\"Bob\"}";
        Message msgByBob = Message.builder()
                .id(1001L)
                .author(sharerB)
                .college(testCollege)
                .destinationSection(testSection)
                .content(directShareContent)
                .messageType("POST_SHARE")
                .createdAt(LocalDateTime.now())
                .build();

        when(messageRepository.findByDestinationSectionIdOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(msgByBob));
        when(messageReactionRepository.countReactionsBySectionId(10L))
                .thenReturn(Collections.emptyList());

        // When Bob views history
        ResponseEntity<?> responseForBob = messageController.getSectionHistory(10L);
        assertEquals(HttpStatus.OK, responseForBob.getStatusCode());
        List<ChatResponsePayload> historyForBob = (List<ChatResponsePayload>) responseForBob.getBody();
        assertNotNull(historyForBob);
        assertEquals(1, historyForBob.size());
        assertTrue(historyForBob.get(0).getIsMe());
        assertEquals("Bob", historyForBob.get(0).getGhostName());

        // When Alice views history
        UserDetailsImpl aliceDetails = UserDetailsImpl.build(authorA);
        when(authentication.getPrincipal()).thenReturn(aliceDetails);
        when(userRepository.findById(101L)).thenReturn(Optional.of(authorA));

        ResponseEntity<?> responseForAlice = messageController.getSectionHistory(10L);
        assertEquals(HttpStatus.OK, responseForAlice.getStatusCode());
        List<ChatResponsePayload> historyForAlice = (List<ChatResponsePayload>) responseForAlice.getBody();
        assertNotNull(historyForAlice);
        assertEquals(1, historyForAlice.size());
        assertFalse(historyForAlice.get(0).getIsMe()); // Not Alice's message
        assertEquals("Bob", historyForAlice.get(0).getGhostName()); // Sharer name is Bob
    }

    @Test
    void testAdminPostSharedAlwaysUsesCampusSpotlight() {
        User adminUser = User.builder()
                .id(999L)
                .realName("Chirag")
                .ghostName("hirag")
                .isAdmin(true)
                .college(testCollege)
                .primarySection(testSection)
                .gender("FEMALE")
                .build();

        Post adminPost = Post.builder()
                .id(55L)
                .author(adminUser)
                .college(testCollege)
                .content("Special Promotional Fest Announcement")
                .mediaType("VIDEO")
                .videoUrl("https://cloudinary.com/video.mp4")
                .isPinned(true)
                .build();

        when(postRepository.findById(55L)).thenReturn(Optional.of(adminPost));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(555L);
            return m;
        });

        Map<String, Object> shareReq = new HashMap<>();
        shareReq.put("sectionId", 10L);
        shareReq.put("asDirect", false);
        shareReq.put("chatMode", "GHOST");
        shareReq.put("sharedByName", "GhostBob");

        ResponseEntity<?> response = feedController.sharePost(55L, shareReq);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        Message saved = messageCaptor.getValue();

        assertTrue(saved.getContent().contains("\"authorName\":\"Campus Spotlight\""));
        assertTrue(saved.getContent().contains("\"isPinned\":true"));
        assertFalse(saved.getContent().contains("hirag"));
    }

    @Test
    void testAdminUserPostsThroughDirect_UsesRealNameNotCampusSpotlight() {
        User adminUser = User.builder()
                .id(999L)
                .realName("Chirag")
                .ghostName("PhantomChirag")
                .profileImageUrl("https://example.com/chirag.jpg")
                .isAdmin(true)
                .college(testCollege)
                .primarySection(testSection)
                .build();

        Post directPost = Post.builder()
                .id(66L)
                .author(adminUser)
                .college(testCollege)
                .content("[DIRECT|Chirag] My personal project photo")
                .imageUrl("https://example.com/photo.jpg")
                .mediaType("IMAGE")
                .isPinned(false)
                .build();

        Map<String, Object> mapped = feedController.formatPostMap(directPost, adminUser);
        assertEquals("Chirag", mapped.get("authorRealName"));
        assertEquals("https://example.com/chirag.jpg", mapped.get("authorProfileImageUrl"));
        assertEquals(false, mapped.get("isPinned"));
        assertEquals(false, mapped.get("isAdminPost"));
    }

    @Test
    void testAdminUserPostsThroughGhost_UsesGhostNameNotCampusSpotlight() {
        User adminUser = User.builder()
                .id(999L)
                .realName("Chirag")
                .ghostName("PhantomChirag")
                .isAdmin(true)
                .college(testCollege)
                .primarySection(testSection)
                .build();

        Post ghostPost = Post.builder()
                .id(67L)
                .author(adminUser)
                .college(testCollege)
                .content("Casual campus thought")
                .mediaType("NONE")
                .isPinned(false)
                .build();

        Map<String, Object> mapped = feedController.formatPostMap(ghostPost, adminUser);
        assertEquals("PhantomChirag", mapped.get("authorGhostName"));
        assertNotEquals("Campus Spotlight", mapped.get("authorGhostName"));
        assertNotEquals("⚠️ ADMIN", mapped.get("authorGhostName"));
        assertEquals(false, mapped.get("isPinned"));
        assertEquals(false, mapped.get("isAdminPost"));
    }

    @Test
    void testSectionHistoryForAdminMessageReturnsCampusAdminAndSystem() {
        User adminUser = User.builder()
                .id(999L)
                .realName("Chirag")
                .ghostName("hirag")
                .isAdmin(true)
                .college(testCollege)
                .primarySection(testSection)
                .gender("FEMALE")
                .build();

        Message adminMsg = Message.builder()
                .id(2001L)
                .author(adminUser)
                .college(testCollege)
                .destinationSection(testSection)
                .content("Official Announcement from Admin")
                .messageType("TEXT")
                .createdAt(LocalDateTime.now())
                .build();

        when(messageRepository.findByDestinationSectionIdOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(adminMsg));
        when(messageReactionRepository.countReactionsBySectionId(10L))
                .thenReturn(Collections.emptyList());

        ResponseEntity<?> response = messageController.getSectionHistory(10L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<ChatResponsePayload> history = (List<ChatResponsePayload>) response.getBody();
        assertNotNull(history);
        assertEquals(1, history.size());
        ChatResponsePayload item = history.get(0);

        assertEquals("Campus Admin", item.getGhostName());
        assertEquals("System", item.getPrimarySection());
        assertNull(item.getGender());
        assertTrue(item.getIsAdmin());
        assertFalse(item.getGhostName().equals("hirag"));
    }

    @Test
    void testDeletePost_ByAuthor_InvalidatesSharedMessagesInSectionsAndDMs() {
        UserDetailsImpl userDetails = UserDetailsImpl.build(authorA);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userRepository.findById(authorA.getId())).thenReturn(Optional.of(authorA));
        when(userRepository.findByIdWithSectionAndCollege(authorA.getId())).thenReturn(Optional.of(authorA));

        Post post = Post.builder()
                .id(999L)
                .author(authorA)
                .content("My Graduation Post")
                .college(testCollege)
                .build();
        when(postRepository.findById(999L)).thenReturn(Optional.of(post));
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(), any()))
                .thenReturn(List.of(10L));

        ResponseEntity<?> response = feedController.deletePost(999L);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(commentRepository).deleteByPostId(999L);
        verify(commentLikeRepository).deleteByPostId(999L);
        verify(postLikeRepository).deleteByPostId(999L);
        verify(savedPostRepository).deleteByPostId(999L);
        verify(postRepository).delete(post);

        // Verify shared messages in both messages and direct_messages tables are invalidated
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> argCaptor = ArgumentCaptor.forClass(Object.class);

        verify(jdbcTemplate, atLeast(2)).update(sqlCaptor.capture(), argCaptor.capture(), any(), any());
        assertTrue(sqlCaptor.getAllValues().get(0).contains("UPDATE messages SET content = ?, message_type = 'POST_UNAVAILABLE'"));
        assertTrue(sqlCaptor.getAllValues().get(1).contains("UPDATE direct_messages SET content = ?, message_type = 'POST_UNAVAILABLE'"));

        String payload = (String) argCaptor.getAllValues().get(0);
        assertTrue(payload.startsWith("[POST_UNAVAILABLE]"));
        assertTrue(payload.contains("\"postId\":999"));
        assertTrue(payload.contains("This post is unavailable because it has been deleted."));

        // Verify WebSocket notification sent to affected section
        verify(messagingTemplate).convertAndSend(eq("/topic/section/10"), any(Object.class));
    }

    @Test
    void testDeletePost_ByUnauthorizedUser_ReturnsForbidden() {
        // Logged in as sharerB (not author)
        UserDetailsImpl userDetails = UserDetailsImpl.build(sharerB);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userRepository.findById(sharerB.getId())).thenReturn(Optional.of(sharerB));

        Post post = Post.builder()
                .id(888L)
                .author(authorA)
                .content("Alice's Secret Post")
                .college(testCollege)
                .build();
        when(postRepository.findById(888L)).thenReturn(Optional.of(post));

        ResponseEntity<?> response = feedController.deletePost(888L);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(postRepository, never()).delete(any());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}

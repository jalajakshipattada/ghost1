package com.ghostcommunity.backend;

import com.ghostcommunity.backend.controller.PublicShareController;
import com.ghostcommunity.backend.model.College;
import com.ghostcommunity.backend.model.Post;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PublicShareAndSearchTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PublicShareController publicShareController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testDownloadAppRedirectsToApk() {
        ResponseEntity<Void> response = publicShareController.downloadApp();
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertNotNull(response.getHeaders().getLocation());
        assertTrue(response.getHeaders().getLocation().toString().contains("app-release.apk"));
    }

    @Test
    void testGetSharedPostPageNotFound() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<String> response = publicShareController.getSharedPostPage(999L);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("Ghost Post Unavailable"));
    }

    @Test
    void testGetSharedPostPageWithDirectPostAndActionUrl() {
        User author = User.builder()
                .id(1L)
                .realName("Chirag Changappa")
                .ghostName("Ghost#1234")
                .profileImageUrl("https://ghost.com/profile.jpg")
                .build();

        College college = College.builder()
                .id(1L)
                .name("Canara Engineering College")
                .build();

        Post post = Post.builder()
                .id(42L)
                .author(author)
                .college(college)
                .content("[DIRECT|Chirag Changappa] Check out our new campus tech fest!\n\n🔗 https://fest.canaraengineering.in")
                .actionUrl("https://fest.canaraengineering.in")
                .imageUrl("https://ghost.com/poster.jpg")
                .build();

        when(postRepository.findById(42L)).thenReturn(Optional.of(post));

        ResponseEntity<String> response = publicShareController.getSharedPostPage(42L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String html = response.getBody();
        assertNotNull(html);

        // Check Open Graph tags
        assertTrue(html.contains("<meta property=\"og:title\" content=\"👻 Chirag Changappa (Canara Engineering College)\">"));
        assertTrue(html.contains("<meta property=\"og:image\" content=\"https://ghost.com/poster.jpg\">"));
        assertTrue(html.contains("<meta property=\"og:url\" content=\"https://ghost1-3oo4.onrender.com/share/post/42\">"));

        // Check clean content (no [DIRECT|...] leaked)
        assertFalse(html.contains("[DIRECT|Chirag Changappa]"));
        assertTrue(html.contains("Check out our new campus tech fest!"));

        // Check Action CTA Bar
        assertTrue(html.contains("Learn More"));
        assertTrue(html.contains("https://fest.canaraengineering.in"));

        // Check deep link & download button
        assertTrue(html.contains("ghost://feed?postId=42"));
        assertTrue(html.contains("/download/app"));
    }

    @Test
    void testGetSharedPostPageWithGhostAnonymousPost() {
        User author = User.builder()
                .id(2L)
                .ghostName("MysticPhantom")
                .realName("Hidden Person")
                .build();

        College college = College.builder()
                .id(1L)
                .name("MIT")
                .build();

        Post post = Post.builder()
                .id(88L)
                .author(author)
                .college(college)
                .content("Anyone study for the exam tomorrow?")
                .build();

        when(postRepository.findById(88L)).thenReturn(Optional.of(post));

        ResponseEntity<String> response = publicShareController.getSharedPostPage(88L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String html = response.getBody();
        assertNotNull(html);

        // Strictly verify real identity is NOT leaked for anonymous ghost posts
        assertFalse(html.contains("Hidden Person"));
        assertTrue(html.contains("MysticPhantom"));
        assertTrue(html.contains("Anyone study for the exam tomorrow?"));
    }
}

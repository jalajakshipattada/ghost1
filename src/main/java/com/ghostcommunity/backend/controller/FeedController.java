package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.*;
import com.ghostcommunity.backend.repository.*;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;

    @GetMapping
    public ResponseEntity<?> getFeed(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String query) {
        User user = getCurrentUser();
        Page<Post> postsPage;
        if (query != null && !query.trim().isEmpty()) {
            postsPage = postRepository.searchByCollegeAndQuery(
                user.getCollege().getId(), query.trim(), PageRequest.of(page, size));
        } else {
            postsPage = postRepository.findByCollegeIdOrderByCreatedAtDesc(
                user.getCollege().getId(), PageRequest.of(page, size));
        }

        List<Map<String, Object>> feed = postsPage.stream().map(post -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", post.getId());

            boolean isDirect = post.getContent() != null && post.getContent().startsWith("[DIRECT]");
            map.put("isDirect", isDirect);
            map.put("isDirectName", isDirect);

            if (isDirect) {
                // Direct post: show author's real identity, strictly hide dummy/ghost name
                map.put("authorRealName", post.getAuthor().getRealName());
                map.put("authorProfileImageUrl", post.getAuthor().getProfileImageUrl());
                map.put("authorId", post.getAuthor().getId());
                map.put("authorPrimarySection", post.getAuthor().getPrimarySection() != null ? post.getAuthor().getPrimarySection().getName() : "");
            } else {
                // Ghost post: show ghost name, strictly hide real name, email, and photo
                map.put("authorGhostName", post.getAuthor().getGhostName());
                map.put("authorPrimarySection", post.getAuthor().getPrimarySection() != null ? post.getAuthor().getPrimarySection().getName() : "");
            }

            map.put("gender", post.getAuthor().getGender());
            map.put("content", post.getContent());
            map.put("imageUrl", post.getImageUrl() != null ? post.getImageUrl() : "");
            map.put("createdAt", post.getCreatedAt());

            int score = postLikeRepository.getScoreByPostId(post.getId());
            var myLikeOpt = postLikeRepository.findByPostIdAndUserId(post.getId(), user.getId());
            int myVote = myLikeOpt.map(l -> l.getVoteValue() != null ? l.getVoteValue() : 1).orElse(0);

            map.put("score", score);
            map.put("myVote", myVote);
            map.put("likesCount", score);
            map.put("commentsCount", commentRepository.countByPostId(post.getId()));
            map.put("isLikedByMe", myVote == 1);
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", feed);
        response.put("totalPages", postsPage.getTotalPages());
        response.put("totalElements", postsPage.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createPost(@RequestBody Map<String, String> request) {
        User user = getCurrentUser();
        String content = request.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Content cannot be empty");
        }
        String imageUrl = request.get("imageUrl");
        Post post = Post.builder()
                .author(user)
                .college(user.getCollege())
                .content(content)
                .imageUrl(imageUrl != null && !imageUrl.trim().isEmpty() ? imageUrl : null)
                .build();
        postRepository.save(post);
        return ResponseEntity.ok("Post created successfully");
    }

    @PostMapping("/{postId}/vote")
    public ResponseEntity<?> votePost(@PathVariable Long postId, @RequestParam int value) {
        User user = getCurrentUser();
        Post post = postRepository.findById(postId).orElseThrow();

        var existingOpt = postLikeRepository.findByPostIdAndUserId(postId, user.getId());
        if (existingOpt.isPresent()) {
            PostLike existing = existingOpt.get();
            int currentVal = existing.getVoteValue() != null ? existing.getVoteValue() : 1;
            if (currentVal == value || value == 0) {
                postLikeRepository.delete(existing);
            } else {
                existing.setVoteValue(value);
                postLikeRepository.save(existing);
            }
        } else {
            if (value != 0) {
                postLikeRepository.save(PostLike.builder()
                        .post(post)
                        .user(user)
                        .voteValue(value)
                        .build());
            }
        }

        int newScore = postLikeRepository.getScoreByPostId(postId);
        var updatedOpt = postLikeRepository.findByPostIdAndUserId(postId, user.getId());
        int updatedVote = updatedOpt.map(l -> l.getVoteValue() != null ? l.getVoteValue() : 1).orElse(0);

        Map<String, Object> res = new HashMap<>();
        res.put("score", newScore);
        res.put("myVote", updatedVote);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<?> togglePostLike(@PathVariable Long postId) {
        return votePost(postId, 1);
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<?> getComments(@PathVariable Long postId) {
        User user = getCurrentUser();
        List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        List<Map<String, Object>> res = comments.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("authorGhostName", c.getAuthor().getGhostName());
            map.put("authorPrimarySection", c.getAuthor().getPrimarySection() != null ? c.getAuthor().getPrimarySection().getName() : "");
            map.put("gender", c.getAuthor().getGender());
            map.put("content", c.getContent());
            map.put("createdAt", c.getCreatedAt());
            map.put("likesCount", commentLikeRepository.countByCommentId(c.getId()));
            map.put("isLikedByMe", commentLikeRepository.existsByCommentIdAndUserId(c.getId(), user.getId()));
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long postId, @RequestBody Map<String, String> request) {
        User user = getCurrentUser();
        Post post = postRepository.findById(postId).orElseThrow();
        String content = request.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Content cannot be empty");
        }
        Comment comment = Comment.builder()
                .post(post)
                .author(user)
                .content(content)
                .build();
        commentRepository.save(comment);
        return ResponseEntity.ok("Comment added successfully");
    }

    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<?> toggleCommentLike(@PathVariable Long commentId) {
        User user = getCurrentUser();
        Comment comment = commentRepository.findById(commentId).orElseThrow();

        commentLikeRepository.findByCommentIdAndUserId(commentId, user.getId()).ifPresentOrElse(
                commentLikeRepository::delete,
                () -> commentLikeRepository.save(CommentLike.builder().comment(comment).user(user).build())
        );

        return ResponseEntity.ok("Comment like toggled");
    }

    @PostMapping("/{postId}/share")
    public ResponseEntity<?> sharePost(@PathVariable Long postId, @RequestBody Map<String, Long> request) {
        User user = getCurrentUser();
        Post post = postRepository.findById(postId).orElseThrow();
        Long sectionId = request.get("sectionId");
        
        if (sectionId == null) {
            return ResponseEntity.badRequest().body("Section ID is required");
        }
        
        Section section = sectionRepository.findById(sectionId).orElseThrow();
        
        // Ensure section is in same college
        if (!section.getCollege().getId().equals(user.getCollege().getId())) {
            return ResponseEntity.badRequest().body("Security Violation");
        }

        // Create a chat message referencing the post (Instagram-style rich share card)
        boolean isDirect = post.getContent() != null && post.getContent().startsWith("[DIRECT]");
        String authorName = isDirect
                ? (post.getAuthor().getRealName() != null ? post.getAuthor().getRealName() : post.getAuthor().getGhostName())
                : post.getAuthor().getGhostName();
        String imageUrl = post.getImageUrl() != null ? post.getImageUrl() : "";
        String postText = post.getContent() != null ? post.getContent().replace("[DIRECT]", "").trim() : "";
        String authorPhoto = isDirect && post.getAuthor().getProfileImageUrl() != null ? post.getAuthor().getProfileImageUrl() : "";

        String escapedAuthor = authorName.replace("\"", "\\\"");
        String escapedPhoto = authorPhoto.replace("\"", "\\\"");
        String escapedImage = imageUrl.replace("\"", "\\\"");
        String escapedText = postText.replace("\"", "\\\"").replace("\n", " ");

        String shareContent = "[POST_SHARE]{\"postId\":" + post.getId() +
                ",\"authorName\":\"" + escapedAuthor + "\"" +
                ",\"authorPhoto\":\"" + escapedPhoto + "\"" +
                ",\"isDirect\":" + isDirect +
                ",\"imageUrl\":\"" + escapedImage + "\"" +
                ",\"caption\":\"" + escapedText + "\"}";

        Message message = Message.builder()
                .author(user)
                .college(user.getCollege())
                .destinationSection(section)
                .content(shareContent)
                .messageType("POST_SHARE")
                .build();
                
        message = messageRepository.save(message);

        // Broadcast to WebSocket
        com.ghostcommunity.backend.payload.ChatResponsePayload response = com.ghostcommunity.backend.payload.ChatResponsePayload.builder()
                .messageId(message.getId())
                .senderId(user.getId())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .ghostName(user.getGhostName())
                .primarySection(user.getPrimarySection() != null ? user.getPrimarySection().getName() : " ")
                .gender(user.getGender())
                .createdAt(message.getCreatedAt())
                .build();

        messagingTemplate.convertAndSend("/topic/section/" + sectionId, response);
        
        return ResponseEntity.ok("Shared successfully");
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        return userRepository.findByIdWithSectionAndCollege(userDetails.getId()).orElseThrow();
    }
}

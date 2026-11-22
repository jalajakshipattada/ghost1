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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final SavedPostRepository savedPostRepository;
    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;
    private final com.ghostcommunity.backend.repository.SectionReadRepository sectionReadRepository;

    private Map<String, Object> formatPostMap(Post post, User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", post.getId());

        boolean isDirect = post.getContent() != null && post.getContent().startsWith("[DIRECT");
        map.put("isDirect", isDirect);
        map.put("isDirectName", isDirect);

        if (isDirect) {
            // Direct post: show author's real identity, strictly hide dummy/ghost name
            String realName = post.getAuthor().getRealName();
            if ((realName == null || realName.trim().isEmpty()) && post.getContent() != null && post.getContent().contains("[DIRECT|")) {
                int start = post.getContent().indexOf("[DIRECT|") + 8;
                int end = post.getContent().indexOf("]", start);
                if (end != -1) {
                    realName = post.getContent().substring(start, end).trim();
                }
            }
            map.put("authorRealName", realName != null && !realName.isEmpty() ? realName : "Student");
            map.put("authorProfileImageUrl", post.getAuthor().getProfileImageUrl());
            map.put("authorId", post.getAuthor().getId());
            map.put("authorPrimarySection", post.getAuthor().getPrimarySection() != null ? post.getAuthor().getPrimarySection().getName() : "");
        } else {
            // Ghost post: show ghost name, strictly hide real name, email, and photo
            map.put("authorGhostName", post.getAuthor().getGhostName());
            map.put("authorPrimarySection", post.getAuthor().getPrimarySection() != null ? post.getAuthor().getPrimarySection().getName() : "");
        }

        String colName = post.getCollege() != null ? post.getCollege().getName() : (post.getAuthor().getCollege() != null ? post.getAuthor().getCollege().getName() : "");
        map.put("authorCollegeName", colName);
        map.put("collegeName", colName);

        map.put("gender", post.getAuthor().getGender());
        map.put("content", post.getContent());
        map.put("imageUrl", post.getImageUrl() != null ? post.getImageUrl() : "");
        map.put("videoUrl", post.getVideoUrl() != null ? post.getVideoUrl() : "");
        String mediaType = post.getMediaType();
        if (mediaType == null || mediaType.isBlank()) {
            if (post.getVideoUrl() != null && !post.getVideoUrl().isBlank()) {
                mediaType = "VIDEO";
            } else if (post.getImageUrl() != null && !post.getImageUrl().isBlank()) {
                mediaType = "IMAGE";
            } else {
                mediaType = "NONE";
            }
        }
        map.put("mediaType", mediaType);
        map.put("isPinned", Boolean.TRUE.equals(post.getIsPinned()));
        boolean isAdminPost = Boolean.TRUE.equals(post.getIsPinned())
                || (post.getAuthor() != null && post.getAuthor().isAdmin())
                || (post.getContent() != null && post.getContent().startsWith("[OFFICIAL ADMIN]"));
        map.put("isAdminPost", isAdminPost);
        map.put("createdAt", post.getCreatedAt());

        int score = postLikeRepository.getScoreByPostId(post.getId());
        var myLikeOpt = postLikeRepository.findByPostIdAndUserId(post.getId(), user.getId());
        int myVote = myLikeOpt.map(l -> l.getVoteValue() != null ? l.getVoteValue() : 1).orElse(0);

        map.put("score", score);
        map.put("myVote", myVote);
        map.put("likesCount", score);
        map.put("commentsCount", commentRepository.countByPostId(post.getId()));
        map.put("sharesCount", post.getSharesCount() != null ? post.getSharesCount() : 0);
        map.put("isLikedByMe", myVote == 1);
        map.put("isSavedByMe", savedPostRepository.existsByUserIdAndPostId(user.getId(), post.getId()));
        return map;
    }

    public static double calculateInstagramScore(Post post, Long viewerCollegeId, int likes, long commentsCount) {
        int shares = post.getSharesCount() != null ? post.getSharesCount() : 0;
        boolean hasMedia = (post.getImageUrl() != null && !post.getImageUrl().trim().isEmpty())
                || (post.getVideoUrl() != null && !post.getVideoUrl().trim().isEmpty());

        double baseWeight = (likes * 2.5) + (commentsCount * 4.0) + (shares * 5.0) + (hasMedia ? 10.0 : 0.0) + 1.0;

        // Equal for all colleges - no campus bias
        double affinity = 1.0;

        double hoursElapsed = 0.0;
        if (post.getCreatedAt() != null) {
            hoursElapsed = Math.max(0.0, java.time.Duration.between(post.getCreatedAt(), java.time.LocalDateTime.now()).toMinutes() / 60.0);
        }

        double decay = Math.pow(hoursElapsed + 2.0, 1.4);

        return (baseWeight * affinity) / decay;
    }

    @GetMapping
    public ResponseEntity<?> getFeed(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String query,
                                     @RequestParam(defaultValue = "true") boolean allColleges) {
        User user = getCurrentUser();
        boolean queryPresent = query != null && !query.trim().isEmpty();

        if (queryPresent) {
            Page<Post> postsPage;
            if (allColleges || user.getCollege() == null) {
                postsPage = postRepository.searchAllByQuery(query.trim(), PageRequest.of(page, size));
            } else {
                postsPage = postRepository.searchByCollegeAndQuery(
                    user.getCollege().getId(), query.trim(), PageRequest.of(page, size));
            }
            List<Map<String, Object>> feed = postsPage.stream()
                    .map(post -> formatPostMap(post, user))
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("content", feed);
            response.put("totalPages", postsPage.getTotalPages());
            response.put("totalElements", postsPage.getTotalElements());
            return ResponseEntity.ok(response);
        }

        // 1. Fetch pinned admin announcements (Always show at TOP of feed)
        List<Post> pinnedPosts = postRepository.findByIsPinnedTrueOrderByCreatedAtDesc();

        // 2. Fetch candidate feed posts
        List<Post> candidatePosts;
        if (allColleges || user.getCollege() == null) {
            candidatePosts = postRepository.findTop200ByOrderByCreatedAtDesc();
            if (candidatePosts.isEmpty()) {
                candidatePosts = postRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)).getContent();
            }
        } else {
            candidatePosts = postRepository.findTop200ByCollegeIdOrderByCreatedAtDesc(user.getCollege().getId());
            if (candidatePosts.isEmpty()) {
                candidatePosts = postRepository.findByCollegeIdOrderByCreatedAtDesc(
                        user.getCollege().getId(), PageRequest.of(0, 50)).getContent();
            }
        }

        // Filter out any already-pinned posts from algorithmic list
        Set<Long> pinnedIds = pinnedPosts.stream().map(Post::getId).collect(Collectors.toSet());
        List<Post> unpinnedCandidates = candidatePosts.stream()
                .filter(p -> !pinnedIds.contains(p.getId()))
                .collect(Collectors.toList());

        List<Long> postIds = unpinnedCandidates.stream().map(Post::getId).collect(Collectors.toList());
        Map<Long, Long> commentCountMap = new HashMap<>();
        Map<Long, Integer> likeScoreMap = new HashMap<>();
        if (!postIds.isEmpty()) {
            try {
                List<Object[]> commentCounts = commentRepository.countCommentsByPostIds(postIds);
                for (Object[] row : commentCounts) {
                    commentCountMap.put((Long) row[0], ((Number) row[1]).longValue());
                }
            } catch (Exception ignored) {}
            try {
                List<Object[]> likeScores = postLikeRepository.getScoresByPostIds(postIds);
                for (Object[] row : likeScores) {
                    likeScoreMap.put((Long) row[0], ((Number) row[1]).intValue());
                }
            } catch (Exception ignored) {}
        }

        Long viewerCollegeId = (user.getCollege() != null) ? user.getCollege().getId() : null;
        List<Post> sortedPosts = unpinnedCandidates.stream()
                .sorted((p1, p2) -> {
                    double s1 = calculateInstagramScore(p1, viewerCollegeId, likeScoreMap.getOrDefault(p1.getId(), 0), commentCountMap.getOrDefault(p1.getId(), 0L));
                    double s2 = calculateInstagramScore(p2, viewerCollegeId, likeScoreMap.getOrDefault(p2.getId(), 0), commentCountMap.getOrDefault(p2.getId(), 0L));
                    return Double.compare(s2, s1);
                })
                .collect(Collectors.toList());

        // Combine: Pinned Admin Announcements first, followed by Algorithmic Feed
        List<Post> allRanked = new ArrayList<>();
        allRanked.addAll(pinnedPosts);
        allRanked.addAll(sortedPosts);

        int fromIndex = Math.min(page * size, allRanked.size());
        int toIndex = Math.min(fromIndex + size, allRanked.size());
        List<Post> pagedPosts = allRanked.subList(fromIndex, toIndex);

        List<Map<String, Object>> feed = pagedPosts.stream()
                .map(post -> formatPostMap(post, user))
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) allRanked.size() / Math.max(1, size));
        Map<String, Object> response = new HashMap<>();
        response.put("content", feed);
        response.put("totalPages", totalPages);
        response.put("totalElements", allRanked.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-posts")
    public ResponseEntity<?> getMyPosts(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        User user = getCurrentUser();
        Page<Post> postsPage = postRepository.findByAuthorIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(page, size));
        List<Map<String, Object>> feed = postsPage.stream()
                .map(post -> formatPostMap(post, user))
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", feed);
        response.put("totalPages", postsPage.getTotalPages());
        response.put("totalElements", postsPage.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/saved")
    public ResponseEntity<?> getSavedPosts(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        User user = getCurrentUser();
        Page<SavedPost> savedPage = savedPostRepository.findByUserIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(page, size));
        List<Map<String, Object>> feed = savedPage.stream()
                .map(saved -> formatPostMap(saved.getPost(), user))
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", feed);
        response.put("totalPages", savedPage.getTotalPages());
        response.put("totalElements", savedPage.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{postId}/save")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> toggleSavePost(@PathVariable Long postId) {
        User user = getCurrentUser();
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return ResponseEntity.notFound().build();
        }
        var existingOpt = savedPostRepository.findByUserIdAndPostId(user.getId(), postId);
        if (existingOpt.isPresent()) {
            savedPostRepository.delete(existingOpt.get());
            return ResponseEntity.ok(Map.of("saved", false, "message", "Post removed from saved"));
        } else {
            SavedPost savedPost = SavedPost.builder()
                    .user(user)
                    .post(post)
                    .build();
            savedPostRepository.save(savedPost);
            return ResponseEntity.ok(Map.of("saved", true, "message", "Post saved successfully"));
        }
    }

    @DeleteMapping("/{postId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deletePost(@PathVariable Long postId) {
        User user = getCurrentUser();
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return ResponseEntity.notFound().build();
        }
        if (!post.getAuthor().getId().equals(user.getId())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body("You are not authorized to delete this post");
        }
        commentLikeRepository.deleteByPostId(postId);
        commentRepository.deleteByPostId(postId);
        postLikeRepository.deleteByPostId(postId);
        savedPostRepository.deleteByPostId(postId);
        postRepository.delete(post);
        return ResponseEntity.ok(Map.of("message", "Post deleted successfully", "deletedPostId", postId));
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

            boolean isDirect = c.getContent() != null && c.getContent().startsWith("[DIRECT");
            map.put("isDirect", isDirect);
            if (isDirect) {
                map.put("authorRealName", c.getAuthor().getRealName());
                map.put("authorProfileImageUrl", c.getAuthor().getProfileImageUrl());
                map.put("authorId", c.getAuthor().getId());
            } else {
                map.put("authorGhostName", c.getAuthor().getGhostName());
            }

            Long parentId = null;
            if (c.getParentComment() != null) {
                parentId = c.getParentComment().getId();
            } else if (c.getContent() != null && c.getContent().contains("[REPLY:")) {
                try {
                    int start = c.getContent().indexOf("[REPLY:") + 7;
                    int end = c.getContent().indexOf("]", start);
                    if (end != -1) {
                        parentId = Long.parseLong(c.getContent().substring(start, end).trim());
                    }
                } catch (Exception ignored) {}
            }
            map.put("parentId", parentId);

            map.put("authorPrimarySection", c.getAuthor().getPrimarySection() != null ? c.getAuthor().getPrimarySection().getName() : "");
            map.put("gender", c.getAuthor().getGender());
            map.put("content", c.getContent());
            map.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() + "Z" : null);
            map.put("likesCount", commentLikeRepository.countByCommentId(c.getId()));
            map.put("isLikedByMe", commentLikeRepository.existsByCommentIdAndUserId(c.getId(), user.getId()));
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long postId, @RequestBody Map<String, Object> request) {
        User user = getCurrentUser();
        Post post = postRepository.findById(postId).orElseThrow();
        String content = request.get("content") != null ? request.get("content").toString() : null;
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Content cannot be empty");
        }

        Comment parentComment = null;
        Object parentIdObj = request.get("parentId");
        if (parentIdObj != null) {
            try {
                Long pId = Long.parseLong(parentIdObj.toString());
                parentComment = commentRepository.findById(pId).orElse(null);
            } catch (Exception ignored) {}
        } else if (content.contains("[REPLY:")) {
            try {
                int start = content.indexOf("[REPLY:") + 7;
                int end = content.indexOf("]", start);
                if (end != -1) {
                    Long pId = Long.parseLong(content.substring(start, end).trim());
                    parentComment = commentRepository.findById(pId).orElse(null);
                }
            } catch (Exception ignored) {}
        }

        Comment comment = Comment.builder()
                .post(post)
                .author(user)
                .content(content)
                .parentComment(parentComment)
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
    public ResponseEntity<?> sharePost(@PathVariable Long postId, @RequestBody Map<String, Object> request) {
        User user = getCurrentUser();
        Post post = postRepository.findById(postId).orElseThrow();
        Object rawSectionId = request.get("sectionId");
        if (rawSectionId == null) {
            return ResponseEntity.badRequest().body("Section ID is required");
        }
        Long sectionId = (rawSectionId instanceof Number)
                ? ((Number) rawSectionId).longValue()
                : Long.parseLong(rawSectionId.toString());
        
        Section section = sectionRepository.findById(sectionId).orElseThrow();
        
        // Ensure section is in same college
        if (!section.getCollege().getId().equals(user.getCollege().getId())) {
            return ResponseEntity.badRequest().body("Security Violation");
        }

        // Create a chat message referencing the post (Instagram-style rich share card)
        boolean isDirect = post.getContent() != null && post.getContent().startsWith("[DIRECT");
        String authorName = isDirect
                ? (post.getAuthor().getRealName() != null ? post.getAuthor().getRealName() : post.getAuthor().getGhostName())
                : post.getAuthor().getGhostName();
        String imageUrl = post.getImageUrl() != null ? post.getImageUrl() : "";
        String postText = post.getContent() != null ? post.getContent().replaceAll("\\[DIRECT[^\\]]*\\]", "").trim() : "";
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

        // Advance sender's read count so sender never sees own share as unread
        try {
            Long messageId = message.getId();
            long newTotal = messageRepository.countByDestinationSectionId(section.getId());
            var readOpt = sectionReadRepository.findByUserIdAndSectionId(user.getId(), section.getId());
            var sr = readOpt.orElseGet(() -> com.ghostcommunity.backend.model.SectionRead.builder().user(user).section(section).build());
            sr.setLastReadMessageId(messageId);
            sr.setLastReadCount(newTotal);
            sectionReadRepository.save(sr);
        } catch (Exception ignored) {}

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
        
        // Increment post share count
        try {
            int curShares = post.getSharesCount() != null ? post.getSharesCount() : 0;
            post.setSharesCount(curShares + 1);
            postRepository.save(post);
        } catch (Exception ignored) {}

        return ResponseEntity.ok("Shared successfully");
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> getSinglePost(@PathVariable Long postId) {
        User user = getCurrentUser();
        Post post = postRepository.findById(postId).orElse(null);
        if (post != null) {
            return ResponseEntity.ok(formatPostMap(post, user));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{postId}/track-share")
    public ResponseEntity<?> trackExternalShare(@PathVariable Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post != null) {
            int curShares = post.getSharesCount() != null ? post.getSharesCount() : 0;
            post.setSharesCount(curShares + 1);
            postRepository.save(post);
            return ResponseEntity.ok(Map.of("sharesCount", curShares + 1));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/universal-search")
    public ResponseEntity<?> universalSearch(@RequestParam(required = false, defaultValue = "") String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "posts", List.of(),
                    "users", List.of(),
                    "sections", List.of()
            ));
        }

        User user = getCurrentUser();
        Long collegeId = user.getCollege() != null ? user.getCollege().getId() : null;
        String q = query.trim();

        // 1. Search posts across all colleges for discovery
        Page<Post> postsPage = postRepository.searchAllByQuery(q, PageRequest.of(0, 30));
        List<Map<String, Object>> posts = postsPage.stream()
                .map(p -> formatPostMap(p, user))
                .collect(Collectors.toList());

        // 2. Search cross-college real users (strictly by realName across ANY college, Instagram-style)
        List<User> usersList = userRepository.searchAllByRealName(q);
        List<Map<String, Object>> users = usersList.stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("realName", u.getRealName() != null ? u.getRealName() : "Student");
                    m.put("collegeName", u.getCollege() != null ? u.getCollege().getName() : "Campus Member");
                    m.put("collegeId", u.getCollege() != null ? u.getCollege().getId() : null);
                    m.put("profileImageUrl", u.getProfileImageUrl() != null ? u.getProfileImageUrl() : "");
                    m.put("sectionName", u.getPrimarySection() != null ? u.getPrimarySection().getName() : "");
                    m.put("gender", u.getGender() != null ? u.getGender() : "");
                    return m;
                })
                .collect(Collectors.toList());

        // 3. Search college sections
        List<Section> sectionsList = (collegeId != null)
                ? sectionRepository.findByCollegeIdAndNameContainingIgnoreCase(collegeId, q)
                : List.of();
        List<Map<String, Object>> sections = sectionsList.stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    m.put("description", s.getMessageCount() + " messages");
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "posts", posts,
                "users", users,
                "sections", sections
        ));
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        return userRepository.findByIdWithSectionAndCollege(userDetails.getId()).orElseThrow();
    }
}

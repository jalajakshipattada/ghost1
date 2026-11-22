package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.Post;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class PublicShareController {

    private final PostRepository postRepository;

    private static final String APK_DOWNLOAD_URL = "https://github.com/chiragchangappa/ghost/raw/main/app-release.apk";

    @GetMapping("/download/app")
    public ResponseEntity<Void> downloadApp() {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(APK_DOWNLOAD_URL));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping(value = "/share/post/{postId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSharedPostPage(@PathVariable Long postId) {
        Post post = postRepository.findById(postId).orElse(null);

        if (post == null) {
            String notFoundHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">" +
                    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                    "<title>Post Not Found - Ghost</title>" +
                    "<style>" +
                    "body { background: #0A0818; color: #fff; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; text-align: center; }" +
                    ".card { background: #131026; padding: 40px; border-radius: 20px; border: 1px solid rgba(255,255,255,0.1); max-width: 360px; box-shadow: 0 10px 40px rgba(0,0,0,0.6); }" +
                    "h2 { margin: 0 0 10px; color: #00F0FF; font-size: 22px; }" +
                    "p { color: rgba(255,255,255,0.7); font-size: 14px; line-height: 1.5; margin-bottom: 24px; }" +
                    ".btn { display: inline-block; background: #00F0FF; color: #000; padding: 12px 24px; border-radius: 12px; font-weight: 700; text-decoration: none; font-size: 14px; }" +
                    "</style></head><body>" +
                    "<div class=\"card\">" +
                    "<h2>👻 Ghost Post Unavailable</h2>" +
                    "<p>This campus broadcast or post may have expired or was removed by its author.</p>" +
                    "<a class=\"btn\" href=\"/download/app\">Download Ghost App</a>" +
                    "</div></body></html>";
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFoundHtml);
        }

        // 1. Author Resolution
        boolean isDirect = post.getContent() != null && post.getContent().startsWith("[DIRECT");
        User author = post.getAuthor();
        String authorName = "Student";
        String authorPhoto = "";
        if (isDirect) {
            if (author != null && author.getRealName() != null && !author.getRealName().isBlank()) {
                authorName = author.getRealName();
            } else if (post.getContent() != null && post.getContent().contains("[DIRECT|")) {
                int start = post.getContent().indexOf("[DIRECT|") + 8;
                int end = post.getContent().indexOf("]", start);
                if (end != -1) {
                    authorName = post.getContent().substring(start, end).trim();
                }
            }
            if (author != null && author.getProfileImageUrl() != null) {
                authorPhoto = author.getProfileImageUrl();
            }
        } else {
            authorName = (author != null && author.getGhostName() != null)
                    ? author.getGhostName()
                    : (author != null && author.isAdmin() ? "⚠️ Campus Admin" : "Ghost Member");
        }

        // College Name
        String collegeName = "";
        if (post.getCollege() != null && post.getCollege().getName() != null) {
            collegeName = post.getCollege().getName();
        } else if (author != null && author.getCollege() != null && author.getCollege().getName() != null) {
            collegeName = author.getCollege().getName();
        } else {
            collegeName = "Campus Network";
        }

        // Clean caption
        String rawContent = post.getContent() != null ? post.getContent() : "";
        String cleanContent = rawContent
                .replaceAll("\\[DIRECT[^\\]]*\\]", "")
                .replaceAll("\\[DIRECT\\]", "")
                .replaceAll("\\[GHOST\\]", "")
                .replaceAll("\\[OFFICIAL ADMIN\\]", "")
                .trim();

        // Extract action url if present
        String actionUrl = post.getActionUrl() != null ? post.getActionUrl() : "";
        if (actionUrl.isBlank() && rawContent.contains("🔗 ")) {
            int idx = rawContent.indexOf("🔗 ");
            actionUrl = rawContent.substring(idx + 2).trim();
            if (actionUrl.contains("\n")) {
                actionUrl = actionUrl.substring(0, actionUrl.indexOf("\n")).trim();
            }
        }

        // Media Resolution
        String imageUrl = post.getImageUrl() != null ? post.getImageUrl().trim() : "";
        String videoUrl = post.getVideoUrl() != null ? post.getVideoUrl().trim() : "";
        boolean hasVideo = !videoUrl.isEmpty();
        boolean hasImage = !hasVideo && !imageUrl.isEmpty();

        String ogTitle = escapeHtml("👻 " + authorName + " (" + collegeName + ")");
        String ogDescription = escapeHtml(cleanContent.isEmpty() ? "Check out this post on Ghost Campus Community." : cleanContent);
        String escapedAuthor = escapeHtml(authorName);
        String escapedCollege = escapeHtml(collegeName);
        String escapedContent = escapeHtml(cleanContent);
        String appDeepLink = "ghost://feed?postId=" + postId;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>").append(ogTitle).append("</title>");

        // Open Graph Meta Tags (for WhatsApp, Telegram, Instagram, Twitter)
        sb.append("<meta property=\"og:site_name\" content=\"Ghost Campus\">");
        sb.append("<meta property=\"og:title\" content=\"").append(ogTitle).append("\">");
        sb.append("<meta property=\"og:description\" content=\"").append(ogDescription).append("\">");
        sb.append("<meta property=\"og:type\" content=\"").append(hasVideo ? "video.other" : "article").append("\">");
        sb.append("<meta property=\"og:url\" content=\"https://ghost1-3oo4.onrender.com/share/post/").append(postId).append("\">");
        if (hasImage) {
            sb.append("<meta property=\"og:image\" content=\"").append(escapeHtml(imageUrl)).append("\">");
            sb.append("<meta name=\"twitter:card\" content=\"summary_large_image\">");
            sb.append("<meta name=\"twitter:image\" content=\"").append(escapeHtml(imageUrl)).append("\">");
        } else if (hasVideo) {
            sb.append("<meta property=\"og:video\" content=\"").append(escapeHtml(videoUrl)).append("\">");
            sb.append("<meta name=\"twitter:card\" content=\"player\">");
        } else {
            sb.append("<meta name=\"twitter:card\" content=\"summary\">");
        }

        // Instagram-style clean CSS
        sb.append("<style>");
        sb.append("* { box-sizing: border-box; margin: 0; padding: 0; }");
        sb.append("body { background: #070510; color: #FFFFFF; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; display: flex; flex-direction: column; align-items: center; min-height: 100vh; padding: 20px 14px 40px; }");
        sb.append(".top-nav { width: 100%; max-width: 480px; display: flex; justify-content: space-between; align-items: center; padding: 12px 6px 20px; }");
        sb.append(".brand { font-size: 22px; font-weight: 900; letter-spacing: 2px; color: #FFFFFF; display: flex; align-items: center; gap: 8px; }");
        sb.append(".brand span { color: #00F0FF; font-size: 24px; }");
        sb.append(".card { width: 100%; max-width: 480px; background: #130E26; border-radius: 20px; overflow: hidden; border: 1px solid rgba(255,255,255,0.08); box-shadow: 0 16px 50px rgba(0,0,0,0.7); }");
        sb.append(".card-header { display: flex; align-items: center; gap: 12px; padding: 16px; border-bottom: 1px solid rgba(255,255,255,0.05); }");
        sb.append(".avatar { width: 44px; height: 44px; border-radius: 50%; background: #2A204C; display: flex; align-items: center; justify-content: center; font-weight: bold; color: #00F0FF; overflow: hidden; flex-shrink: 0; }");
        sb.append(".avatar img { width: 100%; height: 100%; object-fit: cover; }");
        sb.append(".user-meta h3 { font-size: 15px; font-weight: 700; color: #FFFFFF; }");
        sb.append(".user-meta p { font-size: 12px; color: rgba(255,255,255,0.6); margin-top: 2px; }");
        sb.append(".media-wrap { width: 100%; background: #000; min-height: 240px; max-height: 520px; display: flex; align-items: center; justify-content: center; overflow: hidden; }");
        sb.append(".media-wrap img, .media-wrap video { width: 100%; max-height: 520px; object-fit: contain; }");
        sb.append(".cta-bar { background: #181333; padding: 12px 18px; display: flex; justify-content: space-between; align-items: center; color: #00F0FF; font-weight: 700; font-size: 13px; text-decoration: none; border-top: 1px solid rgba(255,255,255,0.06); }");
        sb.append(".card-body { padding: 16px 18px; }");
        sb.append(".caption { font-size: 14.5px; line-height: 1.5; color: rgba(255,255,255,0.9); word-wrap: break-word; white-space: pre-wrap; }");
        sb.append(".author-tag { font-weight: 700; color: #FFFFFF; margin-right: 6px; }");
        sb.append(".actions { margin-top: 24px; width: 100%; max-width: 480px; display: flex; flex-direction: column; gap: 12px; }");
        sb.append(".btn-primary { background: linear-gradient(135deg, #00F0FF 0%, #7000FF 100%); color: #000; font-weight: 800; font-size: 15px; padding: 15px; border-radius: 14px; text-align: center; text-decoration: none; display: block; box-shadow: 0 4px 20px rgba(0,240,255,0.3); transition: transform 0.15s ease; }");
        sb.append(".btn-primary:active { transform: scale(0.98); }");
        sb.append(".btn-secondary { background: #1C1738; color: #FFFFFF; font-weight: 700; font-size: 14px; padding: 13px; border-radius: 14px; text-align: center; text-decoration: none; display: block; border: 1px solid rgba(255,255,255,0.12); }");
        sb.append("</style>");

        // Auto-launch deep link script
        sb.append("<script>");
        sb.append("window.onload = function() {");
        sb.append("  var deepLink = '").append(appDeepLink).append("';");
        sb.append("  setTimeout(function() { window.location.href = deepLink; }, 250);");
        sb.append("};");
        sb.append("</script>");

        sb.append("</head><body>");

        // Top Navigation
        sb.append("<div class=\"top-nav\">");
        sb.append("<div class=\"brand\"><span>👻</span> GHOST</div>");
        sb.append("</div>");

        // Post Card
        sb.append("<div class=\"card\">");
        sb.append("<div class=\"card-header\">");
        sb.append("<div class=\"avatar\">");
        if (!authorPhoto.isBlank()) {
            sb.append("<img src=\"").append(escapeHtml(authorPhoto)).append("\" alt=\"").append(escapedAuthor).append("\">");
        } else {
            sb.append(escapedAuthor.substring(0, Math.min(1, escapedAuthor.length())).toUpperCase());
        }
        sb.append("</div>");
        sb.append("<div class=\"user-meta\">");
        sb.append("<h3>").append(escapedAuthor).append("</h3>");
        sb.append("<p>").append(escapedCollege).append("</p>");
        sb.append("</div></div>");

        // Media content
        if (hasVideo) {
            sb.append("<div class=\"media-wrap\">");
            sb.append("<video src=\"").append(escapeHtml(videoUrl)).append("\" autoplay loop muted playsinline controls></video>");
            sb.append("</div>");
        } else if (hasImage) {
            sb.append("<div class=\"media-wrap\">");
            sb.append("<img src=\"").append(escapeHtml(imageUrl)).append("\" alt=\"Post Media\">");
            sb.append("</div>");
        }

        // Call-to-action Learn More bar if actionUrl is present
        if (!actionUrl.isBlank()) {
            sb.append("<a class=\"cta-bar\" href=\"").append(escapeHtml(actionUrl)).append("\" target=\"_blank\" rel=\"noopener noreferrer\">");
            sb.append("<span>Learn More</span><span>&rarr;</span></a>");
        }

        // Caption body
        if (!cleanContent.isEmpty()) {
            sb.append("<div class=\"card-body\">");
            sb.append("<div class=\"caption\"><span class=\"author-tag\">").append(escapedAuthor).append("</span>").append(escapedContent).append("</div>");
            sb.append("</div>");
        }

        sb.append("</div>"); // Close card

        // Bottom Action Buttons
        sb.append("<div class=\"actions\">");
        sb.append("<a class=\"btn-primary\" href=\"").append(appDeepLink).append("\">Open in Ghost App</a>");
        sb.append("<a class=\"btn-secondary\" href=\"/download/app\">Download Ghost App (APK)</a>");
        sb.append("</div>");

        sb.append("</body></html>");

        return ResponseEntity.ok(sb.toString());
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

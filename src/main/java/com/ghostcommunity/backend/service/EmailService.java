package com.ghostcommunity.backend.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:chiragchangappa02@gmail.com}")
    private String fromEmail;

    @Value("${sendgrid.api.key:${SENDGRID_API_KEY:}}")
    private String sendGridApiKey;

    /**
     * Send Welcome Greeting Email with full Terms & Conditions upon registration
     */
    public void sendWelcomeRegistrationEmail(String toEmail, String realName) {
        new Thread(() -> {
            try {
                String greetingName = (realName != null && !realName.isBlank()) ? realName : "Student";
                String subject = "Welcome to Ghost Community 👻 - Campus Anonymous Network";

                String htmlContent = "<div style=\"font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0c0b18; color: #ffffff; padding: 32px; border-radius: 16px; max-width: 600px; margin: auto;\">"
                        + "<div style=\"text-align: center; margin-bottom: 24px;\">"
                        + "<h1 style=\"color: #a855f7; margin: 0; font-size: 28px;\">👻 Ghost Community</h1>"
                        + "<p style=\"color: #9ca3af; font-size: 14px; margin-top: 6px;\">Your Verified & Anonymous College Space</p>"
                        + "</div>"
                        + "<p style=\"font-size: 16px; line-height: 1.6; color: #e5e7eb;\">Hi <strong>" + greetingName + "</strong>,</p>"
                        + "<p style=\"font-size: 15px; line-height: 1.6; color: #d1d5db;\">Welcome to <strong>Ghost Community</strong>! Your account has been created successfully. You can now connect with fellow students, discuss branch matters, share opinions in the campus feed, and participate in live section chats with full anonymity.</p>"
                        + "<div style=\"background-color: #151426; border: 1px solid #2e2a4a; border-radius: 12px; padding: 20px; margin: 24px 0;\">"
                        + "<h3 style=\"color: #c084fc; margin-top: 0; margin-bottom: 12px; font-size: 16px;\">📜 Community Guidelines & Terms of Service</h3>"
                        + "<ul style=\"padding-left: 20px; font-size: 13px; color: #9ca3af; line-height: 1.8;\">"
                        + "<li><strong style=\"color: #ffffff;\">1. Ghost Anonymity:</strong> Real names are completely private. Never attempt to dox, uncover, or expose another student.</li>"
                        + "<li><strong style=\"color: #ffffff;\">2. Zero Tolerance for Bullying:</strong> Cyberbullying, defamation, hate speech, and harassment lead to an immediate permanent ban.</li>"
                        + "<li><strong style=\"color: #ffffff;\">3. No NSFW / Explicit Material:</strong> Adult content, graphic violence, and illegal substances are strictly prohibited.</li>"
                        + "<li><strong style=\"color: #ffffff;\">4. Campus Decorum:</strong> Treat classmates with respect. Do not use the platform for cheating, scams, or campus defamation.</li>"
                        + "<li><strong style=\"color: #ffffff;\">5. Mutual Reveal:</strong> Real identities in private DMs remain hidden unless both students mutually accept an identity reveal.</li>"
                        + "<li><strong style=\"color: #ffffff;\">6. Email Blacklisting:</strong> Violations will result in your college email being permanently blacklisted from ever registering again.</li>"
                        + "<li><strong style=\"color: #ffffff;\">7. Account Security:</strong> You are responsible for all actions conducted under your account credentials.</li>"
                        + "</ul>"
                        + "</div>"
                        + "<p style=\"font-size: 14px; color: #9ca3af; line-height: 1.6;\">Enjoy your college community experience. Stay curious, stay supportive, and stay anonymous!</p>"
                        + "<hr style=\"border: none; border-top: 1px solid #26243c; margin: 24px 0;\">"
                        + "<p style=\"font-size: 12px; color: #6b7280; text-align: center;\">© 2026 Ghost Community Inc. All rights reserved.</p>"
                        + "</div>";

                dispatchEmail(toEmail, subject, htmlContent);
            } catch (Exception e) {
                log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
            }
        }).start();
    }

    /**
     * Send Password Reset Email with reset link & code
     */
    public void sendPasswordResetEmail(String toEmail, String resetCode) {
        String link = "https://ghost-qmvi.onrender.com/api/auth/reset-password-page?token=" + resetCode + "&code=" + resetCode;
        sendPasswordResetEmail(toEmail, link, resetCode);
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink, String resetCode) {
        new Thread(() -> {
            try {
                String subject = "Reset Your Ghost Password 🔒";
                String htmlContent = "<div style=\"font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0c0b18; color: #ffffff; padding: 32px; border-radius: 16px; max-width: 600px; margin: auto;\">"
                        + "<div style=\"text-align: center; margin-bottom: 24px;\">"
                        + "<h1 style=\"color: #a855f7; margin: 0; font-size: 26px;\">Ghost Community</h1>"
                        + "<p style=\"color: #9ca3af; font-size: 14px; margin-top: 6px;\">Password Reset Request</p>"
                        + "</div>"
                        + "<p style=\"font-size: 15px; color: #e5e7eb; line-height: 1.6;\">Hello,</p>"
                        + "<p style=\"font-size: 14px; color: #d1d5db; line-height: 1.6;\">We received a request to reset your Ghost Community password. Click the button below to securely set a new password:</p>"
                        + "<div style=\"text-align: center; margin: 28px 0;\">"
                        + "<a href=\"" + resetLink + "\" style=\"background-color: #a855f7; color: #ffffff; text-decoration: none; padding: 14px 28px; border-radius: 10px; font-weight: bold; font-size: 15px; display: inline-block;\">Reset Password</a>"
                        + "</div>"
                        + "<div style=\"background-color: #151426; border: 1px solid #2e2a4a; border-radius: 12px; padding: 16px; margin: 20px 0; text-align: center;\">"
                        + "<p style=\"font-size: 12px; color: #9ca3af; margin: 0 0 6px 0;\">Or enter this 6-digit Reset Code in the Ghost app:</p>"
                        + "<div style=\"font-size: 24px; font-weight: bold; letter-spacing: 4px; color: #38bdf8;\">" + resetCode + "</div>"
                        + "</div>"
                        + "<p style=\"font-size: 13px; color: #9ca3af; line-height: 1.5;\">This link and code will expire in <strong>30 minutes</strong>. After resetting your password, you will be automatically redirected to the login page.</p>"
                        + "<p style=\"font-size: 12px; color: #6b7280; margin-top: 24px;\">If you did not request a password reset, you can safely ignore this email. Your account remains secure.</p>"
                        + "</div>";

                dispatchEmail(toEmail, subject, htmlContent);
            } catch (Exception e) {
                log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            }
        }).start();
    }

    /**
     * Dispatches email via SendGrid HTTPS API (Port 443) if configured,
     * or falls back to Spring JavaMailSender (Port 587).
     */
    public boolean dispatchEmail(String toEmail, String subject, String htmlContent) {
        // Priority 1: SendGrid HTTPS REST API (Works everywhere, never blocked on Render)
        if (sendGridApiKey != null && !sendGridApiKey.isBlank() && sendGridApiKey.startsWith("SG.")) {
            try {
                boolean sent = sendViaSendGridHttpsApi(toEmail, subject, htmlContent);
                if (sent) {
                    log.info("Email successfully sent via SendGrid HTTPS API to {}", toEmail);
                    return true;
                }
            } catch (Exception e) {
                log.warn("SendGrid HTTPS API failed ({}), falling back to SMTP: {}", e.getMessage(), toEmail);
            }
        }

        // Priority 2: JavaMailSender (SMTP)
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
            helper.setFrom(fromEmail, "Ghost Community");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(mimeMessage);
            log.info("Email successfully sent via SMTP to: {}", toEmail);
            return true;
        } catch (Exception e) {
            log.error("SMTP sending failed for {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    /**
     * SendGrid v3 Mail Send API over standard HTTPS (Port 443)
     */
    private boolean sendViaSendGridHttpsApi(String toEmail, String subject, String htmlContent) throws Exception {
        // Build JSON payload safely
        String escapedSubject = subject.replace("\"", "\\\"");
        String escapedHtml = htmlContent.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");

        String jsonPayload = "{"
                + "\"personalizations\": [{\"to\": [{\"email\": \"" + toEmail + "\"}]}],"
                + "\"from\": {\"email\": \"" + fromEmail + "\", \"name\": \"Ghost Community\"},"
                + "\"subject\": \"" + escapedSubject + "\","
                + "\"content\": [{\"type\": \"text/html\", \"value\": \"" + escapedHtml + "\"}]"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                .header("Authorization", "Bearer " + sendGridApiKey.trim())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return true;
        } else {
            log.error("SendGrid API returned status {}: {}", response.statusCode(), response.body());
            return false;
        }
    }
}

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
     * (COMMENTED OUT: No external email dispatch to Gmail)
     */
    public void sendWelcomeRegistrationEmail(String toEmail, String realName) {
        log.info("[In-App Mode] Welcome email to {} skipped (external email dispatch commented out).", toEmail);
        /*
        new Thread(() -> {
            try {
                String greetingName = (realName != null && !realName.isBlank()) ? realName : "Student";
                String subject = "Welcome to Ghost Community 👻 - Campus Anonymous Network";
                ...
                dispatchEmail(toEmail, subject, htmlContent);
            } catch (Exception e) {
                log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
            }
        }).start();
        */
    }

    /**
     * Send Password Reset Email with reset link & code
     * (COMMENTED OUT: No external email dispatch to Gmail - in-app reset code generation active)
     */
    public void sendPasswordResetEmail(String toEmail, String resetCode) {
        log.info("[In-App Mode] Reset code for registered email {} is {}. Gmail dispatch commented out.", toEmail, resetCode);
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink, String resetCode) {
        log.info("[In-App Mode] Reset code for registered email {} is {}. Gmail dispatch commented out.", toEmail, resetCode);
        /*
        new Thread(() -> {
            ...
            dispatchEmail(toEmail, subject, htmlContent);
        }).start();
        */
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

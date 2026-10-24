package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.model.UserStatus;
import com.ghostcommunity.backend.payload.*;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.security.JwtUtils;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import com.ghostcommunity.backend.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;
    private final com.ghostcommunity.backend.repository.PasswordResetTokenRepository tokenRepository;
    private final org.springframework.mail.javamail.JavaMailSender mailSender;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail().trim(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            User user = userRepository.findById(userDetails.getId()).orElseThrow();

            return ResponseEntity.ok(new JwtResponse(jwt,
                    userDetails.getId(),
                    userDetails.getUsername(),
                    userDetails.getStatus(),
                    user.getGhostName()));
        } catch (Exception e) {
            log.error("Login failure: ", e);
            String msg = e.getMessage() != null ? e.getMessage() : "Invalid credentials";
            return ResponseEntity.status(401).body(Map.of("error", msg));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        try {
            if (userRepository.existsByEmail(signUpRequest.getEmail())) {
                return ResponseEntity
                        .badRequest()
                        .body(new MessageResponse("Error: Email is already in use!"));
            }

            // Create new user's account
            User user = new User();
            user.setEmail(signUpRequest.getEmail().trim().toLowerCase());
            user.setPasswordHash(encoder.encode(signUpRequest.getPassword()));
            user.setRealName(signUpRequest.getRealName());
            user.setTermsAccepted(true);
            user.setStatus(UserStatus.ONBOARDING_INCOMPLETE);

            user = userRepository.save(user);

            // Send Welcome Greeting & Terms Email!
            try {
                emailService.sendWelcomeRegistrationEmail(user.getEmail(), user.getRealName());
            } catch (Exception mailEx) {
                log.warn("Welcome email dispatch skipped: {}", mailEx.getMessage());
            }

            return ResponseEntity.ok(new MessageResponse("User registered successfully! Proceed to college selection."));
        } catch (Exception e) {
            log.error("Registration failure: ", e);
            String rootCause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return ResponseEntity.status(500).body(Map.of("error", "Registration error: " + rootCause));
        }
    }

    // POST /api/auth/forgot-password
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is required."));
        }

        final String cleanEmail = email.trim();
        User user = userRepository.findByEmailIgnoreCase(cleanEmail)
                .or(() -> userRepository.findByEmail(cleanEmail))
                .orElse(null);

        Map<String, Object> responseData = new java.util.HashMap<>();
        if (user != null) {
            String code = String.valueOf((int) ((Math.random() * 900000) + 100000));
            String token = UUID.randomUUID().toString().replace("-", "") + code;

            user.setResetToken(token);
            user.setTokenExpiry(LocalDateTime.now().plusMinutes(30));
            userRepository.save(user);

            // Also save into PasswordResetToken table for compatibility
            try {
                com.ghostcommunity.backend.model.PasswordResetToken prt = com.ghostcommunity.backend.model.PasswordResetToken.builder()
                        .token(code)
                        .user(user)
                        .expiryDate(LocalDateTime.now().plusMinutes(30))
                        .build();
                tokenRepository.save(prt);
            } catch (Exception ignored) {}

            String link = "https://ghost-qmvi.onrender.com/api/auth/reset-password-page?token=" + token + "&code=" + code;
            emailService.sendPasswordResetEmail(user.getEmail(), link, code);

            // Return code in JSON response as guaranteed fallback for mobile app
            responseData.put("code", code);
        }

        responseData.put("message", "If that email is registered, password reset instructions have been sent.");
        return ResponseEntity.ok(responseData);
    }

    // POST /api/auth/reset-password
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String token = request.get("token");
        if (token == null || token.isBlank()) {
            token = request.get("code");
        }
        String newPassword = request.get("newPassword");

        if (token == null || token.isBlank() || newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Valid verification code/token and minimum 6 character password are required."));
        }

        final String cleanToken = token.trim();
        User user = null;

        // 1. If email is provided, lookup by email first
        if (email != null && !email.isBlank()) {
            user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
            if (user != null) {
                boolean matches = (user.getResetToken() != null &&
                        (user.getResetToken().equalsIgnoreCase(cleanToken) || user.getResetToken().endsWith(cleanToken)));
                if (!matches) {
                    var prt = tokenRepository.findByToken(cleanToken);
                    if (prt.isEmpty() || !prt.get().getUser().getId().equals(user.getId())) {
                        user = null;
                    }
                }
            }
        }

        // 2. Lookup by resetToken directly
        if (user == null) {
            user = userRepository.findByResetToken(cleanToken).orElse(null);
        }

        // 3. Lookup in PasswordResetToken table
        if (user == null) {
            var prtOpt = tokenRepository.findByToken(cleanToken);
            if (prtOpt.isPresent() && prtOpt.get().getExpiryDate().isAfter(LocalDateTime.now())) {
                user = prtOpt.get().getUser();
            }
        }

        // 4. Fallback search through users
        if (user == null) {
            user = userRepository.findAll().stream()
                    .filter(u -> u.getResetToken() != null && (u.getResetToken().equalsIgnoreCase(cleanToken) || u.getResetToken().endsWith(cleanToken)))
                    .findFirst()
                    .orElse(null);
        }

        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Invalid or expired reset code. Please check the code and try again."));
        }

        if (user.getTokenExpiry() != null && user.getTokenExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Reset code has expired. Please request a new one."));
        }

        user.setPasswordHash(encoder.encode(newPassword));
        user.setResetToken(null);
        user.setTokenExpiry(null);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Password reset successfully! You can now log in."));
    }

    // GET /api/auth/reset-password-page?token=...&code=...
    @GetMapping(value = "/reset-password-page", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resetPasswordPage(
            @RequestParam(required = false, defaultValue = "") String token,
            @RequestParam(required = false, defaultValue = "") String code) {

        String activeToken = !token.isBlank() ? token : code;

        String html = "<!DOCTYPE html>"
                + "<html lang=\"en\">"
                + "<head>"
                + "<meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>Ghost - Reset Password</title>"
                + "<style>"
                + "body { background-color: #0c0b18; color: #ffffff; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }"
                + ".card { background-color: #151426; border: 1px solid #26243c; border-radius: 24px; padding: 36px 28px; width: 100%; max-width: 420px; box-shadow: 0 20px 40px rgba(0,0,0,0.5); text-align: center; }"
                + ".logo { font-size: 48px; margin-bottom: 12px; }"
                + "h2 { margin: 0 0 8px 0; font-size: 24px; font-weight: 700; }"
                + "h2 span { background: linear-gradient(135deg, #818cf8, #c084fc); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }"
                + "p { color: #9ca3af; font-size: 13px; margin: 0 0 28px 0; line-height: 1.5; }"
                + ".form-group { text-align: left; margin-bottom: 18px; }"
                + "label { display: block; font-size: 12px; color: #9ca3af; margin-bottom: 6px; font-weight: 500; }"
                + "input { width: 100%; box-sizing: border-box; background-color: #1e1d32; border: 1px solid #2e2c48; border-radius: 12px; padding: 14px 16px; color: #ffffff; font-size: 14px; outline: none; transition: border-color 0.2s; }"
                + "input:focus { border-color: #a855f7; }"
                + "button { width: 100%; background: linear-gradient(135deg, #8b5cf6, #a855f7, #6366f1); color: #ffffff; border: none; border-radius: 26px; padding: 15px; font-size: 15px; font-weight: 700; cursor: pointer; margin-top: 10px; box-shadow: 0 8px 20px rgba(168,85,247,0.3); transition: transform 0.1s; }"
                + "button:active { transform: scale(0.98); }"
                + ".error { color: #ef4444; font-size: 13px; margin-top: 14px; display: none; }"
                + ".success { color: #10b981; font-size: 14px; margin-top: 14px; display: none; }"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class=\"card\">"
                + "<div class=\"logo\">👻</div>"
                + "<h2>Reset <span>Password</span></h2>"
                + "<p>Enter your new password below to regain access to your Ghost account.</p>"
                + "<form id=\"resetForm\" action=\"/api/auth/reset-password-submit\" method=\"POST\">"
                + "<input type=\"hidden\" name=\"token\" id=\"token\" value=\"" + activeToken + "\">"
                + "<div class=\"form-group\">"
                + "<label for=\"newPassword\">New Password</label>"
                + "<input type=\"password\" id=\"newPassword\" name=\"newPassword\" required minlength=\"6\" placeholder=\"Minimum 6 characters\">"
                + "</div>"
                + "<div class=\"form-group\">"
                + "<label for=\"confirmPassword\">Confirm New Password</label>"
                + "<input type=\"password\" id=\"confirmPassword\" name=\"confirmPassword\" required minlength=\"6\" placeholder=\"Re-enter new password\">"
                + "</div>"
                + "<button type=\"submit\">Reset Password &rarr;</button>"
                + "<div id=\"errorMsg\" class=\"error\">Passwords do not match!</div>"
                + "</form>"
                + "<script>"
                + "document.getElementById('resetForm').addEventListener('submit', function(e) {"
                + "  var p1 = document.getElementById('newPassword').value;"
                + "  var p2 = document.getElementById('confirmPassword').value;"
                + "  if (p1 !== p2) {"
                + "    e.preventDefault();"
                + "    var err = document.getElementById('errorMsg');"
                + "    err.textContent = 'Passwords do not match!';"
                + "    err.style.display = 'block';"
                + "  }"
                + "});"
                + "</script>"
                + "</div>"
                + "</body>"
                + "</html>";

        return ResponseEntity.ok(html);
    }

    // POST /api/auth/reset-password-submit (Browser form submission handler)
    @PostMapping(value = "/reset-password-submit", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resetPasswordSubmit(@RequestParam String token, @RequestParam String newPassword) {
        final String cleanToken = token.trim();
        User user = userRepository.findByResetToken(cleanToken).orElse(null);
        if (user == null) {
            var prtOpt = tokenRepository.findByToken(cleanToken);
            if (prtOpt.isPresent() && prtOpt.get().getExpiryDate().isAfter(LocalDateTime.now())) {
                user = prtOpt.get().getUser();
            }
        }
        if (user == null) {
            user = userRepository.findAll().stream()
                    .filter(u -> u.getResetToken() != null && (u.getResetToken().equalsIgnoreCase(cleanToken) || u.getResetToken().endsWith(cleanToken)))
                    .findFirst()
                    .orElse(null);
        }

        if (user == null || (user.getTokenExpiry() != null && user.getTokenExpiry().isBefore(LocalDateTime.now()))) {
            String errorHtml = "<!DOCTYPE html><html><body style=\"background-color:#0c0b18;color:#fff;font-family:sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0;padding:20px;\">"
                    + "<div style=\"background-color:#151426;border:1px solid #ef4444;border-radius:20px;padding:32px;max-width:400px;text-align:center;\">"
                    + "<div style=\"font-size:48px;\">⚠️</div>"
                    + "<h2 style=\"color:#ef4444;\">Invalid or Expired Link</h2>"
                    + "<p style=\"color:#9ca3af;font-size:14px;\">This password reset link is invalid or has expired. Please open the Ghost app and request a new reset link.</p>"
                    + "</div></body></html>";
            return ResponseEntity.badRequest().body(errorHtml);
        }

        user.setPasswordHash(encoder.encode(newPassword));
        user.setResetToken(null);
        user.setTokenExpiry(null);
        userRepository.save(user);

        String successHtml = "<!DOCTYPE html><html><body style=\"background-color:#0c0b18;color:#fff;font-family:sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0;padding:20px;\">"
                + "<div style=\"background-color:#151426;border:1px solid #10b981;border-radius:24px;padding:36px 28px;max-width:400px;text-align:center;box-shadow:0 20px 40px rgba(0,0,0,0.5);\">"
                + "<div style=\"font-size:48px;margin-bottom:12px;\">🎉</div>"
                + "<h2 style=\"color:#10b981;margin:0 0 10px 0;\">Password Reset Successfully!</h2>"
                + "<p style=\"color:#9ca3af;font-size:14px;line-height:1.5;\">Your Ghost Community password has been updated. You can now return to the Ghost mobile app and log in with your new password.</p>"
                + "<div style=\"margin-top:28px;\">"
                + "<a href=\"ghost://login\" id=\"redirectBtn\" style=\"background:linear-gradient(135deg,#8b5cf6,#a855f7);color:#fff;text-decoration:none;padding:14px 28px;border-radius:24px;font-weight:bold;font-size:14px;display:inline-block;\">Open Ghost App &rarr;</a>"
                + "</div>"
                + "<script>"
                + "setTimeout(function() { window.location.href = 'ghost://login'; }, 1200);"
                + "</script>"
                + "</div></body></html>";

        return ResponseEntity.ok(successHtml);
    }

    @GetMapping("/test-email")
    public ResponseEntity<?> testEmail(@RequestParam(defaultValue = "chiragchangappa02@gmail.com") String to) {
        boolean sent = emailService.dispatchEmail(to, "Test Email from Ghost Community", "<h3>Hello from Ghost!</h3><p>Email delivery test is operational.</p>");
        if (sent) {
            return ResponseEntity.ok("EMAIL_SENT_SUCCESS to " + to);
        } else {
            return ResponseEntity.status(500).body("EMAIL_FAILED: Cloud platform blocked SMTP port 587. Please configure SENDGRID_API_KEY.");
        }
    }
}

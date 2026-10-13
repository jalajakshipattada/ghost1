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

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        return ResponseEntity.ok(new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getStatus(),
                user.getGhostName()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user's account
        User user = new User();
        user.setEmail(signUpRequest.getEmail().trim());
        user.setPasswordHash(encoder.encode(signUpRequest.getPassword()));
        user.setRealName(signUpRequest.getRealName());
        user.setTermsAccepted(true);
        user.setStatus(UserStatus.ONBOARDING_INCOMPLETE);

        userRepository.save(user);

        // Send Welcome Greeting & Terms Email!
        emailService.sendWelcomeRegistrationEmail(user.getEmail(), user.getRealName());

        return ResponseEntity.ok(new MessageResponse("User registered successfully! Proceed to college selection."));
    }

    // POST /api/auth/forgot-password
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is required."));
        }

        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            String code = String.valueOf((int) ((Math.random() * 900000) + 100000));
            String token = UUID.randomUUID().toString().replace("-", "") + code;

            user.setResetToken(token);
            user.setTokenExpiry(LocalDateTime.now().plusMinutes(30));
            userRepository.save(user);

            String link = "https://ghost-qmvi.onrender.com/api/auth/reset-password-page?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), link, code);
        });

        return ResponseEntity.ok(new MessageResponse("If that email is registered, password reset instructions have been sent."));
    }

    // POST /api/auth/reset-password
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String newPassword = request.get("newPassword");

        if (token == null || token.isBlank() || newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Valid token and minimum 6 character password are required."));
        }

        final String cleanToken = token.trim();
        User user = userRepository.findByResetToken(cleanToken).orElse(null);
        if (user == null) {
            user = userRepository.findAll().stream()
                    .filter(u -> u.getResetToken() != null && (u.getResetToken().equals(cleanToken) || u.getResetToken().endsWith(cleanToken)))
                    .findFirst()
                    .orElse(null);
        }

        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Invalid or expired reset token/code."));
        }

        if (user.getTokenExpiry() == null || user.getTokenExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Reset token has expired. Please request a new one."));
        }

        user.setPasswordHash(encoder.encode(newPassword));
        user.setResetToken(null);
        user.setTokenExpiry(null);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Password reset successfully! You can now log in."));
    }

    // GET /api/auth/reset-password-page?token=...
    @GetMapping(value = "/reset-password-page", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resetPasswordPage(@RequestParam(required = false, defaultValue = "") String token) {
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
                + "<input type=\"hidden\" name=\"token\" id=\"token\" value=\"" + token + "\">"
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
            user = userRepository.findAll().stream()
                    .filter(u -> u.getResetToken() != null && (u.getResetToken().equals(cleanToken) || u.getResetToken().endsWith(cleanToken)))
                    .findFirst()
                    .orElse(null);
        }

        if (user == null || user.getTokenExpiry() == null || user.getTokenExpiry().isBefore(LocalDateTime.now())) {
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
                + "<a href=\"ghost://login\" style=\"background:linear-gradient(135deg,#8b5cf6,#a855f7);color:#fff;text-decoration:none;padding:14px 28px;border-radius:24px;font-weight:bold;font-size:14px;display:inline-block;\">Open Ghost App &rarr;</a>"
                + "</div>"
                + "</div></body></html>";

        return ResponseEntity.ok(successHtml);
    }
}

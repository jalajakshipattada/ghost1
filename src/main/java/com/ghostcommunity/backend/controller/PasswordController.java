package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.PasswordResetToken;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.payload.MessageResponse;
import com.ghostcommunity.backend.repository.PasswordResetTokenRepository;
import com.ghostcommunity.backend.repository.UserRepository;
import com.ghostcommunity.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth/password")
@RequiredArgsConstructor
public class PasswordController {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/forgot")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            // Generate a random 6-character alphanumeric code
            String resetCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            
            PasswordResetToken token = PasswordResetToken.builder()
                    .token(resetCode)
                    .user(user)
                    .expiryDate(LocalDateTime.now().plusMinutes(15)) // Strict 15-minute expiration
                    .build();
            
            tokenRepository.save(token);

            // Send the email
            try {
                emailService.sendPasswordResetEmail(user.getEmail(), resetCode);
            } catch (Exception e) {
                // In a production environment, you would log this error.
                // It will fail right now because SMTP is not configured in application.properties yet.
                System.out.println("SMTP NOT CONFIGURED YET. Your reset code is: " + resetCode);
            }
        }

        // SECURITY BEST PRACTICE: Always return a success message even if the email doesn't exist.
        // This prevents hackers from repeatedly guessing emails to see who is registered on the app.
        return ResponseEntity.ok(new MessageResponse("If that email is registered, a reset code has been sent to your inbox."));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> payload) {
        String code = payload.get("code");
        String newPassword = payload.get("newPassword");

        PasswordResetToken resetToken = tokenRepository.findByToken(code)
                .orElseThrow(() -> new RuntimeException("Invalid reset code."));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Reset code has expired. Please request a new one."));
        }

        // Hash the new password and save it
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Delete the token so the hacker can't use it again
        tokenRepository.delete(resetToken);

        return ResponseEntity.ok(new MessageResponse("Password has been reset successfully! You can now log in."));
    }
}

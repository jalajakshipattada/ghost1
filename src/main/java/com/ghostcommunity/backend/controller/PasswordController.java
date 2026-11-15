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
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is required."));
        }

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email.trim())
                .or(() -> userRepository.findByEmail(email.trim()));

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: No account found with this email."));
        }

        User user = userOpt.get();
        // Generate a 6-digit verification code
        String resetCode = String.valueOf((int) ((Math.random() * 900000) + 100000));
        
        PasswordResetToken token = PasswordResetToken.builder()
                .token(resetCode)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(30))
                .build();
        
        tokenRepository.save(token);

        // In-App verification code generated: Email sending commented out as requested
        /*
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), resetCode);
        } catch (Exception e) {
            System.out.println("Reset code: " + resetCode);
        }
        */

        Map<String, Object> res = new java.util.HashMap<>();
        res.put("code", resetCode);
        res.put("email", user.getEmail());
        res.put("message", "Verification code generated: " + resetCode);
        return ResponseEntity.ok(res);
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

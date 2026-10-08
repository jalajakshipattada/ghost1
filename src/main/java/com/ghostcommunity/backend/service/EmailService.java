package com.ghostcommunity.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendPasswordResetEmail(String toEmail, String resetCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        // You will need to put your actual email in application.properties later
        message.setFrom("noreply@ghostcommunity.com"); 
        message.setTo(toEmail);
        message.setSubject("Ghost Community - Password Reset");
        message.setText("Hello,\n\nYou requested a password reset for your Ghost Community account.\n\n" +
                "Your 6-digit secret reset code is: " + resetCode + "\n\n" +
                "If you did not request this, please ignore this email and your password will remain unchanged.\n\n" +
                "Stay Anonymous,\nGhost Community Team");
        
        mailSender.send(message);
    }
}

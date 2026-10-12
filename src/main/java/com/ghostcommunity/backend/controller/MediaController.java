package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final CloudinaryService cloudinaryService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String secureUrl = cloudinaryService.uploadFile(file);
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : UUID.randomUUID().toString();
            String fileType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

            return ResponseEntity.ok(Map.of(
                    "url", secureUrl,
                    "fileName", fileName,
                    "fileType", fileType
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("error", "Could not upload file: " + ex.getMessage()));
        }
    }
}

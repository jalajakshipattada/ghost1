package com.ghostcommunity.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/media")
public class MediaController {

    // For the MVP, we will save files to a local directory named 'uploads'
    // Later, this can be swapped out to save to AWS S3 or Firebase Storage
    private final Path fileStorageLocation;

    public MediaController() {
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // Generate a unique filename to prevent overwriting
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            
            // Save the file
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Generate the URL that the Flutter app will use to download/view the image or audio
            String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/media/download/")
                    .path(fileName)
                    .toUriString();

            return ResponseEntity.ok(Map.of(
                    "url", fileDownloadUri,
                    "fileName", fileName,
                    "fileType", file.getContentType()
            ));
        } catch (IOException ex) {
            return ResponseEntity.status(500).body("Could not store file. Please try again!");
        }
    }
}

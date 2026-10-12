package com.ghostcommunity.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public String uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        try {
            // Upload with resource_type auto to handle images, videos, etc.
            @SuppressWarnings("rawtypes")
            Map result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("resource_type", "auto")
            );

            if (result != null && result.containsKey("secure_url")) {
                String secureUrl = result.get("secure_url").toString();
                log.info("Successfully uploaded file to Cloudinary: {}", secureUrl);
                return secureUrl;
            }
            throw new RuntimeException("Cloudinary response did not contain secure_url");
        } catch (Exception e) {
            log.error("Cloudinary upload failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to Cloudinary: " + e.getMessage(), e);
        }
    }
}

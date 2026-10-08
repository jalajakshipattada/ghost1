package com.ghostcommunity.backend.config;

import com.ghostcommunity.backend.model.College;
import com.ghostcommunity.backend.model.Section;
import com.ghostcommunity.backend.repository.CollegeRepository;
import com.ghostcommunity.backend.repository.SectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final CollegeRepository collegeRepository;
    private final SectionRepository sectionRepository;

    @Override
    public void run(String... args) throws Exception {
        // Only load data if the database is completely empty
        if (collegeRepository.count() == 0) {
            System.out.println("No colleges found in DB. Running DataLoader...");

            // 1. Create the Example College
            College abcCollege = College.builder()
                    .name("ABC Engineering College")
                    .build();
            
            abcCollege = collegeRepository.save(abcCollege);

            // 2. Create the 16 Sections
            List<String> sectionNames = Arrays.asList(
                    "CSE-1", "CSE-2", "CSE-3", "CSE-4",
                    "ECE-1", "ECE-2", "ECE-3", "ECE-4",
                    "ME-1", "ME-2", "ME-3", "ME-4",
                    "CIV-1", "CIV-2", "CIV-3", "CIV-4"
            );

            for (String sectionName : sectionNames) {
                Section section = Section.builder()
                        .name(sectionName)
                        .college(abcCollege)
                        .build();
                sectionRepository.save(section);
            }

            System.out.println("Successfully loaded ABC Engineering College and its 16 sections!");
        }
    }
}

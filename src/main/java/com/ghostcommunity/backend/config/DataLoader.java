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
        // 1. Rename existing "ABC Engineering College" to "Canara Engineering College" if present
        collegeRepository.findByName("ABC Engineering College").ifPresent(c -> {
            c.setName("Canara Engineering College");
            collegeRepository.save(c);
            System.out.println("Updated existing college name to Canara Engineering College!");
        });

        // 2. If no colleges exist at all, create Canara Engineering College
        if (collegeRepository.count() == 0) {
            System.out.println("No colleges found in DB. Seeding Canara Engineering College...");
            College canaraCollege = College.builder()
                    .name("Canara Engineering College")
                    .build();
            canaraCollege = collegeRepository.save(canaraCollege);

            List<String> sectionNames = Arrays.asList(
                    "CSE-1", "CSE-2", "CSE-3", "CSE-4",
                    "ECE-1", "ECE-2", "ECE-3", "ECE-4",
                    "ME-1", "ME-2", "ME-3", "ME-4",
                    "CIV-1", "CIV-2", "CIV-3", "CIV-4",
                    "EEE-1", "EEE-2", "EEE-3", "EEE-4"
            );

            for (String sectionName : sectionNames) {
                Section section = Section.builder()
                        .name(sectionName)
                        .college(canaraCollege)
                        .build();
                sectionRepository.save(section);
            }
            System.out.println("Successfully loaded Canara Engineering College and its sections!");
        } else {
            // Ensure EEE sections exist for Canara Engineering College if missing
            College canaraCollege = collegeRepository.findAll().get(0);
            List<String> extraSections = Arrays.asList("EEE-1", "EEE-2", "EEE-3", "EEE-4");
            for (String sName : extraSections) {
                if (sectionRepository.findByCollegeId(canaraCollege.getId()).stream().noneMatch(s -> s.getName().equals(sName))) {
                    Section section = Section.builder()
                            .name(sName)
                            .college(canaraCollege)
                            .build();
                    sectionRepository.save(section);
                }
            }
        }
    }
}

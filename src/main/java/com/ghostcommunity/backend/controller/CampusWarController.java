package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.*;
import com.ghostcommunity.backend.repository.*;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/campus")
@RequiredArgsConstructor
public class CampusWarController {

    private final CampusStructureRepository campusStructureRepository;
    private final SectionRepository sectionRepository;
    private final UserRepository userRepository;
    private final FortressRaidLogRepository fortressRaidLogRepository;

    /**
     * GET /api/campus/my
     * Returns current user's war points and their section's 3D campus structures.
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyCampus() {
        User user = getCurrentUser();
        Section mySection = user.getPrimarySection();
        if (mySection == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No primary section assigned"));
        }

        List<CampusStructure> structures = ensureSectionStructures(mySection);

        long intactCount = structures.stream().filter(s -> "INTACT".equals(s.getStatus())).count();
        long destroyedCount = structures.stream().filter(s -> "DESTROYED".equals(s.getStatus())).count();
        long damagedCount = structures.stream().filter(s -> "DAMAGED".equals(s.getStatus())).count();

        Map<String, Object> response = new HashMap<>();
        response.put("sectionId", mySection.getId());
        response.put("sectionName", mySection.getName());
        response.put("collegeName", mySection.getCollege().getName());
        response.put("userWarPoints", user.getWarPoints());
        response.put("structures", structures);
        response.put("intactCount", intactCount);
        response.put("damagedCount", damagedCount);
        response.put("destroyedCount", destroyedCount);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/campus/sections
     * List all rival sections in the same college with campus integrity status.
     */
    @GetMapping("/sections")
    public ResponseEntity<?> getCollegeSections() {
        User user = getCurrentUser();
        Section mySection = user.getPrimarySection();
        Long mySecId = mySection != null ? mySection.getId() : -1L;
        Long collegeId = user.getCollege() != null ? user.getCollege().getId() : 1L;

        List<Section> collegeSections = sectionRepository.findByCollegeId(collegeId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Section sec : collegeSections) {
            List<CampusStructure> structs = ensureSectionStructures(sec);
            long intact = structs.stream().filter(s -> "INTACT".equals(s.getStatus())).count();
            long destroyed = structs.stream().filter(s -> "DESTROYED".equals(s.getStatus())).count();
            long damaged = structs.stream().filter(s -> "DAMAGED".equals(s.getStatus())).count();

            Map<String, Object> item = new HashMap<>();
            item.put("sectionId", sec.getId());
            item.put("sectionName", sec.getName());
            item.put("isMySection", sec.getId().equals(mySecId));
            item.put("totalStructures", structs.size());
            item.put("intactCount", intact);
            item.put("damagedCount", damaged);
            item.put("destroyedCount", destroyed);
            result.add(item);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/campus/view/{sectionId}
     * Inspect a specific section's 3D campus structures to target or view.
     */
    @GetMapping("/view/{sectionId}")
    public ResponseEntity<?> viewSectionCampus(@PathVariable Long sectionId) {
        Section sec = sectionRepository.findById(sectionId).orElse(null);
        if (sec == null) {
            return ResponseEntity.notFound().build();
        }

        List<CampusStructure> structures = ensureSectionStructures(sec);
        User user = getCurrentUser();

        Map<String, Object> response = new HashMap<>();
        response.put("sectionId", sec.getId());
        response.put("sectionName", sec.getName());
        response.put("isMySection", user.getPrimarySection() != null && user.getPrimarySection().getId().equals(sec.getId()));
        response.put("userWarPoints", user.getWarPoints());
        response.put("structures", structures);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/campus/attack
     * Launch gun, small missile, or big missile at a rival campus's specific building/pole.
     */
    @PostMapping("/attack")
    public ResponseEntity<?> attackCampusStructure(@RequestBody Map<String, Object> payload) {
        User user = getCurrentUser();
        Long targetSectionId = Long.valueOf(payload.get("targetSectionId").toString());
        String structureKey = (String) payload.get("structureKey");
        String weapon = ((String) payload.getOrDefault("weapon", "GUN")).toUpperCase();

        if (user.getPrimarySection() != null && user.getPrimarySection().getId().equals(targetSectionId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot attack your own section campus!"));
        }

        int cost;
        int damage;
        switch (weapon) {
            case "BIG_MISSILE":
                cost = 400;
                damage = 120;
                break;
            case "SMALL_MISSILE":
                cost = 150;
                damage = 60;
                break;
            case "GUN":
            default:
                cost = 50;
                damage = 30;
                weapon = "GUN";
                break;
        }

        if (user.getWarPoints() < cost) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Insufficient War Points! You need " + cost + " pts. Chat more to earn +10 pts per msg!"
            ));
        }

        Section targetSec = sectionRepository.findById(targetSectionId).orElse(null);
        if (targetSec == null) {
            return ResponseEntity.notFound().build();
        }

        // Ensure structures exist
        ensureSectionStructures(targetSec);

        CampusStructure structure = campusStructureRepository
                .findBySectionIdAndStructureKey(targetSectionId, structureKey)
                .orElse(null);

        if (structure == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Structure not found: " + structureKey));
        }

        if ("DESTROYED".equals(structure.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", structure.getDisplayName() + " is already completely destroyed! Pick another target."));
        }

        // Deduct user points
        user.setWarPoints(user.getWarPoints() - cost);
        userRepository.save(user);

        // Apply damage
        int newHp = Math.max(0, structure.getHp() - damage);
        structure.setHp(newHp);

        boolean wasDestroyed = newHp == 0;
        if (wasDestroyed) {
            structure.setStatus("DESTROYED");
        } else {
            structure.setStatus("DAMAGED");
        }
        campusStructureRepository.save(structure);

        // Record battle log
        FortressRaidLog log = FortressRaidLog.builder()
                .attacker(user)
                .attackerSection(user.getPrimarySection())
                .targetSection(targetSec)
                .weapon(weapon)
                .damageDealt(damage)
                .pointsStolen(cost / 2)
                .shieldAbsorbed(false)
                .timestamp(LocalDateTime.now())
                .build();
        fortressRaidLogRepository.save(log);

        String message;
        if (wasDestroyed) {
            message = "💥 DIRECT HIT! " + targetSec.getName() + "'s " + structure.getDisplayName() + " was completely DESTROYED!";
        } else {
            message = "⚡ HIT! Dealt " + damage + " damage to " + targetSec.getName() + "'s " + structure.getDisplayName() + " (" + newHp + "/" + structure.getMaxHp() + " HP left)!";
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("weapon", weapon);
        resp.put("damage", damage);
        resp.put("structureKey", structureKey);
        resp.put("targetStructure", structure.getDisplayName());
        resp.put("targetSectionName", targetSec.getName());
        resp.put("remainingHp", structure.getHp());
        resp.put("maxHp", structure.getMaxHp());
        resp.put("structureStatus", structure.getStatus());
        resp.put("userWarPoints", user.getWarPoints());
        resp.put("message", message);

        return ResponseEntity.ok(resp);
    }

    /**
     * POST /api/campus/rebuild
     * Classmates pool points to rebuild destroyed or damaged structures.
     */
    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuildStructure(@RequestBody Map<String, Object> payload) {
        User user = getCurrentUser();
        Long structureId = Long.valueOf(payload.get("structureId").toString());
        int points = Integer.parseInt(payload.getOrDefault("points", 50).toString());

        if (points <= 0) points = 50;

        CampusStructure structure = campusStructureRepository.findById(structureId).orElse(null);
        if (structure == null) {
            return ResponseEntity.notFound().build();
        }

        if (user.getPrimarySection() == null || !structure.getSection().getId().equals(user.getPrimarySection().getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You can only rebuild structures in your own section!"));
        }

        if ("INTACT".equals(structure.getStatus()) && structure.getHp() == structure.getMaxHp()) {
            return ResponseEntity.badRequest().body(Map.of("error", structure.getDisplayName() + " is already 100% intact!"));
        }

        int pointsToUse = Math.min(points, user.getWarPoints());
        if (pointsToUse <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "You do not have enough War Points! Chat in section to earn points (+10 pts per msg)."));
        }

        int needed = structure.getRebuildPointsRequired() - structure.getRebuildPointsContributed();
        int actuallyContributed = Math.min(pointsToUse, needed);

        user.setWarPoints(user.getWarPoints() - actuallyContributed);
        userRepository.save(user);

        structure.setRebuildPointsContributed(structure.getRebuildPointsContributed() + actuallyContributed);

        boolean fullyRebuilt = structure.getRebuildPointsContributed() >= structure.getRebuildPointsRequired();
        if (fullyRebuilt) {
            structure.setStatus("INTACT");
            structure.setHp(structure.getMaxHp());
            structure.setRebuildPointsContributed(0);
        } else {
            // Proportional repair
            double progress = (double) structure.getRebuildPointsContributed() / structure.getRebuildPointsRequired();
            int newHp = (int) (progress * structure.getMaxHp());
            structure.setHp(Math.max(structure.getHp(), newHp));
            if (structure.getHp() > 0 && "DESTROYED".equals(structure.getStatus())) {
                structure.setStatus("DAMAGED");
            }
        }
        campusStructureRepository.save(structure);

        String message = fullyRebuilt
                ? "✨ REBUILD COMPLETE! " + structure.getDisplayName() + " is restored to 100% health!"
                : "🛠️ Contributed " + actuallyContributed + " points to rebuild " + structure.getDisplayName() + "! (" + structure.getRebuildPointsContributed() + "/" + structure.getRebuildPointsRequired() + " pts)";

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("fullyRebuilt", fullyRebuilt);
        resp.put("structure", structure);
        resp.put("userWarPoints", user.getWarPoints());
        resp.put("message", message);

        return ResponseEntity.ok(resp);
    }

    /**
     * GET /api/campus/user-points
     */
    @GetMapping("/user-points")
    public ResponseEntity<?> getUserPoints() {
        User user = getCurrentUser();
        return ResponseEntity.ok(Map.of(
                "warPoints", user.getWarPoints(),
                "ghostName", user.getGhostName()
        ));
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        return userRepository.findByIdWithSectionAndCollege(userDetails.getId()).orElseThrow();
    }

    /**
     * Initializes default 7 campus structures if not already created for a section.
     */
    private List<CampusStructure> ensureSectionStructures(Section section) {
        List<CampusStructure> existing = campusStructureRepository.findBySectionId(section.getId());
        if (!existing.isEmpty()) {
            return existing;
        }

        List<CampusStructure> defaults = List.of(
                CampusStructure.builder()
                        .section(section)
                        .structureKey("MAIN_HALL")
                        .displayName("Main Academic Block")
                        .hp(200)
                        .maxHp(200)
                        .status("INTACT")
                        .rebuildPointsRequired(200)
                        .rebuildPointsContributed(0)
                        .coordX(0.0).coordY(0.0).coordZ(0.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("CANTEEN")
                        .displayName("Campus Canteen & Cafe")
                        .hp(100)
                        .maxHp(100)
                        .status("INTACT")
                        .rebuildPointsRequired(120)
                        .rebuildPointsContributed(0)
                        .coordX(-140.0).coordY(0.0).coordZ(-70.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("LIBRARY")
                        .displayName("Tech Library & Clock Tower")
                        .hp(120)
                        .maxHp(120)
                        .status("INTACT")
                        .rebuildPointsRequired(140)
                        .rebuildPointsContributed(0)
                        .coordX(140.0).coordY(0.0).coordZ(-70.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("LIGHT_POLE_1")
                        .displayName("North Light Pole")
                        .hp(40)
                        .maxHp(40)
                        .status("INTACT")
                        .rebuildPointsRequired(40)
                        .rebuildPointsContributed(0)
                        .coordX(-80.0).coordY(0.0).coordZ(90.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("LIGHT_POLE_2")
                        .displayName("South Light Pole")
                        .hp(40)
                        .maxHp(40)
                        .status("INTACT")
                        .rebuildPointsRequired(40)
                        .rebuildPointsContributed(0)
                        .coordX(80.0).coordY(0.0).coordZ(90.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("GARDEN")
                        .displayName("Campus Garden & Quad")
                        .hp(60)
                        .maxHp(60)
                        .status("INTACT")
                        .rebuildPointsRequired(60)
                        .rebuildPointsContributed(0)
                        .coordX(0.0).coordY(0.0).coordZ(120.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("LASER_TURRET")
                        .displayName("Defense Laser Turret")
                        .hp(80)
                        .maxHp(80)
                        .status("INTACT")
                        .rebuildPointsRequired(80)
                        .rebuildPointsContributed(0)
                        .coordX(0.0).coordY(0.0).coordZ(-140.0)
                        .build()
        );

        return campusStructureRepository.saveAll(defaults);
    }
}

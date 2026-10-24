package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.*;
import com.ghostcommunity.backend.repository.*;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
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
        try {
            User user = getCurrentUser();
            Section mySection = user.getPrimarySection();
            if (mySection == null) {
                Long collegeId = (user.getCollege() != null) ? user.getCollege().getId() : 1L;
                List<Section> sections = sectionRepository.findByCollegeId(collegeId);
                if (!sections.isEmpty()) {
                    mySection = sections.get(0);
                    user.setPrimarySection(mySection);
                    userRepository.save(user);
                } else {
                    return ResponseEntity.badRequest().body(Map.of("error", "No primary section available"));
                }
            }

            List<CampusStructure> structures = ensureSectionStructures(mySection);

            long intactCount = structures.stream().filter(s -> "INTACT".equals(s.getStatus())).count();
            long destroyedCount = structures.stream().filter(s -> "DESTROYED".equals(s.getStatus())).count();
            long damagedCount = structures.stream().filter(s -> "DAMAGED".equals(s.getStatus())).count();
            long buildingCount = structures.stream().filter(s -> "BUILDING".equals(s.getStatus())).count();
            long lockedCount = structures.stream().filter(s -> "LOCKED".equals(s.getStatus())).count();

            // Find current active building/rebuild project
            CampusStructure currentProject = findActiveProject(structures);

            String collegeName = "Campus";
            try {
                if (mySection.getCollege() != null) {
                    collegeName = mySection.getCollege().getName();
                }
            } catch (Exception ignored) {}

            Map<String, Object> response = new HashMap<>();
            response.put("sectionId", mySection.getId());
            response.put("sectionName", mySection.getName());
            response.put("collegeName", collegeName);
            response.put("userWarPoints", user.getWarPoints());
            response.put("structures", structures);
            response.put("intactCount", intactCount);
            response.put("damagedCount", damagedCount);
            response.put("destroyedCount", destroyedCount);
            response.put("buildingCount", buildingCount);
            response.put("lockedCount", lockedCount);
            response.put("currentProject", currentProject);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("getMyCampus error: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Campus error: " + e.getMessage()));
        }
    }

    /**
     * GET /api/campus/sections
     * List all rival sections in the same college with campus integrity status.
     */
    @GetMapping("/sections")
    public ResponseEntity<?> getCollegeSections() {
        try {
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
        } catch (Exception e) {
            log.error("getCollegeSections error: ", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/campus/view/{sectionId}
     * Inspect a specific section's 3D campus structures to target or view.
     */
    @GetMapping("/view/{sectionId}")
    public ResponseEntity<?> viewSectionCampus(@PathVariable Long sectionId) {
        try {
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
        } catch (Exception e) {
            log.error("viewSectionCampus error: ", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/campus/attack
     * Launch gun, small missile, or big missile at a rival campus's specific building/pole.
     */
    @PostMapping("/attack")
    public ResponseEntity<?> attackCampusStructure(@RequestBody Map<String, Object> payload) {
        try {
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

            ensureSectionStructures(targetSec);

            CampusStructure structure = campusStructureRepository
                    .findBySectionIdAndStructureKey(targetSectionId, structureKey)
                    .orElse(null);

            if (structure == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Structure not found: " + structureKey));
            }

            if ("LOCKED".equals(structure.getStatus()) || "BUILDING".equals(structure.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", structure.getDisplayName() + " has not been built yet!"));
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
                structure.setRebuildPointsContributed(0);
            } else {
                structure.setStatus("DAMAGED");
            }
            campusStructureRepository.save(structure);

            // Record battle log
            try {
                FortressRaidLog logEntry = FortressRaidLog.builder()
                        .attacker(user)
                        .attackerSection(user.getPrimarySection())
                        .targetSection(targetSec)
                        .weapon(weapon)
                        .damageDealt(damage)
                        .pointsStolen(cost / 2)
                        .shieldAbsorbed(false)
                        .timestamp(LocalDateTime.now())
                        .build();
                fortressRaidLogRepository.save(logEntry);
            } catch (Exception ignored) {}

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
        } catch (Exception e) {
            log.error("attack error: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Attack error: " + e.getMessage()));
        }
    }

    /**
     * POST /api/campus/rebuild
     * Classmates pool points to build locked structures or rebuild damaged/destroyed structures.
     */
    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuildStructure(@RequestBody Map<String, Object> payload) {
        try {
            User user = getCurrentUser();
            Long structureId = Long.valueOf(payload.get("structureId").toString());
            int points = Integer.parseInt(payload.getOrDefault("points", 50).toString());

            if (points <= 0) points = 50;

            CampusStructure structure = campusStructureRepository.findById(structureId).orElse(null);
            if (structure == null) {
                return ResponseEntity.notFound().build();
            }

            if (user.getPrimarySection() == null || !structure.getSection().getId().equals(user.getPrimarySection().getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "You can only build/rebuild structures in your own section!"));
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

                // If building sequentially, unlock the next locked structure!
                List<CampusStructure> allInSec = campusStructureRepository.findBySectionId(structure.getSection().getId());
                allInSec.stream()
                        .filter(s -> "LOCKED".equals(s.getStatus()))
                        .findFirst()
                        .ifPresent(next -> {
                            next.setStatus("BUILDING");
                            campusStructureRepository.save(next);
                        });
            } else {
                structure.setStatus("BUILDING");
                double progress = (double) structure.getRebuildPointsContributed() / structure.getRebuildPointsRequired();
                structure.setHp(Math.max(structure.getHp(), (int) (progress * structure.getMaxHp())));
            }
            campusStructureRepository.save(structure);

            String message = fullyRebuilt
                    ? "✨ COMPLETE! " + structure.getDisplayName() + " is fully constructed and standing tall!"
                    : "🔨 Contributed " + actuallyContributed + " points to " + structure.getDisplayName() + "! (" + structure.getRebuildPointsContributed() + "/" + structure.getRebuildPointsRequired() + " pts)";

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("fullyRebuilt", fullyRebuilt);
            resp.put("structure", structure);
            resp.put("userWarPoints", user.getWarPoints());
            resp.put("message", message);

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("rebuild error: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Rebuild error: " + e.getMessage()));
        }
    }

    /**
     * POST /api/campus/start-fresh
     * Resets current section's campus to Stage 1 sequential building mode
     * so students can build their campus piece-by-piece from blueprints!
     */
    @PostMapping("/start-fresh")
    public ResponseEntity<?> startFreshConstruction() {
        try {
            User user = getCurrentUser();
            Section mySection = user.getPrimarySection();
            if (mySection == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "No primary section assigned"));
            }

            List<CampusStructure> structs = ensureSectionStructures(mySection);
            for (CampusStructure s : structs) {
                switch (s.getStructureKey()) {
                    case "MAIN_HALL":
                        s.setStatus("INTACT");
                        s.setHp(s.getMaxHp());
                        s.setRebuildPointsContributed(0);
                        break;
                    case "LIGHT_POLE_1":
                        s.setStatus("BUILDING");
                        s.setHp(0);
                        s.setRebuildPointsRequired(30);
                        s.setRebuildPointsContributed(0);
                        break;
                    case "LIGHT_POLE_2":
                        s.setStatus("LOCKED");
                        s.setHp(0);
                        s.setRebuildPointsRequired(30);
                        s.setRebuildPointsContributed(0);
                        break;
                    case "CANTEEN":
                        s.setStatus("LOCKED");
                        s.setHp(0);
                        s.setRebuildPointsRequired(80);
                        s.setRebuildPointsContributed(0);
                        break;
                    case "GARDEN":
                        s.setStatus("LOCKED");
                        s.setHp(0);
                        s.setRebuildPointsRequired(60);
                        s.setRebuildPointsContributed(0);
                        break;
                    case "LIBRARY":
                        s.setStatus("LOCKED");
                        s.setHp(0);
                        s.setRebuildPointsRequired(100);
                        s.setRebuildPointsContributed(0);
                        break;
                    case "LASER_TURRET":
                        s.setStatus("LOCKED");
                        s.setHp(0);
                        s.setRebuildPointsRequired(100);
                        s.setRebuildPointsContributed(0);
                        break;
                }
            }
            campusStructureRepository.saveAll(structs);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Campus reset to Stage 1! Chat in your group or contribute points to build each structure piece-by-piece!",
                    "structures", structs
            ));
        } catch (Exception e) {
            log.error("startFresh error: ", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Automatic progression: advance section's active campus construction project by +10 points on each message.
     */
    public static CampusStructure advanceSectionCampusConstruction(Long sectionId, int points, CampusStructureRepository repo) {
        try {
            List<CampusStructure> structs = repo.findBySectionId(sectionId);
            if (structs.isEmpty()) return null;

            // 1. Repair destroyed/damaged structures first
            CampusStructure target = structs.stream()
                    .filter(s -> "DESTROYED".equals(s.getStatus()) || "DAMAGED".equals(s.getStatus()))
                    .findFirst()
                    .orElse(null);

            // 2. Otherwise advance next building under construction or locked
            if (target == null) {
                target = structs.stream()
                        .filter(s -> "BUILDING".equals(s.getStatus()))
                        .findFirst()
                        .orElseGet(() -> structs.stream()
                                .filter(s -> "LOCKED".equals(s.getStatus()))
                                .findFirst()
                                .orElse(null));
            }

            if (target != null) {
                if ("LOCKED".equals(target.getStatus())) {
                    target.setStatus("BUILDING");
                }
                int newContributed = target.getRebuildPointsContributed() + points;
                target.setRebuildPointsContributed(newContributed);

                if (newContributed >= target.getRebuildPointsRequired()) {
                    target.setStatus("INTACT");
                    target.setHp(target.getMaxHp());
                    target.setRebuildPointsContributed(0);

                    // Unlock next
                    structs.stream()
                            .filter(s -> "LOCKED".equals(s.getStatus()))
                            .findFirst()
                            .ifPresent(next -> {
                                next.setStatus("BUILDING");
                                repo.save(next);
                            });
                } else {
                    double progress = (double) newContributed / target.getRebuildPointsRequired();
                    target.setHp(Math.max(target.getHp(), (int) (progress * target.getMaxHp())));
                }
                return repo.save(target);
            }
        } catch (Exception e) {
            log.error("advanceSectionCampusConstruction error: {}", e.getMessage());
        }
        return null;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        return userRepository.findById(userDetails.getId()).orElseThrow();
    }

    private CampusStructure findActiveProject(List<CampusStructure> structs) {
        return structs.stream()
                .filter(s -> "BUILDING".equals(s.getStatus()) || "DESTROYED".equals(s.getStatus()) || "DAMAGED".equals(s.getStatus()))
                .findFirst()
                .orElseGet(() -> structs.stream()
                        .filter(s -> "LOCKED".equals(s.getStatus()))
                        .findFirst()
                        .orElse(null));
    }

    /**
     * Initializes default sequential campus structures:
     * Stage 1: Main Hall (INTACT)
     * Stage 2: North Light Pole (BUILDING)
     * Stage 3: South Light Pole (LOCKED)
     * Stage 4: Canteen (LOCKED)
     * Stage 5: Garden (LOCKED)
     * Stage 6: Library (LOCKED)
     * Stage 7: Laser Turret (LOCKED)
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
                        .status("INTACT") // Starts completed as the core campus center
                        .rebuildPointsRequired(100)
                        .rebuildPointsContributed(0)
                        .coordX(0.0).coordY(0.0).coordZ(0.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("LIGHT_POLE_1")
                        .displayName("North Light Pole")
                        .hp(40)
                        .maxHp(40)
                        .status("BUILDING") // Current active project (30 pts to complete!)
                        .rebuildPointsRequired(30)
                        .rebuildPointsContributed(0)
                        .coordX(-80.0).coordY(0.0).coordZ(90.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("LIGHT_POLE_2")
                        .displayName("South Light Pole")
                        .hp(40)
                        .maxHp(40)
                        .status("LOCKED")
                        .rebuildPointsRequired(30)
                        .rebuildPointsContributed(0)
                        .coordX(80.0).coordY(0.0).coordZ(90.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("CANTEEN")
                        .displayName("Campus Canteen & Cafe")
                        .hp(100)
                        .maxHp(100)
                        .status("LOCKED")
                        .rebuildPointsRequired(80)
                        .rebuildPointsContributed(0)
                        .coordX(-140.0).coordY(0.0).coordZ(-70.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("GARDEN")
                        .displayName("Campus Garden & Quad")
                        .hp(60)
                        .maxHp(60)
                        .status("LOCKED")
                        .rebuildPointsRequired(60)
                        .rebuildPointsContributed(0)
                        .coordX(0.0).coordY(0.0).coordZ(120.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("LIBRARY")
                        .displayName("Tech Library & Clock Tower")
                        .hp(120)
                        .maxHp(120)
                        .status("LOCKED")
                        .rebuildPointsRequired(100)
                        .rebuildPointsContributed(0)
                        .coordX(140.0).coordY(0.0).coordZ(-70.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("LASER_TURRET")
                        .displayName("Defense Laser Turret")
                        .hp(80)
                        .maxHp(80)
                        .status("LOCKED")
                        .rebuildPointsRequired(100)
                        .rebuildPointsContributed(0)
                        .coordX(0.0).coordY(0.0).coordZ(-140.0)
                        .build()
        );

        return campusStructureRepository.saveAll(defaults);
    }
}

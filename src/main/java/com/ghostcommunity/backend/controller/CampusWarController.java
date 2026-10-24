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
     * Returns current user's war points and their section's own full 3D campus territory.
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

            CampusStructure currentProject = findActiveProject(structures);

            String collegeName = "Campus";
            try {
                if (mySection.getCollege() != null) {
                    collegeName = mySection.getCollege().getName();
                }
            } catch (Exception ignored) {}

            int msgCount = mySection.getMessageCount();
            int msgsUntilNextBrick = 5 - (msgCount % 5);
            if (msgsUntilNextBrick == 0) msgsUntilNextBrick = 5;

            Map<String, Object> response = new HashMap<>();
            response.put("sectionId", mySection.getId());
            response.put("sectionName", mySection.getName());
            response.put("collegeName", collegeName);
            response.put("userWarPoints", user.getWarPoints());
            response.put("sectionMessageCount", msgCount);
            response.put("messagesUntilNextBrick", msgsUntilNextBrick);
            response.put("totalBricks", mySection.getTotalBricks());
            response.put("campusLevel", mySection.getCampusLevel());
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
     * List all rival sections in the same college with their individual campus status.
     * Each section has its own full campus map!
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
                int totalHp = structs.stream().mapToInt(CampusStructure::getHp).sum();
                int maxHp = structs.stream().mapToInt(CampusStructure::getMaxHp).sum();

                Map<String, Object> item = new HashMap<>();
                item.put("sectionId", sec.getId());
                item.put("sectionName", sec.getName());
                item.put("isMySection", sec.getId().equals(mySecId));
                item.put("campusLevel", sec.getCampusLevel());
                item.put("totalBricks", sec.getTotalBricks());
                item.put("messageCount", sec.getMessageCount());
                item.put("totalStructures", structs.size());
                item.put("intactCount", intact);
                item.put("damagedCount", damaged);
                item.put("destroyedCount", destroyed);
                item.put("totalHp", totalHp);
                item.put("maxHp", maxHp);
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
     * Inspect a specific section's own full 3D campus territory to view or attack.
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

            int msgCount = sec.getMessageCount();
            int msgsUntilNextBrick = 5 - (msgCount % 5);
            if (msgsUntilNextBrick == 0) msgsUntilNextBrick = 5;

            Map<String, Object> response = new HashMap<>();
            response.put("sectionId", sec.getId());
            response.put("sectionName", sec.getName());
            response.put("isMySection", user.getPrimarySection() != null && user.getPrimarySection().getId().equals(sec.getId()));
            response.put("userWarPoints", user.getWarPoints());
            response.put("campusLevel", sec.getCampusLevel());
            response.put("totalBricks", sec.getTotalBricks());
            response.put("sectionMessageCount", msgCount);
            response.put("messagesUntilNextBrick", msgsUntilNextBrick);
            response.put("structures", structures);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("viewSectionCampus error: ", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/campus/attack
     * Launch gun, small missile, or ICBM at a rival campus's specific building.
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
                structure.setBricksPlaced(0);
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
     * Classmates can manually contribute 50 War Points to place 1 brick!
     */
    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuildStructure(@RequestBody Map<String, Object> payload) {
        try {
            User user = getCurrentUser();
            Long structureId = Long.valueOf(payload.get("structureId").toString());
            int points = Integer.parseInt(payload.getOrDefault("points", 50).toString());

            CampusStructure structure = campusStructureRepository.findById(structureId).orElse(null);
            if (structure == null) {
                return ResponseEntity.notFound().build();
            }

            if (user.getPrimarySection() == null || !structure.getSection().getId().equals(user.getPrimarySection().getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "You can only build/rebuild structures in your own section!"));
            }

            if ("INTACT".equals(structure.getStatus()) && structure.getHp() == structure.getMaxHp()) {
                return ResponseEntity.badRequest().body(Map.of("error", structure.getDisplayName() + " is already 100% complete and intact!"));
            }

            if (user.getWarPoints() < 50) {
                return ResponseEntity.badRequest().body(Map.of("error", "You need at least 50 War Points to place a brick! Chat in section (+10 pts per msg)."));
            }

            // Deduct 50 War points and place 1 brick!
            user.setWarPoints(user.getWarPoints() - 50);
            userRepository.save(user);

            Section mySec = structure.getSection();
            mySec.setTotalBricks(mySec.getTotalBricks() + 1);
            sectionRepository.save(mySec);

            advanceSectionBrickConstruction(mySec.getId(), 1, campusStructureRepository, sectionRepository);

            CampusStructure updated = campusStructureRepository.findById(structureId).orElse(structure);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("structure", updated);
            resp.put("userWarPoints", user.getWarPoints());
            resp.put("totalBricks", mySec.getTotalBricks());
            resp.put("message", "🧱 Placed 1 brick on " + updated.getDisplayName() + "! (" + updated.getBricksPlaced() + "/" + updated.getBricksRequired() + " bricks)");

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("rebuild error: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Rebuild error: " + e.getMessage()));
        }
    }

    /**
     * POST /api/campus/start-fresh
     * Resets current section's campus to Stage 1 foundation:
     * - Main Academic Citadel starts in BUILDING mode (with scaffolding & crane, 0/10 bricks)
     * - All other structures LOCKED
     * - Message counter & brick counter reset
     * Students can now watch their campus construct brick-by-brick every 5 messages!
     */
    @PostMapping("/start-fresh")
    public ResponseEntity<?> startFreshConstruction() {
        try {
            User user = getCurrentUser();
            Section mySection = user.getPrimarySection();
            if (mySection == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "No primary section assigned"));
            }

            mySection.setMessageCount(0);
            mySection.setTotalBricks(0);
            mySection.setCampusLevel(1);
            sectionRepository.save(mySection);

            List<CampusStructure> structs = ensureSectionStructures(mySection);
            for (CampusStructure s : structs) {
                switch (s.getStructureKey()) {
                    case "MAIN_HALL":
                        s.setStatus("BUILDING");
                        s.setHp(20);
                        s.setBricksRequired(10);
                        s.setBricksPlaced(0);
                        s.setRebuildPointsRequired(100);
                        s.setRebuildPointsContributed(0);
                        break;
                    case "LIGHT_POLE_1":
                        s.setStatus("LOCKED");
                        s.setHp(0);
                        s.setBricksRequired(5);
                        s.setBricksPlaced(0);
                        s.setRebuildPointsRequired(30);
                        s.setRebuildPointsContributed(0);
                        break;
                    case "LIGHT_POLE_2":
                        s.setStatus("LOCKED");
                        s.setHp(0);
                        s.setBricksRequired(5);
                        s.setBricksPlaced(0);
                        s.setRebuildPointsRequired(30);
                        s.setRebuildPointsContributed(0);
                        break;
                    case "CANTEEN":
                        s.setStatus("LOCKED");
                        s.setHp(0);
                        s.setBricksRequired(8);
                        s.setBricksPlaced(0);
                        s.setRebuildPointsRequired(80);
                        s.setRebuildPointsContributed(0);
                        break;
                    case "GARDEN":
                        s.setStatus("LOCKED");
                        s.setHp(0);
                        s.setBricksRequired(6);
                        s.setBricksPlaced(0);
                        s.setRebuildPointsRequired(60);
                        s.setRebuildPointsContributed(0);
                        break;
                    case "LIBRARY":
                        s.setStatus("LOCKED");
                        s.setHp(0);
                        s.setBricksRequired(10);
                        s.setBricksPlaced(0);
                        s.setRebuildPointsRequired(100);
                        s.setRebuildPointsContributed(0);
                        break;
                    case "LASER_TURRET":
                        s.setStatus("LOCKED");
                        s.setHp(0);
                        s.setBricksRequired(10);
                        s.setBricksPlaced(0);
                        s.setRebuildPointsRequired(100);
                        s.setRebuildPointsContributed(0);
                        break;
                }
            }
            campusStructureRepository.saveAll(structs);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Campus reset to Stage 1 Foundation! Every 5 messages in section will lay 1 brick on the scaffolding!",
                    "structures", structs,
                    "sectionMessageCount", 0,
                    "messagesUntilNextBrick", 5,
                    "totalBricks", 0
            ));
        } catch (Exception e) {
            log.error("startFresh error: ", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Advances section's active campus construction by adding bricks.
     * Triggered every 5 messages or when classmates contribute points.
     */
    public static CampusStructure advanceSectionBrickConstruction(
            Long sectionId,
            int bricksToAdd,
            CampusStructureRepository repo,
            SectionRepository secRepo
    ) {
        try {
            List<CampusStructure> structs = repo.findBySectionId(sectionId);
            if (structs.isEmpty()) return null;

            // 1. Repair destroyed or damaged structures first
            CampusStructure target = structs.stream()
                    .filter(s -> "DESTROYED".equals(s.getStatus()) || "DAMAGED".equals(s.getStatus()))
                    .findFirst()
                    .orElse(null);

            // 2. Otherwise advance currently BUILDING structure
            if (target == null) {
                target = structs.stream()
                        .filter(s -> "BUILDING".equals(s.getStatus()))
                        .findFirst()
                        .orElse(null);
            }

            // 3. Otherwise activate next LOCKED structure
            if (target == null) {
                target = structs.stream()
                        .filter(s -> "LOCKED".equals(s.getStatus()))
                        .findFirst()
                        .orElse(null);
                if (target != null) {
                    target.setStatus("BUILDING");
                    target.setBricksPlaced(0);
                }
            }

            if (target != null) {
                int req = target.getBricksRequired();
                int currentPlaced = target.getBricksPlaced() + bricksToAdd;
                target.setBricksPlaced(currentPlaced);

                if (currentPlaced >= req) {
                    // Completed!
                    target.setStatus("INTACT");
                    target.setHp(target.getMaxHp());
                    target.setBricksPlaced(req);
                    target.setRebuildPointsContributed(0);

                    // Unlock next locked structure for construction
                    final Long targetId = target.getId();
                    CampusStructure nextLocked = structs.stream()
                            .filter(s -> !s.getId().equals(targetId) && "LOCKED".equals(s.getStatus()))
                            .findFirst()
                            .orElse(null);

                    if (nextLocked != null) {
                        nextLocked.setStatus("BUILDING");
                        nextLocked.setBricksPlaced(0);
                        repo.save(nextLocked);
                    } else {
                        // All structures completed! Level up campus!
                        if (secRepo != null) {
                            secRepo.findById(sectionId).ifPresent(sec -> {
                                sec.setCampusLevel(sec.getCampusLevel() + 1);
                                secRepo.save(sec);
                            });
                        }
                    }
                } else {
                    target.setStatus("BUILDING");
                    double progress = (double) currentPlaced / req;
                    target.setHp(Math.max(10, (int) (progress * target.getMaxHp())));
                }
                return repo.save(target);
            }
        } catch (Exception e) {
            log.error("advanceSectionBrickConstruction error: {}", e.getMessage());
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
     * Stage 1: Main Hall (BUILDING / foundation)
     * Stage 2: North Light Pole (LOCKED)
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
                        .displayName("Main Academic Citadel")
                        .hp(200)
                        .maxHp(200)
                        .status("INTACT")
                        .bricksRequired(10)
                        .bricksPlaced(10)
                        .rebuildPointsRequired(100)
                        .rebuildPointsContributed(0)
                        .coordX(0.0).coordY(0.0).coordZ(0.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("LIGHT_POLE_1")
                        .displayName("North Cyber Light Pole")
                        .hp(40)
                        .maxHp(40)
                        .status("BUILDING")
                        .bricksRequired(5)
                        .bricksPlaced(1)
                        .rebuildPointsRequired(30)
                        .rebuildPointsContributed(0)
                        .coordX(-80.0).coordY(0.0).coordZ(90.0)
                        .build(),

                CampusStructure.builder()
                        .section(section)
                        .structureKey("LIGHT_POLE_2")
                        .displayName("South Cyber Light Pole")
                        .hp(40)
                        .maxHp(40)
                        .status("LOCKED")
                        .bricksRequired(5)
                        .bricksPlaced(0)
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
                        .bricksRequired(8)
                        .bricksPlaced(0)
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
                        .bricksRequired(6)
                        .bricksPlaced(0)
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
                        .bricksRequired(10)
                        .bricksPlaced(0)
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
                        .bricksRequired(10)
                        .bricksPlaced(0)
                        .rebuildPointsRequired(100)
                        .rebuildPointsContributed(0)
                        .coordX(0.0).coordY(0.0).coordZ(-140.0)
                        .build()
        );

        return campusStructureRepository.saveAll(defaults);
    }
}

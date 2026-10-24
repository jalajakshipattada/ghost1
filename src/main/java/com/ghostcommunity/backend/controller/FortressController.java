package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.model.*;
import com.ghostcommunity.backend.repository.*;
import com.ghostcommunity.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/fortress")
@RequiredArgsConstructor
public class FortressController {

    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;
    private final FortressRepository fortressRepository;
    private final FortressRaidLogRepository raidLogRepository;

    private Fortress getOrCreateFortress(Section section) {
        return fortressRepository.findBySectionId(section.getId())
                .orElseGet(() -> {
                    Fortress newFort = Fortress.builder()
                            .section(section)
                            .name(section.getName() + " Fortress")
                            .level(1)
                            .hp(1000)
                            .maxHp(1000)
                            .shields(2)
                            .maxShields(3)
                            .bricks(300)
                            .energy(100)
                            .maxEnergy(100)
                            .autoTurretsActive(true)
                            .points(500)
                            .build();
                    return fortressRepository.save(newFort);
                });
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyFortress(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Section mySection = user.getPrimarySection();
        if (mySection == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No primary section assigned"));
        }

        Fortress myFort = getOrCreateFortress(mySection);

        List<FortressRaidLog> logs = raidLogRepository
                .findTop20ByTargetSectionIdOrAttackerSectionIdOrderByTimestampDesc(mySection.getId(), mySection.getId());

        List<Map<String, Object>> logList = new ArrayList<>();
        for (FortressRaidLog log : logs) {
            boolean isIncoming = log.getTargetSection() != null && log.getTargetSection().getId().equals(mySection.getId());
            Map<String, Object> logMap = new HashMap<>();
            logMap.put("id", log.getId());
            logMap.put("isIncoming", isIncoming);
            logMap.put("attackerSectionName", log.getAttackerSection() != null ? log.getAttackerSection().getName() : "Unknown");
            logMap.put("targetSectionName", log.getTargetSection() != null ? log.getTargetSection().getName() : "Unknown");
            logMap.put("weapon", log.getWeapon());
            logMap.put("damageDealt", log.getDamageDealt());
            logMap.put("pointsStolen", log.getPointsStolen());
            logMap.put("shieldAbsorbed", log.isShieldAbsorbed());
            logMap.put("timestamp", log.getTimestamp() != null ? log.getTimestamp().toString() : "");
            logList.add(logMap);
        }

        Map<String, Object> fortMap = new HashMap<>();
        fortMap.put("id", myFort.getId());
        fortMap.put("sectionId", mySection.getId());
        fortMap.put("sectionName", mySection.getName());
        fortMap.put("name", myFort.getName());
        fortMap.put("level", myFort.getLevel());
        fortMap.put("hp", myFort.getHp());
        fortMap.put("maxHp", myFort.getMaxHp());
        fortMap.put("shields", myFort.getShields());
        fortMap.put("maxShields", myFort.getMaxShields());
        fortMap.put("bricks", myFort.getBricks());
        fortMap.put("energy", myFort.getEnergy());
        fortMap.put("maxEnergy", myFort.getMaxEnergy());
        fortMap.put("autoTurretsActive", myFort.isAutoTurretsActive());
        fortMap.put("points", myFort.getPoints());
        fortMap.put("lastAttackedAt", myFort.getLastAttackedAt() != null ? myFort.getLastAttackedAt().toString() : "");

        Map<String, Object> response = new HashMap<>();
        response.put("fortress", fortMap);
        response.put("logs", logList);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/targets")
    public ResponseEntity<?> getRivalFortresses(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Section mySection = user.getPrimarySection();
        Long mySectionId = mySection != null ? mySection.getId() : -1L;

        List<Section> allSections = (user.getCollege() != null)
                ? sectionRepository.findByCollegeId(user.getCollege().getId())
                : sectionRepository.findAll();

        List<Map<String, Object>> targets = new ArrayList<>();
        for (Section sec : allSections) {
            if (sec.getId().equals(mySectionId)) continue; // Don't raid self
            Fortress f = getOrCreateFortress(sec);
            targets.add(Map.of(
                    "id", f.getId(),
                    "sectionId", sec.getId(),
                    "sectionName", sec.getName(),
                    "name", f.getName(),
                    "level", f.getLevel(),
                    "hp", f.getHp(),
                    "maxHp", f.getMaxHp(),
                    "shields", f.getShields(),
                    "points", f.getPoints()
            ));
        }

        return ResponseEntity.ok(targets);
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<?> getLeaderboard(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Section> allSections = (user.getCollege() != null)
                ? sectionRepository.findByCollegeId(user.getCollege().getId())
                : sectionRepository.findAll();

        List<Map<String, Object>> rankList = new ArrayList<>();
        for (Section sec : allSections) {
            Fortress f = getOrCreateFortress(sec);
            Map<String, Object> item = new HashMap<>();
            item.put("sectionId", sec.getId());
            item.put("sectionName", sec.getName());
            item.put("level", f.getLevel());
            item.put("points", f.getPoints());
            item.put("hp", f.getHp());
            item.put("maxHp", f.getMaxHp());
            rankList.add(item);
        }

        rankList.sort((a, b) -> Integer.compare((int) b.get("points"), (int) a.get("points")));

        for (int i = 0; i < rankList.size(); i++) {
            rankList.get(i).put("rank", i + 1);
        }

        return ResponseEntity.ok(rankList);
    }

    @PostMapping("/attack/{targetSectionId}")
    public ResponseEntity<?> attackFortress(
            @PathVariable Long targetSectionId,
            @RequestParam(defaultValue = "MISSILE") String weapon,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Section mySection = user.getPrimarySection();
        if (mySection == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No primary section assigned"));
        }

        if (mySection.getId().equals(targetSectionId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot attack your own section fortress!"));
        }

        Section targetSection = sectionRepository.findById(targetSectionId)
                .orElseThrow(() -> new RuntimeException("Target section not found"));

        Fortress myFort = getOrCreateFortress(mySection);
        Fortress targetFort = getOrCreateFortress(targetSection);

        // Energy requirement
        int energyCost = 50;
        int baseDamage = 160;
        int baseSteal = 150;

        if ("TROJAN_EMP".equalsIgnoreCase(weapon) || "EMP_SURGE".equalsIgnoreCase(weapon)) {
            energyCost = 80;
            baseDamage = 220;
            baseSteal = 200;
        } else if ("AIR_STRIKE".equalsIgnoreCase(weapon) || "CYBER_ORBITAL".equalsIgnoreCase(weapon)) {
            energyCost = 100;
            baseDamage = 280;
            baseSteal = 250;
        }

        if (myFort.getEnergy() >= energyCost) {
            myFort.setEnergy(myFort.getEnergy() - energyCost);
        } else {
            myFort.setEnergy(Math.max(0, myFort.getEnergy() - 20));
        }

        boolean shieldAbsorbed = false;
        int damageDealt = 0;
        int pointsStolen = 0;

        if (targetFort.getShields() > 0) {
            targetFort.setShields(targetFort.getShields() - 1);
            shieldAbsorbed = true;
        } else {
            int variance = new Random().nextInt(40);
            damageDealt = baseDamage + variance;
            pointsStolen = Math.min(targetFort.getPoints(), baseSteal + (variance / 2));

            targetFort.setHp(Math.max(0, targetFort.getHp() - damageDealt));
            targetFort.setPoints(Math.max(0, targetFort.getPoints() - pointsStolen));
            myFort.setPoints(myFort.getPoints() + pointsStolen);
        }

        targetFort.setLastAttackedAt(LocalDateTime.now());
        fortressRepository.save(targetFort);
        fortressRepository.save(myFort);

        // Record raid log
        FortressRaidLog raidLog = FortressRaidLog.builder()
                .attacker(user)
                .attackerSection(mySection)
                .targetSection(targetSection)
                .weapon(weapon.toUpperCase())
                .damageDealt(damageDealt)
                .pointsStolen(pointsStolen)
                .shieldAbsorbed(shieldAbsorbed)
                .timestamp(LocalDateTime.now())
                .build();
        raidLogRepository.save(raidLog);

        Map<String, Object> result = new HashMap<>();
        result.put("shieldAbsorbed", shieldAbsorbed);
        result.put("damageDealt", damageDealt);
        result.put("pointsStolen", pointsStolen);
        result.put("targetSectionName", targetSection.getName());
        result.put("targetOldHp", targetFort.getHp() + damageDealt);
        result.put("targetRemainingHp", targetFort.getHp());
        result.put("targetRemainingShields", targetFort.getShields());
        result.put("myVaultPoints", myFort.getPoints());
        result.put("myRemainingEnergy", myFort.getEnergy());
        result.put("message", shieldAbsorbed
                ? "Enemy Cyber Shield absorbed your strike! (Shield -1)"
                : "Hacker Missile hit! Dealt " + damageDealt + " damage & stole " + pointsStolen + " points for " + mySection.getName() + "!");

        return ResponseEntity.ok(result);
    }

    @PostMapping("/donate")
    public ResponseEntity<?> donateBricksAndEnergy(
            @RequestParam(defaultValue = "50") int bricks,
            @RequestParam(defaultValue = "20") int energy,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Section mySection = user.getPrimarySection();
        Fortress myFort = getOrCreateFortress(mySection);

        myFort.setBricks(myFort.getBricks() + bricks);
        myFort.setEnergy(Math.min(myFort.getMaxEnergy(), myFort.getEnergy() + energy));
        fortressRepository.save(myFort);

        return ResponseEntity.ok(Map.of(
                "message", "Donated " + bricks + " Bricks & " + energy + " Energy to " + mySection.getName() + "!",
                "bricks", myFort.getBricks(),
                "energy", myFort.getEnergy()
        ));
    }

    @PostMapping("/repair")
    public ResponseEntity<?> repairFortress(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Section mySection = user.getPrimarySection();
        Fortress myFort = getOrCreateFortress(mySection);

        if (myFort.getHp() >= myFort.getMaxHp()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fortress is already at maximum health (1000 HP)!"));
        }

        myFort.setHp(myFort.getMaxHp());
        myFort.setBricks(Math.max(0, myFort.getBricks() - 40));
        fortressRepository.save(myFort);

        return ResponseEntity.ok(Map.of(
                "message", "Emergency Repair complete! Health restored to 100% (1,000 HP) 🔧",
                "hp", myFort.getHp(),
                "bricks", myFort.getBricks()
        ));
    }

    @PostMapping("/deploy-shield")
    public ResponseEntity<?> deployShield(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Section mySection = user.getPrimarySection();
        Fortress myFort = getOrCreateFortress(mySection);

        if (myFort.getShields() >= myFort.getMaxShields()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Shield capacity already full (3/3)!"));
        }

        myFort.setShields(myFort.getShields() + 1);
        myFort.setBricks(Math.max(0, myFort.getBricks() - 50));
        fortressRepository.save(myFort);

        return ResponseEntity.ok(Map.of(
                "message", "Cyber Shield activated! (Shields: " + myFort.getShields() + "/" + myFort.getMaxShields() + ")",
                "shields", myFort.getShields(),
                "bricks", myFort.getBricks()
        ));
    }

    @PostMapping("/mine-bricks")
    public ResponseEntity<?> mineBricks(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Section mySection = user.getPrimarySection();
        Fortress myFort = getOrCreateFortress(mySection);

        myFort.setBricks(myFort.getBricks() + 60);
        myFort.setEnergy(Math.min(myFort.getMaxEnergy(), myFort.getEnergy() + 25));
        fortressRepository.save(myFort);

        return ResponseEntity.ok(Map.of(
                "message", "Mined 60 Cyber Bricks & recharged +25 Energy!",
                "bricks", myFort.getBricks(),
                "energy", myFort.getEnergy()
        ));
    }

    @PostMapping("/upgrade")
    public ResponseEntity<?> upgradeFortress(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Section mySection = user.getPrimarySection();
        Fortress myFort = getOrCreateFortress(mySection);

        myFort.setLevel(myFort.getLevel() + 1);
        myFort.setMaxHp(myFort.getMaxHp() + 250);
        myFort.setHp(myFort.getMaxHp());
        myFort.setPoints(myFort.getPoints() + 500);
        fortressRepository.save(myFort);

        return ResponseEntity.ok(Map.of(
                "message", "FORTRESS UPGRADED to Level " + myFort.getLevel() + "! Sky Citadel Unlocked! ⚡",
                "level", myFort.getLevel(),
                "maxHp", myFort.getMaxHp(),
                "hp", myFort.getHp(),
                "bricks", myFort.getBricks(),
                "points", myFort.getPoints()
        ));
    }
}

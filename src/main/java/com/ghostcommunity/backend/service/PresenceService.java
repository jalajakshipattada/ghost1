package com.ghostcommunity.backend.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {

    // userId -> lastActive timestamp
    private final Map<Long, Instant> userLastSeen = new ConcurrentHashMap<>();

    // userId -> primarySectionId
    private final Map<Long, Long> userSection = new ConcurrentHashMap<>();

    // User is considered active if seen within the last 120 seconds (2 minutes)
    private static final long ONLINE_THRESHOLD_SECONDS = 120;

    public void updateActivity(Long userId, Long sectionId) {
        if (userId == null) return;
        userLastSeen.put(userId, Instant.now());
        if (sectionId != null) {
            userSection.put(userId, sectionId);
        }
    }

    public void removeUser(Long userId) {
        if (userId != null) {
            userLastSeen.remove(userId);
            userSection.remove(userId);
        }
    }

    public long getOnlineCountForSection(Long sectionId) {
        if (sectionId == null) return 0;
        Instant cutoff = Instant.now().minusSeconds(ONLINE_THRESHOLD_SECONDS);
        return userLastSeen.entrySet().stream()
                .filter(entry -> entry.getValue().isAfter(cutoff))
                .filter(entry -> {
                    Long sId = userSection.get(entry.getKey());
                    return sId != null && sId.equals(sectionId);
                })
                .count();
    }
}

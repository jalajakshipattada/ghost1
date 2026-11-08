package com.ghostcommunity.backend.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class RallyPayload {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Telemetry {
        private String playerId;
        private String playerName;
        private String branch;
        private Long collegeId;
        private String roomId;
        private float x;
        private float y;
        private float z;
        private float rotY;
        private float speed;
        private boolean nitro;
        private boolean drift;
        private float progress; // 0.0 to 1.0 along track
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JoinRequest {
        private String playerId;
        private String playerName;
        private String branch;
        private Long collegeId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerInfo {
        private String playerId;
        private String playerName;
        private String branch;
        private int gridSlot; // 1 to 10
        private boolean ready;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoomState {
        private String roomId;
        private Long collegeId;
        private String status; // WAITING, COUNTDOWN, RACING, FINISHED
        private int countdown; // 5, 4, 3, 2, 1, 0
        private int playerCount;
        private List<PlayerInfo> players;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FinishEvent {
        private String playerId;
        private String playerName;
        private String branch;
        private Long collegeId;
        private String roomId;
        private int rank;
        private long lapTimeMs;
        private String formattedTime;
        private int pointsAwarded;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BranchStanding {
        private String branch; // e.g. "CSE-1"
        private int points; // cumulative points
        private int percentage; // progress to 100
        private int wins;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LeaderboardResponse {
        private Long collegeId;
        private List<BranchStanding> standings;
        private String leadingBranch;
        private int targetPoints; // 100
        private List<FinishEvent> recentFinishes;
    }
}

package com.ghostcommunity.backend.controller;

import com.ghostcommunity.backend.payload.RallyPayload.*;
import com.ghostcommunity.backend.service.RallyMatchmakingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class RallyGameController {

    private final RallyMatchmakingService matchmakingService;

    // --- STOMP WebSocket Mappings ---

    @MessageMapping("/rally/join")
    public void onPlayerJoin(@Payload JoinRequest request) {
        log.info("[Rally WS] Join request from {} ({})", request.getPlayerName(), request.getBranch());
        matchmakingService.joinOrCreateRoom(request);
    }

    @MessageMapping("/rally/telemetry")
    public void onPlayerTelemetry(@Payload Telemetry telemetry) {
        matchmakingService.handleTelemetry(telemetry);
    }

    @MessageMapping("/rally/finish")
    public void onPlayerFinish(@Payload FinishEvent finishEvent) {
        log.info("[Rally WS] Finish event for {} (Rank: {})", finishEvent.getPlayerName(), finishEvent.getRank());
        matchmakingService.recordFinish(finishEvent);
    }

    // --- REST API Endpoints ---

    @GetMapping("/api/rally/leaderboard/{collegeId}")
    public ResponseEntity<LeaderboardResponse> getLeaderboard(@PathVariable Long collegeId) {
        LeaderboardResponse lb = matchmakingService.getLeaderboard(collegeId);
        return ResponseEntity.ok(lb);
    }

    @PostMapping("/api/rally/join")
    public ResponseEntity<RoomState> restJoinRoom(@RequestBody JoinRequest request) {
        RoomState room = matchmakingService.joinOrCreateRoom(request);
        return ResponseEntity.ok(room);
    }

    @PostMapping("/api/rally/finish")
    public ResponseEntity<FinishEvent> restFinishRace(@RequestBody FinishEvent finishEvent) {
        FinishEvent result = matchmakingService.recordFinish(finishEvent);
        return ResponseEntity.ok(result);
    }
}

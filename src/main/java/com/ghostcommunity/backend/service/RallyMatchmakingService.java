package com.ghostcommunity.backend.service;

import com.ghostcommunity.backend.model.Section;
import com.ghostcommunity.backend.model.User;
import com.ghostcommunity.backend.payload.RallyPayload.*;
import com.ghostcommunity.backend.repository.SectionRepository;
import com.ghostcommunity.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RallyMatchmakingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final SectionRepository sectionRepository;
    private final UserRepository userRepository;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger roomCounter = new AtomicInteger(100);

    // CollegeId -> (RoomId -> RoomStateInternal)
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, RoomStateInternal>> collegeRooms = new ConcurrentHashMap<>();

    // CollegeId -> (BranchName -> Points)
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, AtomicInteger>> collegeBranchPoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, AtomicInteger>> collegeBranchWins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<FinishEvent>> recentFinishes = new ConcurrentHashMap<>();

    private static final int MIN_PLAYERS_TO_START = 2;
    private static final int MAX_PLAYERS_PER_ROOM = 10;
    private static final int COUNTDOWN_SECONDS = 5;

    private static class RoomStateInternal {
        String roomId;
        Long collegeId;
        String status = "WAITING"; // WAITING, COUNTDOWN, RACING, FINISHED
        int countdown = COUNTDOWN_SECONDS;
        ConcurrentHashMap<String, PlayerInfo> players = new ConcurrentHashMap<>();
        List<FinishEvent> finishes = new CopyOnWriteArrayList<>();
        ScheduledFuture<?> countdownTask = null;
    }

    @PostConstruct
    public void init() {
        log.info("[RallyMatchmakingService] Initialized real-time multiplayer rally service.");
    }

    public synchronized RoomState joinOrCreateRoom(JoinRequest req) {
        Long collegeId = req.getCollegeId() != null ? req.getCollegeId() : 1L;
        collegeRooms.putIfAbsent(collegeId, new ConcurrentHashMap<>());
        var rooms = collegeRooms.get(collegeId);

        // 1. Search for an available open room (WAITING or COUNTDOWN with < 10 players)
        RoomStateInternal targetRoom = null;
        for (RoomStateInternal r : rooms.values()) {
            if ((r.status.equals("WAITING") || r.status.equals("COUNTDOWN")) && r.players.size() < MAX_PLAYERS_PER_ROOM) {
                targetRoom = r;
                break;
            }
        }

        // 2. If all existing rooms are RACING or full, dynamically create a new room!
        if (targetRoom == null) {
            String newRoomId = "col_" + collegeId + "_r" + roomCounter.incrementAndGet();
            targetRoom = new RoomStateInternal();
            targetRoom.roomId = newRoomId;
            targetRoom.collegeId = collegeId;
            rooms.put(newRoomId, targetRoom);
            log.info("[Rally] Created dynamic new room {} for college {}", newRoomId, collegeId);
        }

        // 3. Add player to the room
        int nextGridSlot = targetRoom.players.size() + 1;
        PlayerInfo pInfo = PlayerInfo.builder()
                .playerId(req.getPlayerId())
                .playerName(req.getPlayerName() != null ? req.getPlayerName() : "GhostRacer")
                .branch(req.getBranch() != null ? req.getBranch() : "CSE-1")
                .gridSlot(nextGridSlot)
                .ready(true)
                .build();
        targetRoom.players.put(req.getPlayerId(), pInfo);

        log.info("[Rally] Player {} ({}) joined room {}. Total players: {}",
                pInfo.getPlayerName(), pInfo.getBranch(), targetRoom.roomId, targetRoom.players.size());

        // 4. Check minimum 2 players rule
        if (targetRoom.players.size() >= MIN_PLAYERS_TO_START && targetRoom.status.equals("WAITING")) {
            startCountdown(targetRoom);
        }

        RoomState state = buildRoomState(targetRoom);
        broadcastRoomState(targetRoom);
        return state;
    }

    private void startCountdown(RoomStateInternal room) {
        room.status = "COUNTDOWN";
        room.countdown = COUNTDOWN_SECONDS;

        if (room.countdownTask != null && !room.countdownTask.isDone()) {
            room.countdownTask.cancel(true);
        }

        room.countdownTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (room.countdown > 0) {
                    room.countdown--;
                    broadcastRoomState(room);
                } else {
                    // Launch Race!
                    room.status = "RACING";
                    broadcastRoomState(room);
                    if (room.countdownTask != null) room.countdownTask.cancel(false);
                    log.info("[Rally] Race launched in room {} with {} players!", room.roomId, room.players.size());
                }
            } catch (Exception e) {
                log.error("[Rally] Error during countdown tick", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void handleTelemetry(Telemetry telemetry) {
        if (telemetry == null || telemetry.getRoomId() == null) return;
        Long collegeId = telemetry.getCollegeId() != null ? telemetry.getCollegeId() : 1L;

        // Low latency broadcast to all players in the room
        messagingTemplate.convertAndSend("/topic/rally/" + collegeId + "/" + telemetry.getRoomId() + "/telemetry", telemetry);
    }

    public synchronized FinishEvent recordFinish(FinishEvent event) {
        Long collegeId = event.getCollegeId() != null ? event.getCollegeId() : 1L;
        var rooms = collegeRooms.get(collegeId);
        if (rooms == null) return event;

        RoomStateInternal room = rooms.get(event.getRoomId());
        if (room == null) return event;

        // Calculate rank based on finish order
        int rank = room.finishes.size() + 1;
        event.setRank(rank);

        // Points formula: 1st=25, 2nd=18, 3rd=15, 4th=12, 5th=10, 6th=8, 7th=6, 8th=4, 9th=2, 10th=1
        int[] pointTable = {25, 18, 15, 12, 10, 8, 6, 4, 2, 1};
        int points = rank <= pointTable.length ? pointTable[rank - 1] : 1;
        event.setPointsAwarded(points);

        room.finishes.add(event);

        // Award points to branch/section
        String branch = event.getBranch() != null ? event.getBranch() : "CSE-1";
        collegeBranchPoints.putIfAbsent(collegeId, new ConcurrentHashMap<>());
        collegeBranchPoints.get(collegeId).putIfAbsent(branch, new AtomicInteger(0));
        int totalBranchPoints = collegeBranchPoints.get(collegeId).get(branch).addAndGet(points);

        if (rank == 1) {
            collegeBranchWins.putIfAbsent(collegeId, new ConcurrentHashMap<>());
            collegeBranchWins.get(collegeId).putIfAbsent(branch, new AtomicInteger(0));
            collegeBranchWins.get(collegeId).get(branch).incrementAndGet();
        }

        // Store in recent finishes for college
        recentFinishes.putIfAbsent(collegeId, new CopyOnWriteArrayList<>());
        List<FinishEvent> recents = recentFinishes.get(collegeId);
        recents.add(0, event);
        if (recents.size() > 15) recents.remove(recents.size() - 1);

        // Persist points to database user if applicable
        try {
            if (event.getPlayerName() != null) {
                userRepository.findByGhostNameAndCollegeId(event.getPlayerName(), collegeId).ifPresent(u -> {
                    u.setWarPoints(u.getWarPoints() + points);
                    userRepository.save(u);
                });
            }
        } catch (Exception e) {
            log.warn("[Rally] Could not persist war points to DB user: {}", e.getMessage());
        }

        // Broadcast race finish event & updated community leaderboard
        messagingTemplate.convertAndSend("/topic/rally/" + collegeId + "/" + room.roomId + "/finish", event);
        broadcastLeaderboard(collegeId);

        log.info("[Rally] Race finish: #{} {} ({}) +{} pts! Total for {}: {}",
                rank, event.getPlayerName(), branch, points, branch, totalBranchPoints);

        return event;
    }

    public LeaderboardResponse getLeaderboard(Long collegeId) {
        if (collegeId == null) collegeId = 1L;
        collegeBranchPoints.putIfAbsent(collegeId, new ConcurrentHashMap<>());
        collegeBranchWins.putIfAbsent(collegeId, new ConcurrentHashMap<>());

        var bPoints = collegeBranchPoints.get(collegeId);
        var bWins = collegeBranchWins.get(collegeId);

        // Ensure default branches from DB or presets exist
        List<String> defaultBranches = List.of("CSE-1", "CSE-2", "ECE", "MECH", "CIVIL", "AIML");
        for (String b : defaultBranches) {
            bPoints.putIfAbsent(b, new AtomicInteger(0));
            bWins.putIfAbsent(b, new AtomicInteger(0));
        }

        List<BranchStanding> standings = bPoints.entrySet().stream()
                .map(e -> {
                    int pts = e.getValue().get();
                    int wins = bWins.getOrDefault(e.getKey(), new AtomicInteger(0)).get();
                    int percentage = Math.min(100, (int) ((pts / 100.0) * 100));
                    return BranchStanding.builder()
                            .branch(e.getKey())
                            .points(pts)
                            .percentage(percentage)
                            .wins(wins)
                            .build();
                })
                .sorted((a, b) -> Integer.compare(b.getPoints(), a.getPoints()))
                .collect(Collectors.toList());

        String leader = !standings.isEmpty() ? standings.get(0).getBranch() : "CSE-1";

        return LeaderboardResponse.builder()
                .collegeId(collegeId)
                .standings(standings)
                .leadingBranch(leader)
                .targetPoints(100)
                .recentFinishes(recentFinishes.getOrDefault(collegeId, Collections.emptyList()))
                .build();
    }

    private void broadcastRoomState(RoomStateInternal room) {
        RoomState state = buildRoomState(room);
        messagingTemplate.convertAndSend("/topic/rally/" + room.collegeId + "/" + room.roomId + "/status", state);
    }

    private void broadcastLeaderboard(Long collegeId) {
        LeaderboardResponse lb = getLeaderboard(collegeId);
        messagingTemplate.convertAndSend("/topic/rally/" + collegeId + "/leaderboard", lb);
    }

    private RoomState buildRoomState(RoomStateInternal room) {
        String msg;
        if (room.players.size() < MIN_PLAYERS_TO_START) {
            msg = "Waiting for opponents... (At least 2 players required to start race)";
        } else if (room.status.equals("COUNTDOWN")) {
            msg = "Race starts in " + room.countdown + "s!";
        } else if (room.status.equals("RACING")) {
            msg = "RACE IN PROGRESS!";
        } else {
            msg = "Race Completed";
        }

        return RoomState.builder()
                .roomId(room.roomId)
                .collegeId(room.collegeId)
                .status(room.status)
                .countdown(room.countdown)
                .playerCount(room.players.size())
                .players(new ArrayList<>(room.players.values()))
                .message(msg)
                .build();
    }
}

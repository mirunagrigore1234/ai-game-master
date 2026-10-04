package com.aigamemaster.service;

import com.aigamemaster.model.GameState;
import com.aigamemaster.model.JoinGameResponse;
import com.aigamemaster.model.Challenge;
import com.aigamemaster.model.Player;
import com.aigamemaster.model.LeaderboardEntry;
import com.aigamemaster.model.Evidence;
import com.aigamemaster.model.EvidenceResponse;
import com.aigamemaster.model.Vote;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Service
public class GameService {

    private final GameCodeGenerator gameCodeGenerator;
    private final ChallengeGenerationService challengeGenerationService;
    private final EvidenceVisionService evidenceVisionService;
    private final Map<String, GameState> games = new ConcurrentHashMap<>();

    public GameService(
            GameCodeGenerator gameCodeGenerator,
            ChallengeGenerationService challengeGenerationService,
            EvidenceVisionService evidenceVisionService
    ) {
        this.gameCodeGenerator = gameCodeGenerator;
        this.challengeGenerationService = challengeGenerationService;
        this.evidenceVisionService = evidenceVisionService;
    }

    public GameState createGame(GameState requestedGame) {
        validateGameConfiguration(requestedGame);

        while (true) {
            String code = gameCodeGenerator.generateCode();
            GameState game = new GameState(
                    code,
                    requestedGame.getMode(),
                    requestedGame.getEnvironment(),
                    requestedGame.getDifficulty(),
                    requestedGame.getChallengeCount(),
                    requestedGame.getDuration(),
                    new ArrayList<>()
            );

            if (games.putIfAbsent(code, game) == null) {
                return game;
            }
        }
    }

    private void validateGameConfiguration(GameState requestedGame) {
        if (requestedGame == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Game configuration is required");
        }

        String mode = requestedGame.getMode();
        if (!"mirror".equals(mode) && !"balanced".equals(mode) && !"chaos".equals(mode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mode must be mirror, balanced, or chaos"
            );
        }

        String environment = requestedGame.getEnvironment();
        if (!"indoor".equals(environment)
                && !"outdoor".equals(environment)
                && !"restaurant".equals(environment)
                && !"bar".equals(environment)
                && !"street".equals(environment)
                && !"anywhere".equals(environment)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Environment must be indoor, outdoor, restaurant, bar, street, or anywhere"
            );
        }

        String difficulty = requestedGame.getDifficulty();
        if ("chaos".equals(mode)) {
            if (difficulty != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Difficulty must be null for chaos mode"
                );
            }
        } else if (!"easy".equals(difficulty) && !"medium".equals(difficulty) && !"hard".equals(difficulty)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Difficulty must be easy, medium, or hard"
            );
        }

        if (requestedGame.getChallengeCount() < 1 || requestedGame.getChallengeCount() > 7) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Challenge count must be between 1 and 7"
            );
        }

        String duration = requestedGame.getDuration();
        if (!"ai".equals(duration)
                && !"10".equals(duration)
                && !"20".equals(duration)
                && !"30".equals(duration)
                && !"45".equals(duration)
                && !"60".equals(duration)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Duration must be ai, 10, 20, 30, 45, or 60"
            );
        }
    }

    public GameState getGame(String requestedCode) {
        String code = requestedCode == null ? "" : requestedCode.trim().toUpperCase(Locale.ROOT);
        GameState game = games.get(code);
        if (game == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found");
        }

        synchronized (game) {
            updateTimer(game);
        }
        return game;
    }

    public List<Player> getPlayers(String requestedCode) {
    String code = requestedCode == null ? "" : requestedCode.trim().toUpperCase(Locale.ROOT);

    GameState game = games.get(code);

    if (game == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found");
    }

    return new ArrayList<>(game.getPlayers());
}

    public JoinGameResponse joinGame(String requestedCode, String requestedName) {
        String code = requestedCode == null ? "" : requestedCode.trim().toUpperCase(Locale.ROOT);
        GameState game = games.get(code);
        if (game == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found");
        }
        if (!"lobby".equals(game.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Game has already started"
            );
        }

        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isEmpty() || name.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid player name");
        }

        synchronized (game) {
            if (!game.getChallenges().isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This game is no longer accepting players."
                );
            }

            boolean nameTaken = game.getPlayers().stream()
                    .anyMatch(player -> player.getName().equalsIgnoreCase(name));
            if (nameTaken) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Player name already taken");
            }

            Player player = new Player(UUID.randomUUID().toString(), name);
            game.getPlayers().add(player);
            if (game.getHostPlayerId() == null) {
                game.setHostPlayerId(player.getId());
            }
            return new JoinGameResponse(
                    code,
                    player.getId(),
                    player.getName(),
                    game.getHostPlayerId(),
                    new ArrayList<>(game.getPlayers())
            );
        }
    }

    public void removePlayer(String requestedCode, String playerId) {
        GameState game = getGame(requestedCode);
        synchronized (game) {
            Player player = game.getPlayers().stream()
                    .filter(currentPlayer -> currentPlayer.getId().equals(playerId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

            if (player.getId().equals(game.getHostPlayerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The host must end the game");
            }

            game.getPlayers().remove(player);
        }
    }

    public void startGame(String requestedCode, String playerId) {
        startGame(requestedCode, playerId, null);
    }

    public void startGame(String requestedCode, String playerId, String selectedDuration) {
    String code = requestedCode == null
            ? ""
            : requestedCode.trim().toUpperCase(Locale.ROOT);

    GameState game = games.get(code);

    if (game == null) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Game not found"
        );
    }

    synchronized (game) {
        if (playerId == null || !playerId.equals(game.getHostPlayerId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the host can start the game"
            );
        }

        if (!"lobby".equals(game.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Game has already started"
            );
        }

        if (game.getPlayers().size() < 2) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "At least 2 players are required to start the game."
            );
        }

        if (game.getChallenges().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Challenges must be prepared before starting"
            );
        }

        if (selectedDuration != null) {
            if (!List.of("10", "20", "30", "45", "60").contains(selectedDuration)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid game duration");
            }
            game.setDuration(selectedDuration);
        } else if ("ai".equals(game.getDuration())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A final duration must be selected before starting"
            );
        }

        try {
            Instant startTime = Instant.now();
            game.setStartTime(startTime);
            game.setEndTime(startTime.plus(durationFor(game.getDuration())));
            game.setStatus("playing");
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not generate challenges",
                    exception
            );
        }
    }
}

    public void endGame(String requestedCode, String playerId) {
        String code = requestedCode == null ? "" : requestedCode.trim().toUpperCase(Locale.ROOT);
        GameState game = games.get(code);
        if (game == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found");
        }

        synchronized (game) {
            if (playerId == null || !playerId.equals(game.getHostPlayerId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Only the host can end the game"
                );
            }

            game.setStatus("finished");
            System.out.println("GAME FINISHED: host ended the game");
        }
    }

    public void regenerateChallenges(String requestedCode, String playerId) {
        GameState game = getGame(requestedCode);
        synchronized (game) {
            if (playerId == null || !playerId.equals(game.getHostPlayerId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Only the host can regenerate challenges"
                );
            }

            if (!"lobby".equals(game.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Game has already started"
                );
            }
            if (game.getRegenerationCount() >= 2) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Maximum challenge regenerations reached"
                );
            }

            rememberCurrentChallenges(game);
            List<Challenge> generated = challengeGenerationService.generateChallenges(game);
            game.setChallenges(new ArrayList<>(generated));
            rememberChallenges(game, generated);
            game.setRegenerationCount(game.getRegenerationCount() + 1);
        }
    }

    public GameState prepareChallenges(String requestedCode, String playerId) {
        GameState game = getGame(requestedCode);
        synchronized (game) {
            requireHost(game, playerId, "Only the host can prepare challenges");
            if (!"lobby".equals(game.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Game has already started");
            }
            if (game.getPlayers().size() < 2) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "At least 2 players are required to start the game."
                );
            }
            if (!game.getChallenges().isEmpty()) {
                System.out.println("PREPARE CHALLENGES IGNORED: challenges already exist");
                return game;
            }
            System.out.println("PREPARE CHALLENGES GENERATING: explicit prepare request");
            rememberCurrentChallenges(game);
            List<Challenge> generated = challengeGenerationService.generateChallenges(game);
            game.setChallenges(new ArrayList<>(generated));
            rememberChallenges(game, generated);
            if ("ai".equals(game.getDuration())) {
                int recommendedDuration;
                try {
                    recommendedDuration = challengeGenerationService.recommendDuration(game);
                } catch (RuntimeException exception) {
                    recommendedDuration = 30;
                    System.out.println("Using fallback duration: 30 minutes");
                }
                if (!List.of(10, 20, 30, 45, 60).contains(recommendedDuration)) {
                    recommendedDuration = 30;
                    System.out.println("Using fallback duration: 30 minutes");
                }
                game.setRecommendedDuration(recommendedDuration);
                game.setDuration(String.valueOf(recommendedDuration));
            }
            System.out.println("Challenges generated successfully: count=" + generated.size());
            return game;
        }
    }

    private void rememberCurrentChallenges(GameState game) {
        rememberChallenges(game, game.getChallenges());
    }

    private void rememberChallenges(GameState game, List<Challenge> challenges) {
        for (Challenge challenge : challenges) {
            String normalized = challengeGenerationService.normalizeChallengeText(challenge.getChallenge());
            if (!normalized.isBlank()) {
                game.getUsedChallengeTexts().add(normalized);
            }
        }
    }

    public List<Challenge> getChallenges(String requestedCode, String playerId) {
        GameState game = getGame(requestedCode);
        synchronized (game) {
            if (playerId == null) {
                return new ArrayList<>(game.getChallenges());
            }
            return game.getChallenges().stream()
                    .filter(challenge -> playerId.equals(challenge.getPlayerId()))
                    .toList();
        }
    }

    public EvidenceResponse submitEvidence(
            String requestedCode,
            String challengeId,
            String playerId,
            MultipartFile image
    ) {
        if (image == null || image.isEmpty()) {
            System.out.println("EVIDENCE FILE MISSING OR EMPTY");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence image is missing or empty.");
        }
        System.out.println("EVIDENCE FILE RECEIVED");
        GameState game = getGame(requestedCode);
        synchronized (game) {
            System.out.println("EVIDENCE VALIDATION START");
            updateTimer(game);
            if (!"playing".equals(game.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Game is not active");
            }
            Player player = findPlayer(game, playerId);
            Challenge challenge = findPendingOwnedChallenge(game, challengeId, player.getId());
            if (game.getEvidence().stream().anyMatch(current -> challengeId.equals(current.getChallengeId()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Evidence already submitted for this challenge");
            }

            try {
                String extension = "image/png".equals(image.getContentType()) ? ".png" : ".jpg";
                Path path = Files.createTempFile("ai-game-evidence-", extension);
                image.transferTo(path);
                Evidence submitted = new Evidence(
                        UUID.randomUUID().toString(),
                        challenge.getId(),
                        player.getId(),
                        path.toString()
                );
                submitted.setStatus("analyzing");
                game.getEvidence().add(submitted);
                System.out.println("EVIDENCE FILE SAVED evidenceId=" + submitted.getEvidenceId());
                CompletableFuture.runAsync(() -> evidenceVisionService.analyze(submitted, challenge.getChallenge()));
                return toEvidenceResponse(game, submitted, playerId);
            } catch (Exception exception) {
                System.out.println("EVIDENCE FILE STORAGE FAILED: " + exception.getClass().getSimpleName()
                        + ": " + exception.getMessage());
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Could not store evidence",
                        exception
                );
            }
        }
    }

    public List<EvidenceResponse> getEvidence(String requestedCode, String viewerId) {
        GameState game = getGame(requestedCode);
        synchronized (game) {
            return game.getEvidence().stream().map(evidence -> toEvidenceResponse(game, evidence, viewerId)).toList();
        }
    }

    public EvidenceResponse castVote(String requestedCode, String evidenceId, Vote request) {
        GameState game = getGame(requestedCode);
        synchronized (game) {
            updateTimer(game);
            if ("finished".equals(game.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Game is finished");
            }
            Evidence evidence = game.getEvidence().stream()
                    .filter(current -> evidenceId.equals(current.getEvidenceId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));
            if (evidence.getFinalDecision() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Evidence already finalized");
            }
            Player voter = findPlayer(game, request.getPlayerId());
            if (voter.getId().equals(evidence.getPlayerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Evidence owner cannot vote");
            }
            if (!"approve".equals(request.getDecision()) && !"reject".equals(request.getDecision())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be approve or reject");
            }
            if (evidence.getVotes().stream().anyMatch(vote -> voter.getId().equals(vote.getPlayerId()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Player has already voted");
            }
            evidence.getVotes().add(new Vote(evidenceId, voter.getId(), request.getDecision()));
            resolveEvidenceIfDecided(game, evidence);
            return toEvidenceResponse(game, evidence, voter.getId());
        }
    }

    public EvidenceResponse retryEvidence(String requestedCode, String evidenceId, String playerId) {
        GameState game = getGame(requestedCode);
        synchronized (game) {
            Evidence evidence = game.getEvidence().stream()
                    .filter(current -> evidenceId.equals(current.getEvidenceId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));
            if (!playerId.equals(evidence.getPlayerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Evidence belongs to another player");
            }
            if (!"submitted".equals(evidence.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Evidence is not ready for retry");
            }
            Challenge challenge = game.getChallenges().stream()
                    .filter(current -> current.getId().equals(evidence.getChallengeId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));
            evidence.setStatus("analyzing");
            CompletableFuture.runAsync(() -> evidenceVisionService.analyze(evidence, challenge.getChallenge()));
            return toEvidenceResponse(game, evidence, playerId);
        }
    }

    public ResponseEntity<Resource> getEvidenceImage(String requestedCode, String evidenceId) {
        GameState game = getGame(requestedCode);
        synchronized (game) {
            Evidence evidence = game.getEvidence().stream()
                    .filter(current -> evidenceId.equals(current.getEvidenceId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));
            Path path = Path.of(evidence.getImagePath());
            if (!Files.exists(path)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence image not found");
            }
            try {
                String contentType = Files.probeContentType(path);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType == null
                                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType))
                        .body(new FileSystemResource(path));
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence image not found", exception);
            }
        }
    }

    private Challenge findPendingOwnedChallenge(GameState game, String challengeId, String playerId) {
        Challenge challenge = game.getChallenges().stream()
                .filter(current -> challengeId.equals(current.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));
        if (!playerId.equals(challenge.getPlayerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Challenge belongs to another player");
        }
        if (!"pending".equals(challenge.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Challenge is no longer pending");
        }
        return challenge;
    }

    private void resolveEvidenceIfDecided(GameState game, Evidence evidence) {
        int approve = countVotes(evidence, "approve");
        int reject = countVotes(evidence, "reject");
        int eligible = (int) game.getPlayers().stream()
                .filter(player -> !player.getId().equals(evidence.getPlayerId()))
                .count();
        int remaining = eligible - evidence.getVotes().size();
        String decision = null;
        if (approve > reject && approve > remaining) {
            decision = "approved";
        } else if (reject > approve && reject > remaining) {
            decision = "rejected";
        } else if (evidence.getVotes().size() >= eligible) {
            decision = approve > reject ? "approved" : "rejected";
        }
        if (decision == null) {
            return;
        }
        evidence.setFinalDecision(decision);
        Challenge challenge = game.getChallenges().stream()
                .filter(current -> current.getId().equals(evidence.getChallengeId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));
        System.out.println("Resolving evidence " + evidence.getEvidenceId() + ": decision=" + decision);
        if ("approved".equals(decision)) {
            challenge.setState("completed");
            if (!evidence.isPointsAwarded()) {
                Player owner = findPlayer(game, evidence.getPlayerId());
                owner.setScore(owner.getScore() + challenge.getPoints());
                evidence.setPointsAwarded(true);
            }
        } else {
            challenge.setState("rejected");
        }
        System.out.println("Challenge " + challenge.getId() + " resolved with state=" + challenge.getState());
        finishIfAllChallengesResolved(game);
    }

    private int countVotes(Evidence evidence, String decision) {
        return (int) evidence.getVotes().stream()
                .filter(vote -> decision.equals(vote.getDecision()))
                .count();
    }

    private void finishIfAllChallengesResolved(GameState game) {
        int resolvedCount = (int) game.getChallenges().stream()
                .filter(challenge -> "completed".equals(challenge.getState())
                        || "skipped".equals(challenge.getState())
                        || "rejected".equals(challenge.getState()))
                .count();
        int totalChallenges = game.getChallenges().size();
        System.out.println("Checking game completion: resolved=" + resolvedCount
                + ", total=" + totalChallenges);
        if (totalChallenges > 0 && resolvedCount == totalChallenges) {
            game.setStatus("finished");
            System.out.println("GAME FINISHED: all challenges resolved");
        }
    }

    private EvidenceResponse toEvidenceResponse(GameState game, Evidence evidence, String viewerId) {
        String playerName = game.getPlayers().stream()
                .filter(player -> player.getId().equals(evidence.getPlayerId()))
                .map(Player::getName)
                .findFirst()
                .orElse("Player");
        String challengeText = game.getChallenges().stream()
                .filter(challenge -> challenge.getId().equals(evidence.getChallengeId()))
                .map(Challenge::getChallenge)
                .findFirst()
                .orElse("");
        int approveVotes = countVotes(evidence, "approve");
        int rejectVotes = countVotes(evidence, "reject");
        int eligibleVoters = (int) game.getPlayers().stream()
                .filter(player -> !player.getId().equals(evidence.getPlayerId()))
                .count();
        String currentVote = evidence.getVotes().stream()
                .filter(vote -> vote.getPlayerId().equals(viewerId))
                .map(Vote::getDecision)
                .findFirst()
                .orElse(null);
        int pointsAwarded = "approved".equals(evidence.getFinalDecision())
                ? game.getChallenges().stream()
                .filter(challenge -> challenge.getId().equals(evidence.getChallengeId()))
                .mapToInt(Challenge::getPoints)
                .findFirst()
                .orElse(0)
                : 0;
        return new EvidenceResponse(
                evidence.getEvidenceId(),
                evidence.getChallengeId(),
                evidence.getPlayerId(),
                playerName,
                challengeText,
                "/games/" + game.getCode() + "/evidence/" + evidence.getEvidenceId() + "/image",
                evidence.getStatus(),
                evidence.getAiApproved(),
                evidence.getAiConfidence(),
                evidence.getAiAnalysis(),
                evidence.getCreatedAt(),
                approveVotes,
                rejectVotes,
                evidence.getVotes().size(),
                eligibleVoters,
                evidence.getFinalDecision(),
                pointsAwarded,
                currentVote
        );
    }

    public void setStartDuration(String requestedCode, String playerId, String duration) {
        GameState game = getGame(requestedCode);
        synchronized (game) {
            requireHost(game, playerId, "Only the host can choose the duration");
            if (!"lobby".equals(game.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Game has already started");
            }
            if (!List.of("10", "20", "30", "45", "60").contains(duration)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid game duration");
            }
            game.setDuration(duration);
        }
    }

    public void completeChallenge(String requestedCode, String challengeId, String playerId) {
        updateChallengeState(requestedCode, challengeId, playerId, "completed");
    }

    public void skipChallenge(String requestedCode, String challengeId, String playerId) {
        updateChallengeState(requestedCode, challengeId, playerId, "skipped");
    }

    private void updateChallengeState(
            String requestedCode,
            String challengeId,
            String playerId,
            String nextState
    ) {
        GameState game = getGame(requestedCode);
        synchronized (game) {
            updateTimer(game);
            if (!"playing".equals(game.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Game is not active");
            }

            Challenge challenge = game.getChallenges().stream()
                    .filter(current -> current.getId().equals(challengeId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Challenge not found"
                    ));
            if (!playerId.equals(challenge.getPlayerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Challenge belongs to another player");
            }
            if (!"pending".equals(challenge.getState())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Challenge is no longer pending");
            }
            if ("completed".equals(nextState)
                    && game.getEvidence().stream().anyMatch(evidence -> challengeId.equals(evidence.getChallengeId()))) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Challenge completion requires a human evidence decision"
                );
            }

            challenge.setState(nextState);
            if ("completed".equals(nextState)) {
                Player player = findPlayer(game, playerId);
                int availablePoints = game.getChallenges().stream()
                        .filter(current -> playerId.equals(current.getPlayerId()))
                        .mapToInt(Challenge::getPoints)
                        .sum();
                player.setScore(Math.min(availablePoints, player.getScore() + challenge.getPoints()));
            }
            if (game.getChallenges().stream()
                    .allMatch(current -> "completed".equals(current.getState())
                            || "skipped".equals(current.getState())
                            || "rejected".equals(current.getState()))) {
                game.setStatus("finished");
            }
        }
    }

    public List<LeaderboardEntry> getLeaderboard(String requestedCode) {
        GameState game = getGame(requestedCode);
        synchronized (game) {
            return game.getPlayers().stream()
                    .map(player -> {
                        int completed = countChallenges(game, player.getId(), "completed");
                        int skipped = countChallenges(game, player.getId(), "skipped");
                        int total = countChallenges(game, player.getId(), null);
                        return new LeaderboardEntry(
                                player.getId(),
                                player.getName(),
                                player.getScore(),
                                completed,
                                skipped,
                                total
                        );
                    })
                    .sorted(Comparator.comparingInt(LeaderboardEntry::getScore).reversed())
                    .toList();
        }
    }

    private Player findPlayer(GameState game, String playerId) {
        return game.getPlayers().stream()
                .filter(player -> player.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));
    }

    private int countChallenges(GameState game, String playerId, String state) {
        return (int) game.getChallenges().stream()
                .filter(challenge -> playerId.equals(challenge.getPlayerId()))
                .filter(challenge -> state == null || state.equals(challenge.getState()))
                .count();
    }

    private void updateTimer(GameState game) {
        if ("playing".equals(game.getStatus())
                && game.getEndTime() != null
                && !Instant.now().isBefore(game.getEndTime())) {
            game.setStatus("finished");
        }
    }

    private Duration durationFor(String duration) {
        int minutes = Integer.parseInt(duration);
        return Duration.ofMinutes(minutes);
    }

    private void requireHost(GameState game, String playerId, String message) {
        if (playerId == null || !playerId.equals(game.getHostPlayerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }
}

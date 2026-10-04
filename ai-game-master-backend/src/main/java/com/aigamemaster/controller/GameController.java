package com.aigamemaster.controller;

import com.aigamemaster.model.Challenge;
import com.aigamemaster.model.ChallengeActionRequest;
import com.aigamemaster.model.GameState;
import com.aigamemaster.model.JoinGameRequest;
import com.aigamemaster.model.JoinGameResponse;
import com.aigamemaster.model.Player;
import com.aigamemaster.model.LeaderboardEntry;
import com.aigamemaster.model.RegenerateChallengesRequest;
import com.aigamemaster.model.StartGameRequest;
import com.aigamemaster.model.EvidenceResponse;
import com.aigamemaster.service.GameService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/games")
@CrossOrigin
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameState createGame(@RequestBody GameState requestedGame) {
        return gameService.createGame(requestedGame);
    }

    @GetMapping("/{code}")
    public GameState getGame(@PathVariable String code) {
        return gameService.getGame(code);
    }

    @GetMapping("/{code}/players")
    public List<Player> getPlayers(@PathVariable String code) {
        return gameService.getPlayers(code);
    }

    @PostMapping("/{code}/join")
    @ResponseStatus(HttpStatus.OK)
    public JoinGameResponse joinGame(
            @PathVariable String code,
            @RequestBody JoinGameRequest request
    ) {
        return gameService.joinGame(code, request.getName());
    }

    @DeleteMapping("/{code}/players/{playerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePlayer(@PathVariable String code, @PathVariable String playerId) {
        gameService.removePlayer(code, playerId);
    }

    @PostMapping("/{code}/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endGame(@PathVariable String code, @RequestBody Map<String, String> request) {
        gameService.endGame(code, request.get("playerId"));
    }

    @PostMapping("/{code}/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startGame(@PathVariable String code, @RequestBody StartGameRequest request) {
        gameService.startGame(code, request.getPlayerId(), request.getDuration());
    }

    @PostMapping("/{code}/regenerate-challenges")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void regenerateChallenges(
            @PathVariable String code,
            @RequestBody RegenerateChallengesRequest request
    ) {
        gameService.regenerateChallenges(code, request.getPlayerId());
    }

    @PostMapping("/{code}/prepare-challenges")
    public GameState prepareChallenges(@PathVariable String code, @RequestBody RegenerateChallengesRequest request) {
        System.out.println("PREPARE CHALLENGES CALLED FROM POST /games/{code}/prepare-challenges");
        return gameService.prepareChallenges(code, request.getPlayerId());
    }

    @GetMapping("/{code}/challenges")
    public List<Challenge> getChallenges(
            @PathVariable String code,
            @RequestParam(required = false) String playerId
    ) {
        return gameService.getChallenges(code, playerId);
    }

    @PostMapping("/{code}/challenges/{challengeId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeChallenge(
            @PathVariable String code,
            @PathVariable String challengeId,
            @RequestBody ChallengeActionRequest request
    ) {
        gameService.completeChallenge(code, challengeId, request.getPlayerId());
    }

    @PostMapping("/{code}/challenges/{challengeId}/skip")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void skipChallenge(
            @PathVariable String code,
            @PathVariable String challengeId,
            @RequestBody ChallengeActionRequest request
    ) {
        gameService.skipChallenge(code, challengeId, request.getPlayerId());
    }

    @PostMapping("/{code}/challenges/{challengeId}/evidence")
    public EvidenceResponse submitEvidence(
            @PathVariable String code,
            @PathVariable String challengeId,
            @RequestParam("playerId") String playerId,
            @RequestPart("image") MultipartFile image
    ) {
        System.out.println("=== EVIDENCE UPLOAD REQUEST ===");
        System.out.println("gameCode=" + code);
        System.out.println("challengeId=" + challengeId);
        System.out.println("playerId=" + playerId);
        System.out.println("fileName=" + (image == null ? null : image.getOriginalFilename()));
        System.out.println("contentType=" + (image == null ? null : image.getContentType()));
        System.out.println("size=" + (image == null ? null : image.getSize()));
        return gameService.submitEvidence(code, challengeId, playerId, image);
    }

    @GetMapping("/{code}/evidence")
    public List<EvidenceResponse> getEvidence(
            @PathVariable String code,
            @RequestParam String playerId
    ) {
        return gameService.getEvidence(code, playerId);
    }

    @PostMapping("/{code}/evidence/{evidenceId}/votes")
    public EvidenceResponse castVote(
            @PathVariable String code,
            @PathVariable String evidenceId,
            @RequestBody com.aigamemaster.model.Vote request
    ) {
        return gameService.castVote(code, evidenceId, request);
    }

    @PostMapping("/{code}/evidence/{evidenceId}/retry")
    public EvidenceResponse retryEvidence(
            @PathVariable String code,
            @PathVariable String evidenceId,
            @RequestBody ChallengeActionRequest request
    ) {
        return gameService.retryEvidence(code, evidenceId, request.getPlayerId());
    }

    @GetMapping("/{code}/evidence/{evidenceId}/image")
    public ResponseEntity<Resource> getEvidenceImage(
            @PathVariable String code,
            @PathVariable String evidenceId
    ) {
        return gameService.getEvidenceImage(code, evidenceId);
    }

    @GetMapping("/{code}/leaderboard")
    public List<LeaderboardEntry> getLeaderboard(@PathVariable String code) {
        return gameService.getLeaderboard(code);
    }
}

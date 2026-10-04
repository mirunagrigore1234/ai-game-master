package com.aigamemaster.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.Instant;

public class GameState {

    private String code;
    private String mode;
    private String environment;
    private String difficulty;
    private int challengeCount;
    private String duration;
    private String hostPlayerId;
    private String status = "lobby";
    private List<Player> players = new ArrayList<>();
    private List<Challenge> challenges = new ArrayList<>();
    private List<Evidence> evidence = new ArrayList<>();
    private Set<String> usedChallengeTexts = new HashSet<>();
    private int regenerationCount;
    private Integer recommendedDuration;
    private Instant startTime;
    private Instant endTime;

    public GameState() {
    }

    public GameState(
            String code,
            String mode,
            String environment,
            String difficulty,
            int challengeCount,
            String duration,
            List<Player> players
    ) {
        this.code = code;
        this.mode = mode;
        this.environment = environment;
        this.difficulty = difficulty;
        this.challengeCount = challengeCount;
        this.duration = duration;
        this.players = players;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public int getChallengeCount() {
        return challengeCount;
    }

    public void setChallengeCount(int challengeCount) {
        this.challengeCount = challengeCount;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getHostPlayerId() {
        return hostPlayerId;
    }

    public void setHostPlayerId(String hostPlayerId) {
        this.hostPlayerId = hostPlayerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public List<Challenge> getChallenges() {
        return challenges;
    }

    public void setChallenges(List<Challenge> challenges) {
        this.challenges = challenges;
    }

    public List<Evidence> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<Evidence> evidence) {
        this.evidence = evidence;
    }

    public Set<String> getUsedChallengeTexts() {
        return usedChallengeTexts;
    }

    public void setUsedChallengeTexts(Set<String> usedChallengeTexts) {
        this.usedChallengeTexts = usedChallengeTexts;
    }

    public int getRegenerationCount() {
        return regenerationCount;
    }

    public void setRegenerationCount(int regenerationCount) {
        this.regenerationCount = regenerationCount;
    }

    public Integer getRecommendedDuration() {
        return recommendedDuration;
    }

    public void setRecommendedDuration(Integer recommendedDuration) {
        this.recommendedDuration = recommendedDuration;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }
}

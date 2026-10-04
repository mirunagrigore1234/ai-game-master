package com.aigamemaster.model;

public class LeaderboardEntry {

    private String playerId;
    private String name;
    private int score;
    private int completedChallengeCount;
    private int skippedChallengeCount;
    private int totalChallenges;

    public LeaderboardEntry(
            String playerId,
            String name,
            int score,
            int completedChallengeCount,
            int skippedChallengeCount,
            int totalChallenges
    ) {
        this.playerId = playerId;
        this.name = name;
        this.score = score;
        this.completedChallengeCount = completedChallengeCount;
        this.skippedChallengeCount = skippedChallengeCount;
        this.totalChallenges = totalChallenges;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public int getCompletedChallengeCount() {
        return completedChallengeCount;
    }

    public int getSkippedChallengeCount() {
        return skippedChallengeCount;
    }

    public int getTotalChallenges() {
        return totalChallenges;
    }
}

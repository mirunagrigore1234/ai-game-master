package com.aigamemaster.service;

import com.aigamemaster.model.Challenge;
import com.aigamemaster.model.Player;

import java.util.List;
import java.util.Set;

public interface ChallengeAiProvider {

    List<Challenge> generateChallenges(
            String mode,
            String environment,
            String difficulty,
            int numberOfChallenges,
            List<Player> players
    );

    default List<Challenge> generateChallenges(
            String mode,
            String environment,
            String difficulty,
            int numberOfChallenges,
            List<Player> players,
            Set<String> excludedChallenges
    ) {
        return generateChallenges(mode, environment, difficulty, numberOfChallenges, players);
    }

    default int recommendDuration(
            String mode,
            String environment,
            String difficulty,
            int numberOfChallenges,
            List<Player> players
    ) {
        int base = numberOfChallenges * 5;
        int difficultyAdjustment = "hard".equals(difficulty) ? 15 : "medium".equals(difficulty) ? 5 : 0;
        int modeAdjustment = "balanced".equals(mode) ? 5 : "chaos".equals(mode) ? 10 : 0;
        int playerAdjustment = Math.max(0, players.size() - 2) * 5;
        int recommendation = base + difficultyAdjustment + modeAdjustment + playerAdjustment;
        if (recommendation <= 10) return 10;
        if (recommendation <= 20) return 20;
        if (recommendation <= 30) return 30;
        if (recommendation <= 45) return 45;
        return 60;
    }
}

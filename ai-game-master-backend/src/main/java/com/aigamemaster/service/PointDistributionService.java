package com.aigamemaster.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PointDistributionService {

    public List<Integer> distribute(int numberOfChallenges, String difficulty) {
        if (numberOfChallenges < 1 || numberOfChallenges > 7) {
            throw new IllegalArgumentException("Number of challenges must be between 1 and 7");
        }

        String normalizedDifficulty = difficulty == null ? "medium" : difficulty;
        int minimumPoints = numberOfChallenges;
        int pointsToDistribute = 100 - minimumPoints;
        List<Integer> weights = new ArrayList<>(numberOfChallenges);
        for (int index = 0; index < numberOfChallenges; index++) {
            int weight = switch (normalizedDifficulty) {
                case "easy" -> 1;
                case "hard" -> (index + 1) * (index + 1);
                default -> index + 1;
            };
            weights.add(weight);
        }

        int totalWeight = weights.stream().mapToInt(Integer::intValue).sum();
        List<Integer> points = new ArrayList<>(numberOfChallenges);
        int assigned = 0;

        for (int index = 0; index < numberOfChallenges; index++) {
            int extra = pointsToDistribute * weights.get(index) / totalWeight;
            points.add(1 + extra);
            assigned += extra;
        }

        int remainder = pointsToDistribute - assigned;
        for (int index = numberOfChallenges - 1; remainder > 0; index = (index - 1 + numberOfChallenges) % numberOfChallenges) {
            points.set(index, points.get(index) + 1);
            remainder--;
        }

        int total = points.stream().mapToInt(Integer::intValue).sum();
        if (total != 100 || points.stream().anyMatch(point -> point <= 0)) {
            throw new IllegalStateException("Point distribution must contain positive points totaling 100");
        }

        return points;
    }

    public List<Integer> distribute(int numberOfChallenges, List<String> difficulties) {
        if (numberOfChallenges < 1 || numberOfChallenges > 7) {
            throw new IllegalArgumentException("Number of challenges must be between 1 and 7");
        }
        if (difficulties == null || difficulties.size() != numberOfChallenges) {
            throw new IllegalArgumentException("Difficulty count must match the number of challenges");
        }

        int minimumPoints = numberOfChallenges;
        int pointsToDistribute = 100 - minimumPoints;
        List<Integer> weights = difficulties.stream()
                .mapToInt(this::difficultyWeight)
                .boxed()
                .toList();
        int totalWeight = weights.stream().mapToInt(Integer::intValue).sum();
        List<Integer> points = new ArrayList<>(numberOfChallenges);
        int assigned = 0;

        for (int weight : weights) {
            int extra = pointsToDistribute * weight / totalWeight;
            points.add(1 + extra);
            assigned += extra;
        }

        int remainder = pointsToDistribute - assigned;
        for (int index = numberOfChallenges - 1; remainder > 0; index = (index - 1 + numberOfChallenges) % numberOfChallenges) {
            points.set(index, points.get(index) + 1);
            remainder--;
        }

        int total = points.stream().mapToInt(Integer::intValue).sum();
        if (total != 100 || points.stream().anyMatch(point -> point <= 0)) {
            throw new IllegalStateException("Point distribution must contain positive points totaling 100");
        }

        return points;
    }

    private int difficultyWeight(String difficulty) {
        return switch (difficulty == null ? "medium" : difficulty.toLowerCase(Locale.ROOT)) {
            case "easy" -> 1;
            case "hard" -> 3;
            default -> 2;
        };
    }
}

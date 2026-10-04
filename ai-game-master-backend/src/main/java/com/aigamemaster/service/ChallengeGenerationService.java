package com.aigamemaster.service;

import com.aigamemaster.model.Challenge;
import com.aigamemaster.model.GameState;
import com.aigamemaster.model.Player;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ChallengeGenerationService {

    private final ChallengeAiProvider aiProvider;
    private final ChallengeSafetyService safetyService;
    private final PointDistributionService pointDistributionService;

    public ChallengeGenerationService(
            ChallengeAiProvider aiProvider,
            ChallengeSafetyService safetyService,
            PointDistributionService pointDistributionService
    ) {
        this.aiProvider = aiProvider;
        this.safetyService = safetyService;
        this.pointDistributionService = pointDistributionService;
    }

    public List<Challenge> generateChallenges(GameState game) {
        List<Player> players = new ArrayList<>(game.getPlayers());
        return generateChallenges(
                game.getMode(),
                game.getEnvironment(),
                game.getDifficulty(),
                game.getChallengeCount(),
                players,
                game.getUsedChallengeTexts()
        );
    }

    public int recommendDuration(GameState game) {
        return aiProvider.recommendDuration(
                game.getMode(),
                game.getEnvironment(),
                game.getDifficulty(),
                game.getChallengeCount(),
                new ArrayList<>(game.getPlayers())
        );
    }

    public List<Challenge> generateChallenges(
            String mode,
            String environment,
            String difficulty,
            int numberOfChallenges,
            List<Player> players,
            Set<String> excludedChallenges
    ) {
        if (players.isEmpty()) {
            return new ArrayList<>();
        }

        List<Challenge> candidates = aiProvider.generateChallenges(
                mode,
                environment,
                difficulty,
                numberOfChallenges,
                players,
                excludedChallenges
        );
        candidates.forEach(this::removeSafetyWording);
        Set<String> previous = new HashSet<>(excludedChallenges == null ? Set.of() : excludedChallenges);
        List<Integer> points = "chaos".equals(mode)
                ? null
                : pointDistributionService.distribute(numberOfChallenges, difficulty);

        if ("mirror".equals(mode)) {
            return generateMirror(mode, difficulty, numberOfChallenges, players, candidates, points, environment, previous);
        }
        return generateIndividual(mode, difficulty, numberOfChallenges, players, candidates, points, environment, previous);
    }

    private List<Challenge> generateMirror(
            String mode,
            String difficulty,
            int numberOfChallenges,
            List<Player> players,
            List<Challenge> candidates,
            List<Integer> points,
            String environment,
            Set<String> excludedChallenges
    ) {
        List<Challenge> base = selectSafeUnique(candidates, numberOfChallenges, excludedChallenges, environment);
        List<Challenge> result = new ArrayList<>();
        for (Player player : players) {
            for (int index = 0; index < base.size(); index++) {
                result.add(copyChallenge(base.get(index), "ch_" + player.getId() + "_" + index,
                        player.getId(), difficulty, points.get(index)));
            }
        }
        return result;
    }

    private List<Challenge> generateIndividual(
            String mode,
            String difficulty,
            int numberOfChallenges,
            List<Player> players,
            List<Challenge> candidates,
            List<Integer> points,
            String environment,
            Set<String> excludedChallenges
    ) {
        List<Challenge> result = new ArrayList<>();
        Set<String> globallyUsed = new HashSet<>(excludedChallenges);

        for (Player player : players) {
            Set<String> playerUsed = new HashSet<>();
            List<Challenge> selected = new ArrayList<>();
            for (Challenge candidate : candidates) {
                if (candidate == null
                        || (candidate.getPlayerId() != null
                        && !candidate.getPlayerId().equals(player.getId()))) {
                    continue;
                }
                String normalized = normalizeChallengeText(candidate.getChallenge());
                if (normalized.isEmpty()
                        || excludedChallenges.contains(normalized)
                        || playerUsed.contains(normalized)
                        || globallyUsed.contains(normalized)
                        || !safetyService.isSafe(candidate)) {
                    continue;
                }
                selected.add(candidate);
                playerUsed.add(normalized);
                globallyUsed.add(normalized);
                if (selected.size() == numberOfChallenges) {
                    break;
                }
            }

            for (int index = selected.size(); index < numberOfChallenges; index++) {
                Challenge fallback = fallbackChallenge(player, index, globallyUsed, mode, environment);
                selected.add(fallback);
                globallyUsed.add(normalizeChallengeText(fallback.getChallenge()));
            }

            List<String> challengeDifficulties = new ArrayList<>(selected.size());
            for (int index = 0; index < selected.size(); index++) {
                Challenge candidate = selected.get(index);
                challengeDifficulties.add("chaos".equals(mode)
                        ? validChaosDifficulty(candidate.getDifficulty(), index)
                        : difficulty);
            }
            List<Integer> playerPoints = "chaos".equals(mode)
                    ? pointDistributionService.distribute(numberOfChallenges, challengeDifficulties)
                    : points;

            for (int index = 0; index < selected.size(); index++) {
                Challenge candidate = selected.get(index);
                result.add(copyChallenge(
                        candidate,
                        "ch_" + player.getId() + "_" + index,
                        player.getId(),
                        challengeDifficulties.get(index),
                        playerPoints.get(index)
                ));
            }
        }
        return result;
    }

    private List<Challenge> selectSafeUnique(
            List<Challenge> candidates,
            int requested,
            Set<String> excluded,
            String environment
    ) {
        List<Challenge> selected = new ArrayList<>();
        Set<String> used = new HashSet<>(excluded);
        for (Challenge candidate : candidates) {
            String normalized = normalizeChallengeText(candidate == null ? null : candidate.getChallenge());
            if (!normalized.isEmpty() && !used.contains(normalized) && safetyService.isSafe(candidate)) {
                selected.add(candidate);
                used.add(normalized);
                if (selected.size() == requested) {
                    break;
                }
            }
        }
        while (selected.size() < requested) {
            Challenge fallback = fallbackChallenge(null, selected.size(), used, "mirror", environment);
            selected.add(fallback);
            used.add(normalizeChallengeText(fallback.getChallenge()));
        }
        return selected;
    }

    private Challenge fallbackChallenge(
            Player player,
            int index,
            Set<String> used,
            String mode,
            String environment
    ) {
        List<String> fallbackPool = new ArrayList<>(fallbackChallenges(environment));
        List<String> viewVariants = List.of(
                " in a close-up view.",
                " with the surrounding setting visible.",
                " from a different angle.",
                " in a wide view."
        );
        for (String base : new ArrayList<>(fallbackPool)) {
            String withoutPeriod = base.endsWith(".")
                    ? base.substring(0, base.length() - 1)
                    : base;
            for (String variant : viewVariants) {
                fallbackPool.add(withoutPeriod + variant);
            }
        }
        String fallbackText = null;
        for (int offset = 0; offset < fallbackPool.size(); offset++) {
            int candidateIndex = (Math.max(0, index) + offset) % fallbackPool.size();
            String candidate = fallbackPool.get(candidateIndex);
            if (!used.contains(normalizeChallengeText(candidate))) {
                fallbackText = candidate;
                break;
            }
        }
        if (fallbackText == null) {
            throw new IllegalStateException(
                    "Not enough unique fallback challenges for environment " + environment
            );
        }

        Challenge fallback = new Challenge();
        fallback.setChallenge(fallbackText);
        fallback.setPlayerId(player == null ? null : player.getId());
        fallback.setDifficulty("chaos".equals(mode) ? "medium" : "medium");
        fallback.setCategory("observation");
        return fallback;
    }

    private List<String> fallbackChallenges(String environment) {
        return switch (environment) {
            case "indoor" -> List.of(
                    "Ask a willing person to help create a funny indoor product advertisement and photograph the final pose.",
                    "Create a short waiter-and-customer role-play with a willing participant and photograph it.",
                    "Ask a willing friend to invent a ridiculous use for an indoor object and pose as its spokesperson.",
                    "Create a fake product advertisement with a willing participant and photograph the final scene.",
                    "Build a tiny indoor stage from available objects and ask a willing participant to pose on it.",
                    "Create a funny transformation of an indoor seating area with a willing collaborator.",
                    "Photograph a decoration while a willing participant poses as its tour guide.",
                    "Ask a willing person to choose an object and incorporate it into a playful photo scene."
            );
            case "outdoor" -> List.of(
                    "Ask a willing person to pose as if advertising a nearby landmark and photograph the result.",
                    "Find a willing participant to create a funny tourist-advertisement pose with you.",
                    "If two willing people agree, ask them to recreate a playful movie-poster pose outdoors.",
                    "Ask a willing friend to act as a tour guide while you pose as an amazed visitor.",
                    "Create a fake outdoor news report with a willing participant beside a landmark.",
                    "Ask a willing person to turn a public outdoor object into a ridiculous advertisement.",
                    "Create a harmless outdoor role-play scene inspired by the nearby landscape.",
                    "Photograph a natural feature while a willing participant acts as its ambassador."
            );
            case "restaurant" -> List.of(
                    "Ask a willing staff member to pose with you while holding the menu and photograph the moment.",
                    "If a willing staff member agrees, ask them to help create a funny table scene.",
                    "Ask a willing staff member to choose between two harmless visible options and photograph the choice.",
                    "Ask a willing staff member to choose an interesting table item and advertise it as a luxury product.",
                    "Ask a willing friend to pretend to be a famous food critic beside the table.",
                    "Create a funny waiter-and-customer role-play with a willing participant.",
                    "Ask a willing staff member to help create a dramatic food-commercial pose without interrupting work.",
                    "Create a collaborative table scene with willing participants and visible decorations."
            );
            case "bar" -> List.of(
                    "Ask a willing person to do a high-five with you and photograph the moment.",
                    "Ask a willing person to pose with you as if you won an imaginary award.",
                    "Ask two willing people to help create a ridiculous band photo using visible decorations.",
                    "Ask a willing person to choose a music category and create a funny pose representing it.",
                    "Create a mock awards ceremony with a willing participant and visible decorations.",
                    "Turn a visible table object into a luxury product advertisement with a willing person.",
                    "Create a harmless celebrity-arrival scene with a willing participant.",
                    "Ask a willing friend to recreate a funny dramatic scene."
            );
            case "street" -> List.of(
                    "Ask a willing person to pose as if advertising a nearby landmark.",
                    "Find two willing people to create a funny movie-poster pose.",
                    "Ask a willing person to choose between two visible public objects and photograph the choice.",
                    "Ask a willing person to act as a tour guide while you pose as a surprised visitor.",
                    "Create a fake street-interview scene with a willing participant using visible surroundings.",
                    "Ask a willing friend to make a dramatic advertisement for a nearby building.",
                    "Recreate a harmless movie-poster pose beside public architecture with a willing person.",
                    "Photograph public art while a willing participant acts as its spokesperson."
            );
            default -> List.of(
                    "Ask a willing person to help create a funny pose or composition.",
                    "Ask a willing person to choose one of two visible objects and photograph the choice.",
                    "Ask a willing participant to perform a harmless funny action with you.",
                    "Create a short visual role-play with a willing participant.",
                    "Ask a willing friend to make a ridiculous advertisement for an ordinary object.",
                    "Convince a willing participant to pose with you as if you won an imaginary award.",
                    "Ask two willing people to recreate a playful movie-poster scene.",
                    "Transform an ordinary setting into a game-show moment with a willing collaborator."
            );
        };
    }

    private void removeSafetyWording(Challenge challenge) {
        if (challenge == null || challenge.getChallenge() == null) {
            return;
        }
        challenge.setChallenge(challenge.getChallenge()
                .replaceAll("(?i)\\bsafe\\s+(?=(visible|nearby|stationary|creative|geometric|object|surface))", "")
                .replaceAll("\\s{2,}", " ")
                .trim());
    }

    private Challenge copyChallenge(
            Challenge source,
            String id,
            String playerId,
            String difficulty,
            int points
    ) {
        Challenge copy = new Challenge();
        copy.setId(id);
        copy.setPlayerId(playerId);
        copy.setChallenge(source.getChallenge());
        copy.setDifficulty(difficulty);
        copy.setPoints(points);
        copy.setCategory(source.getCategory() == null ? "observation" : source.getCategory());
        return copy;
    }

    private String validChaosDifficulty(String difficulty, int index) {
        return "easy".equals(difficulty) || "medium".equals(difficulty) || "hard".equals(difficulty)
                ? difficulty
                : new String[]{"easy", "medium", "hard"}[index % 3];
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    public String normalizeChallengeText(String value) {
        return normalize(value)
                .replace("waiter", "staff")
                .replace("server", "staff")
                .replace("take a photo", "photograph")
                .replace("take photo", "photograph");
    }
}

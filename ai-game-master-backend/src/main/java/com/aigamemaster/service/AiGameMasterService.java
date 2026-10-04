package com.aigamemaster.service;

import com.aigamemaster.model.Challenge;
import com.aigamemaster.model.Player;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Service
public class AiGameMasterService implements ChallengeAiProvider {

    private static final String MODEL = "gemini-3.8-flash";
    private static final boolean USE_LOCAL_CHALLENGE_FALLBACK =
            Boolean.parseBoolean(System.getProperty("ai.game.local-challenges", "true"));
    private static final Logger LOGGER = LoggerFactory.getLogger(AiGameMasterService.class);
    private static final Random RANDOM = new Random();

    private final ObjectMapper objectMapper;

    public AiGameMasterService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Challenge> generateChallenges(
            String mode,
            String environment,
            String difficulty,
            int numberOfChallenges,
            List<Player> players
    ) {
        return generateChallengesInternal(
                mode, environment, difficulty, numberOfChallenges, players, Set.of()
        );
    }

    private List<Challenge> generateChallengesInternal(
            String mode,
            String environment,
            String difficulty,
            int numberOfChallenges,
            List<Player> players,
            Set<String> excludedChallenges
    ) {
        if (USE_LOCAL_CHALLENGE_FALLBACK) {
            LOGGER.info("Using local challenge fallback");
            return generateFallback(mode, environment, difficulty, numberOfChallenges, players, excludedChallenges);
        }

        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.warn("GEMINI_API_KEY is not configured; using local challenge fallback");
            return generateFallback(mode, environment, difficulty, numberOfChallenges, players, excludedChallenges);
        }

        LOGGER.info("Gemini challenge generation is active using model {}", MODEL);
        Client client = Client.builder().apiKey(apiKey).build();
        GenerateContentConfig config = GenerateContentConfig.builder()
                .candidateCount(1)
                .maxOutputTokens(4096)
                .responseMimeType("application/json")
                .build();

        try {
            String response = client.models
                    .generateContent(
                            MODEL,
                            buildPrompt(mode, environment, difficulty, numberOfChallenges, players, excludedChallenges),
                            config
                    )
                    .text();
            return objectMapper.readValue(response, new TypeReference<List<Challenge>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Gemini returned invalid challenge JSON", exception);
        } catch (RuntimeException exception) {
            LOGGER.warn("Gemini generation failed; using safe local challenge fallback", exception);
            return generateFallback(mode, environment, difficulty, numberOfChallenges, players, excludedChallenges);
        }
    }

    @Override
    public List<Challenge> generateChallenges(
            String mode,
            String environment,
            String difficulty,
            int numberOfChallenges,
            List<Player> players,
            Set<String> excludedChallenges
    ) {
        return generateChallengesInternal(
                mode, environment, difficulty, numberOfChallenges, players,
                excludedChallenges == null ? Set.of() : excludedChallenges
        );
    }

    private List<Challenge> generateFallback(
            String mode,
            String environment,
            String difficulty,
            int numberOfChallenges,
            List<Player> players,
            Set<String> excludedChallenges
    ) {
        List<String> pool = new ArrayList<>(switch (environment) {
            case "indoor" -> List.of(
                    "Ask a willing person to help create a funny pose with an indoor object and photograph the result.",
                    "Ask a willing person to choose one of two visible objects and photograph the chosen object in a creative composition.",
                    "If they agree, ask a willing participant to create a short funny role-play scene with you and photograph the final pose.",
                    "Ask a willing person to choose an indoor object and pose with it in a playful game-show-style photo.",
                    "Ask a willing friend to invent a ridiculous use for an indoor object and pose as its spokesperson.",
                    "Create a fake product advertisement with a willing participant and photograph the final scene.",
                    "Build a tiny indoor stage from available objects and ask a willing participant to pose on it.",
                    "Create a funny before-and-after transformation of an indoor seating area with a willing collaborator.",
                    "Photograph a distinctive decoration while a willing participant poses as its tour guide."
            );
            case "outdoor" -> List.of(
                    "Ask a willing person to pose as if advertising a nearby landmark and photograph the result.",
                    "Ask a willing person to choose between two visible objects and photograph the choice.",
                    "Find a willing participant to create a funny tourist-advertisement pose with you and photograph it.",
                    "If two willing people agree, ask them to recreate a playful movie-poster pose and photograph the result.",
                    "Ask a willing friend to act as a tour guide while you pose as an amazed visitor.",
                    "Create a fake outdoor news report with a willing participant beside a landmark.",
                    "Ask a willing person to help turn a public outdoor object into a ridiculous advertisement.",
                    "Create a harmless outdoor role-play scene inspired by the nearby buildings or landscape.",
                    "Photograph a natural feature while a willing participant acts as its ambassador."
            );
            case "restaurant" -> List.of(
                    "Ask a willing staff member to pose with you while holding the menu and photograph the moment.",
                    "If a willing staff member agrees, ask them to help create a funny table arrangement and photograph it.",
                    "Ask a willing staff member to choose between two harmless visible options and photograph the choice.",
                    "If a willing staff member agrees, ask them to choose the most visually interesting table item and photograph it with you.",
                    "Ask a willing staff member to help create a dramatic food-commercial pose without interrupting their work.",
                    "Ask a willing friend to pretend to be a famous food critic while you pose beside the table.",
                    "Create a funny waiter-and-customer role-play with a willing participant.",
                    "Ask a willing person to choose a table item and help advertise it as a luxury product.",
                    "Create a collaborative scene with visible napkins, cutlery, and decorations."
            );
            case "bar" -> List.of(
                    "Ask a willing person to do a high-five with you and photograph the moment.",
                    "Ask a willing person to pose with you as if you won an imaginary award and photograph the result.",
                    "Ask someone willing to choose a color, then create a photo with three visible objects of that color.",
                    "Ask a willing friend or participant to recreate a funny dramatic scene and photograph the final pose.",
                    "Ask two willing people to help create a ridiculous band photo using visible decorations.",
                    "Ask a willing person to choose a music category and create a funny pose representing it.",
                    "Create a mock awards ceremony with a willing participant and visible decorations.",
                    "Turn a visible table object into a luxury product advertisement with a willing person.",
                    "Create a harmless celebrity-arrival scene with a willing participant."
            );
            case "street" -> List.of(
                    "Ask a willing person to pose as if advertising a nearby landmark and photograph the result.",
                    "Find two willing people to create a funny movie-poster pose and photograph it.",
                    "Ask a willing person to choose between two visible public objects and photograph the choice.",
                    "Find a willing participant to pose with you in a playful tourist-advertisement scene.",
                    "Ask a willing person to act as a tour guide while you pose as a surprised visitor.",
                    "Create a fake street-interview scene with a willing participant using visible surroundings.",
                    "Ask a willing friend to make a dramatic advertisement for a nearby building.",
                    "Ask a willing person to recreate a harmless movie-poster pose beside public architecture.",
                    "Photograph public art while a willing participant acts as its spokesperson."
            );
            default -> List.of(
                    "Ask a willing person to help create a funny pose or composition and photograph the result.",
                    "Ask a willing person to choose one of two visible objects and photograph the choice.",
                    "If they agree, ask a willing participant to perform a harmless funny action with you and photograph the result.",
                    "Create a short visual role-play with a willing participant and photograph the final pose.",
                    "Ask a willing friend to make a ridiculous advertisement for an ordinary object.",
                    "Convince a willing participant to pose with you as if you won an imaginary award.",
                    "Ask two willing people to recreate a playful movie-poster scene.",
                    "Transform an ordinary setting into a game-show moment with a willing collaborator.",
                    "Ask a willing person to choose an object and incorporate it into a funny performance pose."
            );
        });
        Collections.shuffle(pool, RANDOM);
        int difficultyOffset = "hard".equals(difficulty) ? 8 : "medium".equals(difficulty) ? 4 : 0;
        List<Challenge> generated = new ArrayList<>();
        for (Player player : players) {
            for (int index = 0; index < numberOfChallenges; index++) {
                Challenge challenge = new Challenge();
                challenge.setId("fallback-" + generated.size());
                challenge.setPlayerId(player.getId());
                int offset = mode.equals("mirror") ? 0 : generated.size();
                String text = pool.get((index + offset + difficultyOffset) % pool.size());
                challenge.setChallenge(text);
                challenge.setDifficulty("chaos".equals(mode)
                        ? randomDifficulty()
                        : difficulty);
                challenge.setCategory("observation");
                generated.add(challenge);
            }
        }
        return generated;
    }

    private String randomDifficulty() {
        return List.of("easy", "medium", "hard").get(RANDOM.nextInt(3));
    }

    @Override
    public int recommendDuration(
            String mode,
            String environment,
            String difficulty,
            int numberOfChallenges,
            List<Player> players
    ) {
        if (USE_LOCAL_CHALLENGE_FALLBACK) {
            int recommendation = localDurationRecommendation(mode, environment, difficulty, numberOfChallenges, players);
            LOGGER.info("Using local duration recommendation because Gemini is unavailable: {} minutes", recommendation);
            return recommendation;
        }

        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            int recommendation = localDurationRecommendation(mode, environment, difficulty, numberOfChallenges, players);
            LOGGER.info("Gemini duration recommendation unavailable; using local recommendation of {} minutes", recommendation);
            return recommendation;
        }

        LOGGER.info("Gemini duration recommendation is active using model {}", MODEL);
        try {
            Client client = Client.builder().apiKey(apiKey).build();
            String response = client.models.generateContent(
                    MODEL,
                    """
                    Recommend exactly one duration in minutes from 10, 20, 30, 45, or 60.
                    Return only the integer.
                    Consider mode=%s, environment=%s, difficulty=%s, challenges=%d, players=%d.
                    """.formatted(mode, environment, difficulty, numberOfChallenges, players.size()),
                    GenerateContentConfig.builder().maxOutputTokens(8).build()
            ).text().trim();
            int recommendation = Integer.parseInt(response.replaceAll("[^0-9]", ""));
            if (List.of(10, 20, 30, 45, 60).contains(recommendation)) {
                LOGGER.info("Gemini recommended {} minutes for the game", recommendation);
                return recommendation;
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Gemini duration recommendation failed; using local recommendation", exception);
        }
        int recommendation = localDurationRecommendation(mode, environment, difficulty, numberOfChallenges, players);
        LOGGER.info("Using local duration recommendation of {} minutes", recommendation);
        return recommendation;
    }

    private int localDurationRecommendation(
            String mode,
            String environment,
            String difficulty,
            int numberOfChallenges,
            List<Player> players
    ) {
        int base = numberOfChallenges * 5;
        int difficultyAdjustment = "hard".equals(difficulty) ? 15 : "medium".equals(difficulty) ? 5 : 0;
        int modeAdjustment = "balanced".equals(mode) ? 5 : "chaos".equals(mode) ? 10 : 0;
        int environmentAdjustment = switch (environment) {
            case "restaurant", "bar" -> 5;
            case "street", "outdoor" -> 10;
            default -> 0;
        };
        int playerAdjustment = Math.max(0, players.size() - 2) * 5;
        int recommendation = base + difficultyAdjustment + modeAdjustment + environmentAdjustment + playerAdjustment;
        if (recommendation <= 10) return 10;
        if (recommendation <= 20) return 20;
        if (recommendation <= 30) return 30;
        if (recommendation <= 45) return 45;
        return 60;
    }

    private String buildPrompt(
            String mode,
            String environment,
            String difficulty,
            int numberOfChallenges,
            List<Player> players,
            Set<String> excludedChallenges
    ) {
        try {
            return """
                    Return only a valid JSON array of physical, photo-verifiable challenge objects.
                    Each object must contain: id, playerId, challenge, difficulty, points, category.
                    Generate %d challenges for each player.
                    mode=%s, environment=%s, difficulty=%s, players=%s.
                    Previously used normalized challenge ideas to exclude=%s.
                    Every challenge must be completed and judged from one photograph showing an observable physical result.
                    Mirror uses identical challenge text, difficulty, and points for every player.
                    Balanced uses different challenge text at the configured difficulty and equivalent visual complexity.
                    Chaos uses unpredictable different photo challenges and chooses difficulty per challenge.
                    Easy means one obvious visual condition; medium means two visual conditions;
                    hard means a safe creative multi-condition visual result.
                    You are a game master creating memorable real-world multiplayer party-game
                    moments, not a photography scavenger hunt. Prefer asking, persuading,
                    recreating, performing, improvising, posing, choosing, collaborating, acting,
                    and transforming ordinary situations. Roughly half of the challenges should
                    involve talking with friends, asking a willing stranger or staff member for
                    help, posing, choosing between options, harmless collaboration, or short
                    visual role-play. Keep the remaining challenges as a mix of creative physical
                    tasks, observation, and environment-specific compositions. Do not make every
                    challenge social.
                    Avoid generic shape, color, pattern, circle, triangle, symmetry, and nearby-
                    object scavenger tasks unless part of a substantially more creative social
                    scene. Every challenge should create a memorable visible moment. Social
                    challenges may ask a willing person to pose, choose between visible options,
                    or help with a harmless funny action, but only if they freely agree.
                    If someone declines, the challenge must remain harmless and skippable.
                    Never pressure, threaten, deceive, embarrass, harass, touch, or photograph
                    anyone without consent. Never require personal belongings, private areas,
                    or interrupting someone who is clearly busy.
                    Environment must strongly constrain the objects and setting:
                    indoor uses rooms, furniture, objects, decorations, or visible structures;
                    outdoor uses plants, buildings, paths, signs, or natural elements;
                    restaurant uses tables, menus, plates, decorations, or food presentation;
                    bar uses decorations, tables, and non-alcohol visual objects;
                    street uses only safe stationary surroundings and never traffic or road crossing;
                    anywhere uses general safe visual objects.
                    The photograph is evidence of the completed visible result. Every challenge
                    must be provable from one photograph; do not require audio, explanations,
                    written evidence, or invisible conversations. Use action verbs such as ask,
                    convince, recreate, perform, pose, improvise, collaborate, choose, act,
                    create, arrange, or photograph.
                    Never use describe, explain, name, identify a sound, tell, write, or say.
                    Do not include dangerous, illegal, sexual, harmful, private-information,
                    harassment, traffic, weapon, drug, intoxication, trespassing, or property-damage actions.
                    """.formatted(
                    numberOfChallenges,
                    mode,
                    environment,
                    difficulty,
                    objectMapper.writeValueAsString(players),
                    excludedChallenges
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize players for Gemini", exception);
        }
    }
}

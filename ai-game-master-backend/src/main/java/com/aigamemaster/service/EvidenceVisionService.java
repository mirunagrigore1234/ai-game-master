package com.aigamemaster.service;

import com.aigamemaster.model.Evidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class EvidenceVisionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceVisionService.class);
    private static final String MODEL = "gemini-3.8-flash";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public EvidenceVisionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void analyze(Evidence evidence, String challengeText) {
        evidence.setStatus("analyzing");
        try {
            VisionResult result = requestAnalysis(evidence.getImagePath(), challengeText);
            evidence.setAiApproved(result.approved());
            evidence.setAiConfidence(result.confidence());
            evidence.setAiAnalysis(result.analysis());
            evidence.setStatus("analyzed");
        } catch (RuntimeException | IOException | InterruptedException exception) {
            evidence.setAiApproved(null);
            evidence.setAiConfidence(null);
            evidence.setAiAnalysis("AI analysis failed. You can retry.");
            evidence.setStatus("submitted");
            LOGGER.warn("Gemini evidence analysis failed for {}", evidence.getEvidenceId(), exception);
        }
    }

    private VisionResult requestAnalysis(String imagePath, String challengeText) throws IOException, InterruptedException {
        String apiKey = System.getenv("GEMINI_API_KEY");
        LOGGER.info("Gemini evidence request: apiKeyPresent={}, model={}",
                apiKey != null && !apiKey.isBlank(), MODEL);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }

        String mimeType = Files.probeContentType(Path.of(imagePath));
        if (mimeType == null || !mimeType.startsWith("image/")) {
            mimeType = "image/jpeg";
        }
        byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));
        String imageData = Base64.getEncoder().encodeToString(imageBytes);
        String prompt = """
                Evaluate only whether this photograph visibly satisfies the exact challenge below.
                This is evidence evaluation for a real-world multiplayer game.
                Return only JSON with approved (boolean), confidence (number from 0.0 to 1.0),
                and analysis (short explanation). Do not award points, complete or reject the
                challenge, change scores, or make a voting decision.
                Confidence means how certain you are about your visual judgment, not whether the
                evidence is approved or rejected. Calibrate it from the clarity of the visible
                evidence and the exact challenge requirements. Use 1.0 only when the evidence is
                extremely clear and unambiguous. Confidence of 0.9 or higher should be rare.
                If the evidence is ambiguous, unclear, partial, obstructed, or leaves meaningful
                doubt, use a noticeably lower confidence. A rejection may have high confidence
                when the mismatch is visually obvious, and an approval may have high confidence
                when the challenge is clearly satisfied. Do not default to 1.0 and do not use
                confidence as a synonym for approved/rejected.
                Exact challenge: %s
                """.formatted(challengeText);
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "contents", new Object[] {
                        Map.of("parts", new Object[] {
                                Map.of("text", prompt),
                                Map.of("inline_data", Map.of("mime_type", mimeType, "data", imageData))
                        })
                }
        ));
        URI endpoint = URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                + MODEL + ":generateContent?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        LOGGER.info("Gemini evidence request details: method=POST, endpoint={}, mimeType={}, imageBytes={}, challenge={}",
                endpoint.toString().replace("?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8), "?key=[REDACTED]"),
                mimeType, imageBytes.length, challengeText);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            LOGGER.error("Gemini evidence HTTP request failed: {}", exception.getMessage(), exception);
            throw exception;
        }
        LOGGER.info("Gemini evidence response: status={}, body={}", response.statusCode(), response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.error("Gemini evidence HTTP error: status={}, body={}",
                    response.statusCode(), response.body());
            throw new IOException("Gemini returned HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        LOGGER.info("Gemini evidence model text: {}", text);
        if (text.isBlank()) {
            throw new IOException("Gemini response did not contain candidates[0].content.parts[0].text");
        }
        JsonNode result = objectMapper.readTree(stripJsonFence(text));
        if (!result.has("approved") || !result.get("approved").isBoolean()
                || !result.has("confidence") || !result.get("confidence").isNumber()
                || !result.has("analysis") || !result.get("analysis").isTextual()) {
            LOGGER.error("Gemini evidence JSON shape mismatch: expected boolean approved, numeric confidence, "
                    + "text analysis; parsed={}", result);
            throw new IOException("Gemini returned unexpected evidence JSON: " + result);
        }
        double confidence = result.get("confidence").asDouble();
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IOException("Gemini returned invalid confidence");
        }
        return new VisionResult(result.get("approved").asBoolean(), confidence, result.get("analysis").asText());
    }

    private String stripJsonFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    private record VisionResult(boolean approved, double confidence, String analysis) {
    }
}

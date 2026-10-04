package com.aigamemaster.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Evidence {

    private String evidenceId;
    private String challengeId;
    private String playerId;
    private String imageReference;
    private String status;
    private Boolean aiApproved;
    private Double aiConfidence;
    private String aiAnalysis;
    private Instant createdAt;
    private List<Vote> votes = new ArrayList<>();
    private String finalDecision;
    private boolean pointsAwarded;

    public Evidence() {
    }

    public Evidence(String evidenceId, String challengeId, String playerId, String imageReference) {
        this.evidenceId = evidenceId;
        this.challengeId = challengeId;
        this.playerId = playerId;
        this.imageReference = imageReference;
        this.status = "submitted";
        this.createdAt = Instant.now();
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public String getPlayerId() {
        return playerId;
    }

    @JsonIgnore
    public String getImageReference() {
        return imageReference;
    }

    @JsonIgnore
    public String getImagePath() {
        return imageReference;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getAiApproved() {
        return aiApproved;
    }

    public Double getAiConfidence() {
        return aiConfidence;
    }

    public String getAiAnalysis() {
        return aiAnalysis;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Vote> getVotes() {
        return votes;
    }

    public String getFinalDecision() {
        return finalDecision;
    }

    public boolean isPointsAwarded() {
        return pointsAwarded;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAiApproved(Boolean aiApproved) {
        this.aiApproved = aiApproved;
    }

    public void setAiConfidence(Double aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public void setAiAnalysis(String aiAnalysis) {
        this.aiAnalysis = aiAnalysis;
    }

    public void setFinalDecision(String finalDecision) {
        this.finalDecision = finalDecision;
    }

    public void setPointsAwarded(boolean pointsAwarded) {
        this.pointsAwarded = pointsAwarded;
    }
}

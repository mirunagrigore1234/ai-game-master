package com.aigamemaster.model;

public class Vote {

    private String evidenceId;
    private String playerId;
    private String decision;

    public Vote() {
    }

    public Vote(String evidenceId, String playerId, String decision) {
        this.evidenceId = evidenceId;
        this.playerId = playerId;
        this.decision = decision;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getDecision() {
        return decision;
    }
}

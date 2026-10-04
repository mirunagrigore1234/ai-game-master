package com.aigamemaster.model;

import java.time.Instant;

public record EvidenceResponse(
        String evidenceId,
        String challengeId,
        String playerId,
        String playerName,
        String challenge,
        String imageUrl,
        String status,
        Boolean aiApproved,
        Double aiConfidence,
        String aiAnalysis,
        Instant createdAt,
        int approveVotes,
        int rejectVotes,
        int submittedVoteCount,
        int totalEligibleVoters,
        String finalDecision,
        int pointsAwarded,
        String currentVoterDecision
) {
}

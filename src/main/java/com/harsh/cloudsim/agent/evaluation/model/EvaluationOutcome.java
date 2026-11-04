package com.harsh.cloudsim.agent.evaluation.model;

import java.util.Objects;

public record EvaluationOutcome(
        String evaluationId,
        String executionId,
        long evaluatedAtEpochMs,
        SloStatus sloStatus,
        double latencyDeltaMs,
        boolean rollbackRecommended,
        double multiplierAdjustmentDelta,
        String evaluationSummary
) {
    public EvaluationOutcome {
        Objects.requireNonNull(evaluationId, "Evaluation ID cannot be null.");
        Objects.requireNonNull(executionId, "Execution ID cannot be null.");
        Objects.requireNonNull(sloStatus, "SLO status cannot be null.");
        Objects.requireNonNull(evaluationSummary, "Summary cannot be null.");
    }
}
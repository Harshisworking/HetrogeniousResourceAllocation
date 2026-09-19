package com.harsh.cloudsim.agent.model;

import java.util.Objects;

/**
 * Represents a structured recommendation for placing one VM on one host.
 *
 * Unlike a plain host number, this object preserves:
 * - which VM the decision concerns;
 * - which host was selected;
 * - how confident the decision-maker is;
 * - why the host was selected;
 * - which component produced the decision.
 *
 * @param vmId         ID of the VM being placed
 * @param targetHostId ID of the recommended host
 * @param confidence   confidence score between 0.0 and 1.0
 * @param reason       human-readable explanation
 * @param source       component that produced the decision
 */
public record PlacementDecision(
        long vmId,
        long targetHostId,
        double confidence,
        String reason,
        DecisionSource source
) {

    /**
     * Compact canonical constructor used to validate every decision.
     */
    public PlacementDecision {
        if (vmId < 0) {
            throw new IllegalArgumentException(
                    "VM ID must not be negative."
            );
        }

        if (targetHostId < 0) {
            throw new IllegalArgumentException(
                    "Target host ID must not be negative."
            );
        }

        if (!Double.isFinite(confidence)
                || confidence < 0.0
                || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "Confidence must be a finite number between 0.0 and 1.0."
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Decision reason must not be blank."
            );
        }

        Objects.requireNonNull(
                source,
                "Decision source must not be null."
        );

        /*
         * Records are immutable, but constructor parameters can be cleaned
         * before Java assigns them to the final record fields.
         */
        reason = reason.trim();
    }
}
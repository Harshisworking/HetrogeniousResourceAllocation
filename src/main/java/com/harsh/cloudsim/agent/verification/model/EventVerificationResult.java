package com.harsh.cloudsim.agent.verification.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result returned by the Event Verification Agent.
 *
 * @param status                 final verification status
 * @param verificationScore      trust score between 0.0 and 1.0
 * @param normalizedSourceDomain normalized domain used during verification
 * @param reasons                human-readable verification findings
 */
public record EventVerificationResult(
        VerificationStatus status,
        double verificationScore,
        String normalizedSourceDomain,
        List<String> reasons
) {

    public EventVerificationResult {
        Objects.requireNonNull(
                status,
                "Verification status must not be null."
        );

        if (!Double.isFinite(verificationScore)
                || verificationScore < 0.0
                || verificationScore > 1.0) {
            throw new IllegalArgumentException(
                    "Verification score must be a finite number "
                            + "between 0.0 and 1.0."
            );
        }

        if (normalizedSourceDomain == null
                || normalizedSourceDomain.isBlank()) {
            throw new IllegalArgumentException(
                    "Normalized source domain must not be blank."
            );
        }

        Objects.requireNonNull(
                reasons,
                "Verification reasons must not be null."
        );

        if (reasons.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one verification reason is required."
            );
        }

        if (reasons.stream().anyMatch(
                reason -> reason == null || reason.isBlank()
        )) {
            throw new IllegalArgumentException(
                    "Verification reasons must not contain blank values."
            );
        }

        normalizedSourceDomain =
                normalizedSourceDomain.trim().toLowerCase();

        /*
         * Produce a new immutable list so external code cannot modify
         * the result after verification.
         */
        reasons = reasons.stream()
                .map(String::trim)
                .toList();
    }
}
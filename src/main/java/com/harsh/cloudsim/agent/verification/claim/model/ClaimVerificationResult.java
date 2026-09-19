package com.harsh.cloudsim.agent.verification.claim.model;

import java.util.List;
import java.util.Objects;

/**
 * Structured result produced after comparing an event claim with
 * retrieved source-page evidence.
 *
 * @param verdict                       overall claim verdict
 * @param confidence                    confidence between 0.0 and 1.0
 * @param supportedFacts                facts supported by the evidence
 * @param missingOrContradictedFacts    missing or contradictory facts
 * @param evidenceExcerpts              short excerpts from the source
 * @param reasoning                     explanation of the verdict
 */
public record ClaimVerificationResult(
        ClaimVerdict verdict,
        double confidence,
        List<String> supportedFacts,
        List<String> missingOrContradictedFacts,
        List<String> evidenceExcerpts,
        String reasoning
) {

    public ClaimVerificationResult {
        Objects.requireNonNull(
                verdict,
                "Claim verdict must not be null."
        );

        if (!Double.isFinite(confidence)
                || confidence < 0.0
                || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "Claim-verification confidence must be "
                            + "between 0.0 and 1.0."
            );
        }

        supportedFacts = validateAndCopyList(
                supportedFacts,
                "Supported facts"
        );

        missingOrContradictedFacts =
                validateAndCopyList(
                        missingOrContradictedFacts,
                        "Missing or contradicted facts"
                );

        evidenceExcerpts = validateAndCopyList(
                evidenceExcerpts,
                "Evidence excerpts"
        );

        if (reasoning == null || reasoning.isBlank()) {
            throw new IllegalArgumentException(
                    "Claim-verification reasoning must not be blank."
            );
        }

        reasoning = reasoning.trim();

        validateVerdictConsistency(
                verdict,
                supportedFacts,
                missingOrContradictedFacts,
                evidenceExcerpts
        );
    }

    private static List<String> validateAndCopyList(
            List<String> values,
            String fieldName
    ) {
        Objects.requireNonNull(
                values,
                fieldName + " must not be null."
        );

        if (values.stream().anyMatch(
                value -> value == null
                        || value.isBlank()
        )) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not contain null or blank values."
            );
        }

        return values.stream()
                .map(String::trim)
                .toList();
    }

    private static void validateVerdictConsistency(
            ClaimVerdict verdict,
            List<String> supportedFacts,
            List<String> missingOrContradictedFacts,
            List<String> evidenceExcerpts
    ) {
        switch (verdict) {
            case SUPPORTED -> {
                if (supportedFacts.isEmpty()) {
                    throw new IllegalArgumentException(
                            "A SUPPORTED result requires at least "
                                    + "one supported fact."
                    );
                }

                if (!missingOrContradictedFacts.isEmpty()) {
                    throw new IllegalArgumentException(
                            "A SUPPORTED result cannot contain "
                                    + "missing or contradicted facts."
                    );
                }

                if (evidenceExcerpts.isEmpty()) {
                    throw new IllegalArgumentException(
                            "A SUPPORTED result requires at least "
                                    + "one evidence excerpt."
                    );
                }
            }

            case PARTIALLY_SUPPORTED -> {
                if (supportedFacts.isEmpty()) {
                    throw new IllegalArgumentException(
                            "A PARTIALLY_SUPPORTED result requires "
                                    + "at least one supported fact."
                    );
                }

                if (missingOrContradictedFacts.isEmpty()) {
                    throw new IllegalArgumentException(
                            "A PARTIALLY_SUPPORTED result requires "
                                    + "at least one missing or "
                                    + "contradicted fact."
                    );
                }

                if (evidenceExcerpts.isEmpty()) {
                    throw new IllegalArgumentException(
                            "A PARTIALLY_SUPPORTED result requires "
                                    + "at least one evidence excerpt."
                    );
                }
            }

            case NOT_SUPPORTED -> {
                if (missingOrContradictedFacts.isEmpty()) {
                    throw new IllegalArgumentException(
                            "A NOT_SUPPORTED result requires at least "
                                    + "one missing or contradicted fact."
                    );
                }
            }
        }
    }
}
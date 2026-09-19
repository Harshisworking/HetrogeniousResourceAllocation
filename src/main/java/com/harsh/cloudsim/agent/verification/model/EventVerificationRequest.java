package com.harsh.cloudsim.agent.verification.model;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;

import java.util.Objects;

/**
 * Input supplied to the Event Verification Agent.
 *
 * @param assessment     validated assessment produced by the Event Agent
 * @param sourceReference independently supplied source URL or domain
 */
public record EventVerificationRequest(
        EventImpactAssessment assessment,
        String sourceReference
) {

    public EventVerificationRequest {
        Objects.requireNonNull(
                assessment,
                "Event assessment must not be null."
        );

        if (sourceReference == null
                || sourceReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Source reference must not be blank."
            );
        }

        sourceReference = sourceReference.trim();
    }
}
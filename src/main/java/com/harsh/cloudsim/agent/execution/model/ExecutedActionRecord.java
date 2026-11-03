package com.harsh.cloudsim.agent.execution.model;

import com.harsh.cloudsim.agent.model.DecisionSource;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingAction;

import java.util.Objects;

/**
 * Immutable audit record for a single executed VM/Pod scaling operation.
 */
public record ExecutedActionRecord(
        long vmId,
        int vmType,
        VmScalingAction action,
        long requestedHostId,
        long actualHostId,
        boolean success,
        DecisionSource decisionSource,
        String auditMessage
) {
    public ExecutedActionRecord {
        if (vmId < -1) {
            throw new IllegalArgumentException("VM ID must be -1 (unassigned) or non-negative.");
        }
        if (vmType != 0 && vmType != 1) {
            throw new IllegalArgumentException("VM type must be 0 (HEAVY x86) or 1 (LIGHT ARM).");
        }
        if (requestedHostId < 0) {
            throw new IllegalArgumentException("Requested host ID must not be negative.");
        }
        if (actualHostId < -1) {
            throw new IllegalArgumentException("Actual host ID must be -1 (none) or non-negative.");
        }
        Objects.requireNonNull(action, "Scaling action must not be null.");
        Objects.requireNonNull(decisionSource, "Decision source must not be null.");
        Objects.requireNonNull(auditMessage, "Audit message must not be null.");
        auditMessage = auditMessage.trim();
        if (auditMessage.isBlank()) {
            throw new IllegalArgumentException("Audit message must not be blank.");
        }
    }
}

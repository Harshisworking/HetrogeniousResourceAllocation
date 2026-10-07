package com.harsh.cloudsim.agent.optimisation.model;

import java.util.Objects;

public record VmScalingProposal(
        int vmType,
        long targetHostId,
        long requestedPes,
        long requestedRamMb,
        VmScalingAction action,
        String reason
) {
    public VmScalingProposal {
        if (targetHostId < 0) {
            throw new IllegalArgumentException("Target host ID must not be negative.");
        }
        if (requestedPes < 1) {
            throw new IllegalArgumentException("Requested PEs must be at least 1.");
        }
        if (requestedRamMb < 1) {
            throw new IllegalArgumentException("Requested RAM must be at least 1 MB.");
        }
        Objects.requireNonNull(action, "Scaling action cannot be null.");
        Objects.requireNonNull(reason, "Reason cannot be null.");
    }
}
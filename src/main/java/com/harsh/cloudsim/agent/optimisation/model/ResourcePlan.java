package com.harsh.cloudsim.agent.optimisation.model;

import java.util.List;
import java.util.Objects;

public record ResourcePlan(
        String planId,
        int currentVmCount,
        int targetVmCount,
        double projectedCpuDemand,
        double projectedRamDemand,
        List<VmScalingProposal> scalingProposals
) {
    public ResourcePlan {
        Objects.requireNonNull(planId, "Plan ID cannot be null.");
        if (currentVmCount < 0 || targetVmCount < 0) {
            throw new IllegalArgumentException("VM counts cannot be negative.");
        }
        if (projectedCpuDemand < 0 || projectedRamDemand < 0) {
            throw new IllegalArgumentException("Projected demands cannot be negative.");
        }
        Objects.requireNonNull(scalingProposals, "Scaling proposals list cannot be null.");
        scalingProposals = List.copyOf(scalingProposals);
    }
}
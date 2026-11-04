package com.harsh.cloudsim.agent.evaluation.model;

public record ObservedPerformance(
        long observationWindowMs,
        double observedP95LatencyMs,
        double observedErrorRateRatio,
        double actualCpuUtilization,
        int activeReplicas
) {
    public ObservedPerformance {
        if (observationWindowMs <= 0 || observedP95LatencyMs < 0 || actualCpuUtilization < 0) {
            throw new IllegalArgumentException("Performance metrics must be non-negative.");
        }
    }
}
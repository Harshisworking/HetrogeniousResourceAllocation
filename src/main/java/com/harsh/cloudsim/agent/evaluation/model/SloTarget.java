package com.harsh.cloudsim.agent.evaluation.model;

import java.util.Objects;

public record SloTarget(
        String serviceName,
        double maxP95LatencyMs,
        double maxErrorRateRatio,
        double targetCpuUtilization
) {
    public SloTarget {
        Objects.requireNonNull(serviceName, "Service name cannot be null.");
        if (maxP95LatencyMs <= 0 || maxErrorRateRatio < 0.0 || maxErrorRateRatio > 1.0) {
            throw new IllegalArgumentException("Invalid SLO target parameters.");
        }
    }
}
package com.harsh.cloudsim.agent.prediction.model;

public record WorkloadForecast(
        int forecastHorizonMinutes,
        double expectedRequestsPerSecond,
        int expectedConcurrentUsers,
        double predictedCpuDemand,
        double predictedMemoryDemand,
        int recommendedVmCount,
        double predictionConfidence,
        String predictionSource,
        String reasoning
) {
    public WorkloadForecast {
        if (forecastHorizonMinutes < 0) {
            throw new IllegalArgumentException("Forecast horizon cannot be negative.");
        }
        if (expectedRequestsPerSecond < 0 || Double.isNaN(expectedRequestsPerSecond) || Double.isInfinite(expectedRequestsPerSecond)) {
            throw new IllegalArgumentException("Expected RPS must be a valid positive number.");
        }
        if (expectedConcurrentUsers < 0) {
            throw new IllegalArgumentException("Expected concurrent users cannot be negative.");
        }
        if (predictedCpuDemand < 0 || Double.isNaN(predictedCpuDemand) || Double.isInfinite(predictedCpuDemand)) {
            throw new IllegalArgumentException("Predicted CPU demand must be a valid positive number.");
        }
        if (predictedMemoryDemand < 0 || Double.isNaN(predictedMemoryDemand) || Double.isInfinite(predictedMemoryDemand)) {
            throw new IllegalArgumentException("Predicted Memory demand must be a valid positive number.");
        }
        if (recommendedVmCount < 0) {
            throw new IllegalArgumentException("Recommended VM count cannot be negative.");
        }
        if (predictionConfidence < 0.0 || predictionConfidence > 1.0) {
            throw new IllegalArgumentException("Prediction confidence must be between 0.0 and 1.0.");
        }
        if (predictionSource == null || predictionSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Prediction source cannot be null or empty.");
        }
        if (reasoning == null || reasoning.trim().isEmpty()) {
            throw new IllegalArgumentException("Reasoning cannot be null or empty.");
        }
    }
}
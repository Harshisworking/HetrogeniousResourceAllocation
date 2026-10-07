package com.harsh.cloudsim.agent.prediction;

import com.harsh.cloudsim.agent.prediction.model.WorkloadForecast;
import com.harsh.cloudsim.agent.monitoring.model.MetricsSnapshot;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;

public class DeterministicWorkloadPredictor implements WorkloadPredictor {

    private static final double CPU_LOAD_PER_USER_PERCENTAGE = 0.0005; 
    private static final double RAM_LOAD_PER_USER_MB = 2.5;

    @Override
    public WorkloadForecast predict(EventImpactAssessment assessment, EventVerificationResult verificationResult, MetricsSnapshot currentMetrics) {
        if (assessment == null || verificationResult == null || currentMetrics == null) {
            throw new IllegalArgumentException("Inputs to WorkloadPredictor cannot be null.");
        }

        if (verificationResult.status() != VerificationStatus.VERIFIED) {
            return new WorkloadForecast(
                    0,
                    currentMetrics.estimatedRequestsPerSecond(),
                    0, 
                    currentMetrics.averageCpuUtilization(),
                    currentMetrics.averageRamUtilization(),
                    currentMetrics.runningVms(),
                    1.0,
                    "Deterministic Fallback",
                    "Event not verified. Maintaining baseline operational forecast."
            );
        }

        double trafficMultiplier = assessment.trafficMultiplier();
        
        int predictedUsers = (int) (assessment.expectedUsers() * trafficMultiplier);
        double predictedRps = currentMetrics.estimatedRequestsPerSecond() * trafficMultiplier;

        double predictedCpu = currentMetrics.averageCpuUtilization() + (predictedUsers * CPU_LOAD_PER_USER_PERCENTAGE);
        double predictedRam = currentMetrics.averageRamUtilization() + (predictedUsers * RAM_LOAD_PER_USER_MB);

        int recommendedVms = currentMetrics.runningVms();
        if (predictedCpu > 0.8) {
            int additionalVmsNeeded = (int) Math.ceil((predictedCpu - 0.8) / 0.8);
            recommendedVms += additionalVmsNeeded;
        }

        return new WorkloadForecast(
                (int) assessment.leadTimeMinutes(),
                predictedRps,
                predictedUsers,
                predictedCpu,
                predictedRam,
                recommendedVms,
                assessment.confidence(),
                "Deterministic Baseline",
                String.format("Calculated based on verified event '%s' with traffic multiplier %.2f", assessment.eventName(), trafficMultiplier)
        );
    }
}
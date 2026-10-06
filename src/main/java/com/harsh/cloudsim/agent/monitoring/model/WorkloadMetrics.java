package com.harsh.cloudsim.agent.monitoring.model;

/**
 * Immutable observation of workload and cloudlet execution status.
 *
 * @param submittedCloudlets        Total cloudlets submitted to broker
 * @param waitingCloudlets          Cloudlets queued / waiting to execute
 * @param runningCloudlets          Cloudlets currently executing
 * @param finishedCloudlets         Cloudlets that successfully finished
 * @param failedCloudlets           Cloudlets that failed during execution
 * @param queueLength               Immediate waiting queue depth
 * @param estimatedRequestsPerSec   Arrival rate proxy (cloudlets / sec)
 * @param estimatedQueueDelaySec    Deterministic average queue delay proxy (sec)
 * @param errorRate                 Failure ratio in [0.0, 1.0]
 */
public record WorkloadMetrics(
        int submittedCloudlets,
        int waitingCloudlets,
        int runningCloudlets,
        int finishedCloudlets,
        int failedCloudlets,
        int queueLength,
        double estimatedRequestsPerSec,
        double estimatedQueueDelaySec,
        double errorRate
) {
    public WorkloadMetrics {
        if (submittedCloudlets < 0 || waitingCloudlets < 0 || runningCloudlets < 0
                || finishedCloudlets < 0 || failedCloudlets < 0 || queueLength < 0) {
            throw new IllegalArgumentException("Cloudlet counts and queue length must be non-negative.");
        }
        if (!Double.isFinite(estimatedRequestsPerSec) || estimatedRequestsPerSec < 0.0) {
            throw new IllegalArgumentException("Estimated requests per second must be a finite non-negative number.");
        }
        if (!Double.isFinite(estimatedQueueDelaySec) || estimatedQueueDelaySec < 0.0) {
            throw new IllegalArgumentException("Estimated queue delay must be a finite non-negative number.");
        }
        if (!Double.isFinite(errorRate) || errorRate < 0.0 || errorRate > 1.0) {
            throw new IllegalArgumentException("Error rate must be finite and within [0.0, 1.0].");
        }
    }
}
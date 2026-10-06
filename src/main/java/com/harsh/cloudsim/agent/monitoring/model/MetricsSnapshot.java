package com.harsh.cloudsim.agent.monitoring.model;

import java.util.List;
import java.util.Objects;

/**
 * Clean, immutable snapshot of CloudSim infrastructure and workload state.
 * Primary contract produced by Agent 3 (Monitoring) and consumed by downstream agents.
 */
public record MetricsSnapshot(
        long collectionSequence,
        double simulationTime,
        int totalHosts,
        int activeHosts,
        int totalVms,
        int runningVms,
        int waitingCloudlets,
        int runningCloudlets,
        int finishedCloudlets,
        int failedCloudlets,
        double averageCpuUtilization,
        double averageRamUtilization,
        double estimatedRequestsPerSecond,
        double estimatedQueueDelaySeconds,
        double errorRate,
        List<HostMetrics> hostMetrics,
        List<VmMetrics> vmMetrics,
        WorkloadMetrics workloadMetrics
) {
    public MetricsSnapshot {
        if (collectionSequence < 0) {
            throw new IllegalArgumentException("Collection sequence must not be negative.");
        }
        if (!Double.isFinite(simulationTime) || simulationTime < 0.0) {
            throw new IllegalArgumentException("Simulation time must be a finite non-negative number.");
        }
        if (totalHosts < 0 || activeHosts < 0 || totalVms < 0 || runningVms < 0
                || waitingCloudlets < 0 || runningCloudlets < 0 || finishedCloudlets < 0 || failedCloudlets < 0) {
            throw new IllegalArgumentException("Host, VM, and Cloudlet counts must not be negative.");
        }
        if (activeHosts > totalHosts) {
            throw new IllegalArgumentException("Active hosts cannot exceed total hosts.");
        }
        if (runningVms > totalVms) {
            throw new IllegalArgumentException("Running VMs cannot exceed total VMs.");
        }
        if (!Double.isFinite(averageCpuUtilization) || averageCpuUtilization < 0.0 || averageCpuUtilization > 1.0) {
            throw new IllegalArgumentException("Average CPU utilization must be finite and within [0.0, 1.0].");
        }
        if (!Double.isFinite(averageRamUtilization) || averageRamUtilization < 0.0 || averageRamUtilization > 1.0) {
            throw new IllegalArgumentException("Average RAM utilization must be finite and within [0.0, 1.0].");
        }
        if (!Double.isFinite(estimatedRequestsPerSecond) || estimatedRequestsPerSecond < 0.0) {
            throw new IllegalArgumentException("Estimated requests per second must be a finite non-negative number.");
        }
        if (!Double.isFinite(estimatedQueueDelaySeconds) || estimatedQueueDelaySeconds < 0.0) {
            throw new IllegalArgumentException("Estimated queue delay must be a finite non-negative number.");
        }
        if (!Double.isFinite(errorRate) || errorRate < 0.0 || errorRate > 1.0) {
            throw new IllegalArgumentException("Error rate must be finite and within [0.0, 1.0].");
        }
        Objects.requireNonNull(hostMetrics, "Host metrics list must not be null.");
        Objects.requireNonNull(vmMetrics, "Vm metrics list must not be null.");
        Objects.requireNonNull(workloadMetrics, "Workload metrics must not be null.");

        // Defensive immutable copy
        hostMetrics = List.copyOf(hostMetrics);
        vmMetrics = List.copyOf(vmMetrics);
    }
}
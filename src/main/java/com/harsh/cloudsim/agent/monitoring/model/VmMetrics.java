package com.harsh.cloudsim.agent.monitoring.model;

/**
 * Immutable metrics observation for a single CloudSim Virtual Machine.
 *
 * @param vmId                 Unique identifier of the VM
 * @param hostId               Identifier of the host on which VM resides (-1 if unassigned)
 * @param pesNumber            Allocated Processing Elements (cores)
 * @param totalMipsCapacity    Total computational capacity in MIPS
 * @param ramMb                Allocated RAM in Megabytes
 * @param cpuUtilization       CPU utilization ratio in [0.0, 1.0]
 * @param runningCloudletCount Number of cloudlets currently running on this VM
 * @param inExecution          Whether the VM is currently instantiated and running
 */
public record VmMetrics(
        long vmId,
        long hostId,
        long pesNumber,
        double totalMipsCapacity,
        long ramMb,
        double cpuUtilization,
        int runningCloudletCount,
        boolean inExecution
) {
    public VmMetrics {
        if (vmId < 0) {
            throw new IllegalArgumentException("VM ID must not be negative.");
        }
        if (hostId < -1) {
            throw new IllegalArgumentException("Host ID must be >= -1.");
        }
        if (pesNumber < 0) {
            throw new IllegalArgumentException("PEs count must not be negative.");
        }
        if (!Double.isFinite(totalMipsCapacity) || totalMipsCapacity < 0.0) {
            throw new IllegalArgumentException("Total MIPS must be a finite non-negative number.");
        }
        if (ramMb < 0) {
            throw new IllegalArgumentException("RAM must not be negative.");
        }
        if (!Double.isFinite(cpuUtilization) || cpuUtilization < 0.0 || cpuUtilization > 1.0) {
            throw new IllegalArgumentException("CPU utilization must be finite and within [0.0, 1.0].");
        }
        if (runningCloudletCount < 0) {
            throw new IllegalArgumentException("Running cloudlet count must not be negative.");
        }
    }
}
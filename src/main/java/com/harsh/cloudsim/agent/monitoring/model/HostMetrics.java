package com.harsh.cloudsim.agent.monitoring.model;

import java.util.Objects;

/**
 * Immutable metrics observation for a single CloudSim Host.
 *
 * @param hostId              Unique identifier of the host
 * @param architecture        Host architecture or type (e.g., x86, ARM)
 * @param totalPes            Total Processing Elements (cores)
 * @param availablePes        Unallocated Processing Elements
 * @param totalMipsCapacity   Total computational capacity in MIPS
 * @param availableMips       Currently available MIPS capacity
 * @param totalRamMb          Total RAM in Megabytes
 * @param availableRamMb      Available RAM in Megabytes
 * @param cpuUtilization      CPU utilization ratio in [0.0, 1.0]
 * @param ramUtilization      RAM utilization ratio in [0.0, 1.0]
 * @param active              Whether the host is powered on and active
 */
public record HostMetrics(
        long hostId,
        String architecture,
        long totalPes,
        long availablePes,
        double totalMipsCapacity,
        double availableMips,
        long totalRamMb,
        long availableRamMb,
        double cpuUtilization,
        double ramUtilization,
        boolean active
) {
    public HostMetrics {
        if (hostId < 0) {
            throw new IllegalArgumentException("Host ID must not be negative.");
        }
        Objects.requireNonNull(architecture, "Architecture must not be null.");
        architecture = architecture.trim();
        if (architecture.isBlank()) {
            throw new IllegalArgumentException("Architecture must not be blank.");
        }
        if (totalPes < 0 || availablePes < 0) {
            throw new IllegalArgumentException("PE counts must not be negative.");
        }
        if (availablePes > totalPes) {
            throw new IllegalArgumentException("Available PEs cannot exceed total PEs.");
        }
        if (!Double.isFinite(totalMipsCapacity) || totalMipsCapacity < 0.0) {
            throw new IllegalArgumentException("Total MIPS must be a finite non-negative number.");
        }
        if (!Double.isFinite(availableMips) || availableMips < 0.0) {
            throw new IllegalArgumentException("Available MIPS must be a finite non-negative number.");
        }
        if (totalRamMb < 0 || availableRamMb < 0) {
            throw new IllegalArgumentException("RAM capacities must not be negative.");
        }
        if (availableRamMb > totalRamMb) {
            throw new IllegalArgumentException("Available RAM cannot exceed total RAM.");
        }
        if (!Double.isFinite(cpuUtilization) || cpuUtilization < 0.0 || cpuUtilization > 1.0) {
            throw new IllegalArgumentException("CPU utilization must be finite and within [0.0, 1.0].");
        }
        if (!Double.isFinite(ramUtilization) || ramUtilization < 0.0 || ramUtilization > 1.0) {
            throw new IllegalArgumentException("RAM utilization must be finite and within [0.0, 1.0].");
        }
    }
}
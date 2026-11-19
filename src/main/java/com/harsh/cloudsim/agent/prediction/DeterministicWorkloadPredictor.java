package com.harsh.cloudsim.agent.prediction;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.monitoring.model.HostMetrics;
import com.harsh.cloudsim.agent.monitoring.model.MetricsSnapshot;
import com.harsh.cloudsim.agent.monitoring.model.VmMetrics;
import com.harsh.cloudsim.agent.prediction.model.WorkloadForecast;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;

/**
 * Deterministic, deliberately uncalibrated demand estimate.
 *
 * Units of WorkloadForecast:
 * - predictedCpuDemand: aggregate CPU demand in fully utilized VM equivalents
 *   (e.g., 2.4 means work equivalent to 2.4 full VMs, NOT a 0..1 fraction).
 * - predictedMemoryDemand: MB of estimated consumed RAM across the workload.
 *
 * User coefficients and default per-VM memory are assumptions to calibrate
 * against measured workloads, not learned values or guaranteed capacity.
 */
public final class DeterministicWorkloadPredictor implements WorkloadPredictor {
    private static final double CPU_VM_EQUIVALENTS_PER_USER = 0.0005;
    private static final double MEMORY_MB_PER_USER = 2.5;
    private static final double TARGET_UTILIZATION = 0.80;
    private static final double DEFAULT_VM_RAM_MB = 1024.0;

    @Override
    public WorkloadForecast predict(EventImpactAssessment assessment,
                                    EventVerificationResult verificationResult,
                                    MetricsSnapshot currentMetrics) {
        if (assessment == null || verificationResult == null || currentMetrics == null) {
            throw new IllegalArgumentException("Inputs to WorkloadPredictor cannot be null.");
        }

        // Averages are dimensionless; multiply CPU by VM count to express
        // aggregate demand in full-VM equivalents.
        double baselineCpuVmEquivalents = currentMetrics.runningVms()
                * currentMetrics.averageCpuUtilization();
        double baselineMemoryMb = observedMemoryMb(currentMetrics);

        if (verificationResult.status() != VerificationStatus.VERIFIED) {
            return new WorkloadForecast(
                    0,
                    currentMetrics.estimatedRequestsPerSecond(),
                    0,
                    baselineCpuVmEquivalents,
                    baselineMemoryMb,
                    currentMetrics.runningVms(),
                    1.0,
                    "Deterministic Fallback",
                    "Event not verified; no speculative scale change. Memory is MB based on available telemetry."
            );
        }

        double multiplier = assessment.trafficMultiplier();
        double concurrentUsers = assessment.expectedUsers() * multiplier;
        if (!Double.isFinite(concurrentUsers) || concurrentUsers > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Predicted concurrent users exceed supported integer range.");
        }
        int predictedUsers = (int) Math.ceil(concurrentUsers);
        double predictedRps = currentMetrics.estimatedRequestsPerSecond() * multiplier;
        double predictedCpu = baselineCpuVmEquivalents
                + predictedUsers * CPU_VM_EQUIVALENTS_PER_USER;
        double predictedMemoryMb = baselineMemoryMb
                + predictedUsers * MEMORY_MB_PER_USER;

        double perVmRamMb = referenceVmRamMb(currentMetrics);
        int cpuVmCount = requiredVms(predictedCpu, TARGET_UTILIZATION);
        int memoryVmCount = requiredVms(predictedMemoryMb, TARGET_UTILIZATION * perVmRamMb);
        int recommendedVms = Math.max(cpuVmCount, memoryVmCount);
        // Do not scale down just because a surge estimate is smaller than observed capacity.
        recommendedVms = Math.max(recommendedVms, currentMetrics.runningVms());

        return new WorkloadForecast(
                Math.toIntExact(assessment.leadTimeMinutes()),
                predictedRps,
                predictedUsers,
                predictedCpu,
                predictedMemoryMb,
                recommendedVms,
                assessment.confidence(),
                "Deterministic Baseline",
                String.format(java.util.Locale.ROOT,
                        "Verified event '%s' (x%.2f). CPU in VM equivalents, RAM in MB; "
                        + "%.0f MB reference VM; coefficients require calibration.",
                        assessment.eventName(), multiplier, perVmRamMb)
        );
    }

    private static double observedMemoryMb(MetricsSnapshot metrics) {
        if (!metrics.hostMetrics().isEmpty()) {
            double used = 0.0;
            for (HostMetrics host : metrics.hostMetrics()) {
                used += host.totalRamMb() * host.ramUtilization();
            }
            return used;
        }
        // Fallback: VM RAM allocations * fleet-average RAM utilization.
        // If both inventories are empty, baseline usage is unknown and treated
        // as 0 MB; consumers must not interpret this as confirmed spare capacity.
        double allocatedRamMb = 0.0;
        for (VmMetrics vm : metrics.vmMetrics()) {
            if (vm.inExecution()) {
                allocatedRamMb += vm.ramMb();
            }
        }
        return allocatedRamMb * metrics.averageRamUtilization();
    }

    private static double referenceVmRamMb(MetricsSnapshot metrics) {
        long count = 0;
        double allocatedMb = 0.0;
        for (VmMetrics vm : metrics.vmMetrics()) {
            if (vm.inExecution() && vm.ramMb() > 0) {
                count++;
                allocatedMb += vm.ramMb();
            }
        }
        return count == 0 ? DEFAULT_VM_RAM_MB : allocatedMb / count;
    }

    private static int requiredVms(double demand, double capacityPerVm) {
        if (!Double.isFinite(demand) || demand < 0.0
                || !Double.isFinite(capacityPerVm) || capacityPerVm <= 0.0) {
            throw new IllegalArgumentException("Invalid predicted demand or per-VM capacity.");
        }
        double required = Math.ceil(demand / capacityPerVm);
        if (required > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Recommended VM count exceeds integer range.");
        }
        return (int) required;
    }
}

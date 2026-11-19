package com.harsh.cloudsim.agent.prediction;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;
import com.harsh.cloudsim.agent.monitoring.model.HostMetrics;
import com.harsh.cloudsim.agent.monitoring.model.MetricsSnapshot;
import com.harsh.cloudsim.agent.monitoring.model.VmMetrics;
import com.harsh.cloudsim.agent.monitoring.model.WorkloadMetrics;
import com.harsh.cloudsim.agent.prediction.model.WorkloadForecast;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicWorkloadPredictorRegressionTest {
    private final DeterministicWorkloadPredictor predictor = new DeterministicWorkloadPredictor();

    private static EventImpactAssessment event(long users, double multiplier) {
        return new EventImpactAssessment("Exam results", EventType.values()[0], "portal",
                users, multiplier, 20, 0.9, "Official notice");
    }

    private static EventVerificationResult verified() {
        return new EventVerificationResult(VerificationStatus.VERIFIED, 0.9,
                "example.edu", List.of("Source verified"));
    }

    private static EventVerificationResult rejected() {
        return new EventVerificationResult(VerificationStatus.REJECTED, 0.1,
                "example.edu", List.of("Rejected"));
    }

    private static MetricsSnapshot snapshot(int vmCount, double cpuRatio, double ramRatio,
                                             double rps, List<HostMetrics> hosts,
                                             List<VmMetrics> vms) {
        WorkloadMetrics workload = new WorkloadMetrics(0, 0, 0, 0, 0, 0, rps, 0, 0);
        return new MetricsSnapshot(0, 0, hosts.size(), hosts.size(), vmCount, vmCount,
                0, 0, 0, 0, cpuRatio, ramRatio, rps, 0, 0,
                hosts, vms, workload);
    }

    @Test
    void ramDemandIsMbNotFractionPlusMb() {
        HostMetrics host = new HostMetrics(0, "x86", 4, 2, 40000, 20000,
                8192, 4096, 0.4, 0.5, true);
        WorkloadForecast result = predictor.predict(event(100, 1), verified(),
                snapshot(2, 0.4, 0.5, 30, List.of(host), List.of()));
        assertEquals(4096 + 100 * 2.5, result.predictedMemoryDemand(), 1e-8);
        assertEquals(2 * 0.4 + 100 * 0.0005, result.predictedCpuDemand(), 1e-8);
    }

    @Test
    void usesVmMemoryWhenHostMetricsMissing() {
        VmMetrics vm = new VmMetrics(1, 0, 1, 1000, 2048, 0.4, 0, true);
        WorkloadForecast result = predictor.predict(event(0, 1), verified(),
                snapshot(1, 0.4, 0.25, 20, List.of(), List.of(vm)));
        assertEquals(512.0, result.predictedMemoryDemand(), 1e-8);
        assertEquals(1, result.recommendedVmCount());
    }

    @Test
    void fallbackDoesNotScaleOnRejectedNotice() {
        WorkloadForecast result = predictor.predict(event(10000, 100), rejected(),
                snapshot(3, 0.5, 0.5, 50, List.of(), List.of()));
        assertEquals(3, result.recommendedVmCount());
        assertEquals(1.5, result.predictedCpuDemand(), 1e-8);
        assertEquals(50, result.expectedRequestsPerSecond(), 1e-8);
    }

    @Test
    void spikeProducesIncreasingDemandAndSafeRecommendation() {
        MetricsSnapshot metrics = snapshot(2, 0.4, 0.25, 40, List.of(), List.of());
        WorkloadForecast small = predictor.predict(event(100, 2), verified(), metrics);
        WorkloadForecast big = predictor.predict(event(10000, 2), verified(), metrics);
        assertTrue(big.recommendedVmCount() > small.recommendedVmCount());
        assertEquals(80, small.expectedRequestsPerSecond(), 1e-8);
        assertEquals(20000, big.expectedConcurrentUsers());
    }

    @Test
    void zeroTrafficWithNoVmDoesNotProvision() {
        WorkloadForecast result = predictor.predict(event(0, 1), verified(),
                snapshot(0, 0, 0, 0, List.of(), List.of()));
        assertEquals(0, result.recommendedVmCount());
        assertEquals(0, result.predictedMemoryDemand());
    }

    @Test
    void eventUsersOverIntegerRangeAreRejectedInsteadOfOverflowing() {
        MetricsSnapshot metrics = snapshot(1, 0, 0, 0, List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> predictor.predict(event(100_000_000L, 100), verified(), metrics));
    }
}

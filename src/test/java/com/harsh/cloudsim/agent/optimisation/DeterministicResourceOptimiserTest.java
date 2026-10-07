package com.harsh.cloudsim.agent.optimisation;

import com.harsh.cloudsim.agent.monitoring.model.MetricsSnapshot;
import com.harsh.cloudsim.agent.prediction.model.WorkloadForecast;
import com.harsh.cloudsim.agent.optimisation.model.ResourcePlan;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicResourceOptimiserTest {

    private DeterministicResourceOptimiser optimiser;

    @BeforeEach
    void setUp() {
        optimiser = new DeterministicResourceOptimiser();
    }

    // Helper method to bypass JVM 24 Mockito compatibility issues.
    // We populate all integer fields with the required VM count to guarantee runningVms() returns it.
    // Helper method to bypass JVM 24 Mockito compatibility issues.
    // Helper method to bypass JVM 24 Mockito compatibility issues.
    private MetricsSnapshot createDummyMetrics(int vms) {
        // Create an empty WorkloadMetrics object matching the exact 9-argument constructor
        // Required: int, int, int, int, int, int, double, double, double
        com.harsh.cloudsim.agent.monitoring.model.WorkloadMetrics dummyWorkload =
                new com.harsh.cloudsim.agent.monitoring.model.WorkloadMetrics(0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0);

        return new MetricsSnapshot(
                1L, 0.0,
                vms, vms, vms, vms, vms, vms, vms, vms, // All ints set to 'vms'
                0.0, 0.0, 0.0, 0.0, 0.0,
                Collections.emptyList(),
                Collections.emptyList(),
                dummyWorkload
        );
    }

    @Test
    void testProvisioningGeneratesHeterogeneousProposals() {
        // Arrange: We currently have 2 VMs
        MetricsSnapshot dummyMetrics = createDummyMetrics(2);

        // Forecast demands 5 VMs (Delta = +3)
        WorkloadForecast forecast = new WorkloadForecast(120, 500.0, 1000, 3.5, 8000.0, 5, 0.95, "Test", "Reason");

        // Act
        ResourcePlan plan = optimiser.optimize(forecast, dummyMetrics);

        // Assert
        assertEquals(2, plan.currentVmCount());
        assertEquals(5, plan.targetVmCount());
        assertEquals(3, plan.scalingProposals().size());

        // Should alternate: Heavy, Light, Heavy
        assertEquals(0, plan.scalingProposals().get(0).vmType()); // Heavy
        assertEquals(1, plan.scalingProposals().get(1).vmType()); // Light
        assertEquals(0, plan.scalingProposals().get(2).vmType()); // Heavy
        assertEquals(VmScalingAction.PROVISION, plan.scalingProposals().get(0).action());
    }

    @Test
    void testMaintainGeneratesNoProposals() {
        // Arrange: We currently have 4 VMs
        MetricsSnapshot dummyMetrics = createDummyMetrics(4);

        // Forecast also demands 4 VMs (Delta = 0)
        WorkloadForecast forecast = new WorkloadForecast(60, 200.0, 500, 1.5, 4000.0, 4, 0.9, "Test", "Reason");

        // Act
        ResourcePlan plan = optimiser.optimize(forecast, dummyMetrics);

        // Assert
        assertTrue(plan.scalingProposals().isEmpty());
    }
}
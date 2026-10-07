package com.harsh.cloudsim.agent.optimisation;

import com.harsh.cloudsim.agent.monitoring.model.MetricsSnapshot;
import com.harsh.cloudsim.agent.prediction.model.WorkloadForecast;
import com.harsh.cloudsim.agent.optimisation.model.ResourcePlan;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingAction;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingProposal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeterministicResourceOptimiser implements ResourceOptimisationAgent {

    // Hardware Profiles matching your CloudSim setup
    private static final int HEAVY_VM_TYPE = 0;
    private static final long HEAVY_HOST_ID = 0L; // x86 Host
    private static final long HEAVY_PES = 2L;
    private static final long HEAVY_RAM_MB = 4096L;

    private static final int LIGHT_VM_TYPE = 1;
    private static final long LIGHT_HOST_ID = 1L; // ARM Host
    private static final long LIGHT_PES = 1L;
    private static final long LIGHT_RAM_MB = 1024L;

    @Override
    public ResourcePlan optimize(WorkloadForecast forecast, MetricsSnapshot currentMetrics) {
        if (forecast == null || currentMetrics == null) {
            throw new IllegalArgumentException("Forecast and MetricsSnapshot cannot be null.");
        }

        int currentVms = currentMetrics.runningVms();
        int targetVms = forecast.recommendedVmCount();
        int delta = targetVms - currentVms;

        List<VmScalingProposal> proposals = new ArrayList<>();

        if (delta > 0) {
            // PROVISIONING LOGIC: Heterogeneous routing
            // Allocate the first few required VMs as Heavy (x86) to handle the main spike,
            // and the rest as Light (ARM) for background load.
            for (int i = 0; i < delta; i++) {
                if (i % 2 == 0) {
                    // Even iterations: Propose Heavy x86 VM
                    proposals.add(new VmScalingProposal(
                            HEAVY_VM_TYPE,
                            HEAVY_HOST_ID,
                            HEAVY_PES,
                            HEAVY_RAM_MB,
                            VmScalingAction.PROVISION,
                            "High capacity requested for projected demand spike."
                    ));
                } else {
                    // Odd iterations: Propose Light ARM VM
                    proposals.add(new VmScalingProposal(
                            LIGHT_VM_TYPE,
                            LIGHT_HOST_ID,
                            LIGHT_PES,
                            LIGHT_RAM_MB,
                            VmScalingAction.PROVISION,
                            "Lightweight instance to support secondary load."
                    ));
                }
            }
        } else if (delta < 0) {
            // DE-PROVISIONING LOGIC
            int vmsToRemove = Math.abs(delta);
            for (int i = 0; i < vmsToRemove; i++) {
                // Target Light VMs (ARM) first for de-provisioning to maintain core x86 performance
                proposals.add(new VmScalingProposal(
                        LIGHT_VM_TYPE,
                        LIGHT_HOST_ID,
                        LIGHT_PES,
                        LIGHT_RAM_MB,
                        VmScalingAction.DEPROVISION,
                        "Scale down triggered. Removing lightweight edge nodes first."
                ));
            }
        }
        // If delta == 0, proposals list remains empty (MAINTAIN)

        return new ResourcePlan(
                UUID.randomUUID().toString(),
                currentVms,
                targetVms,
                forecast.predictedCpuDemand(),
                forecast.predictedMemoryDemand(),
                proposals
        );
    }
}
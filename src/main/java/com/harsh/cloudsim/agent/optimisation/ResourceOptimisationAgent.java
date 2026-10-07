package com.harsh.cloudsim.agent.optimisation;

import com.harsh.cloudsim.agent.monitoring.model.MetricsSnapshot;
import com.harsh.cloudsim.agent.prediction.model.WorkloadForecast;
import com.harsh.cloudsim.agent.optimisation.model.ResourcePlan;

public interface ResourceOptimisationAgent {
    /**
     * Translates workload forecasts and current metrics into a concrete,
     * hardware-aware VM scaling plan.
     */
    ResourcePlan optimize(WorkloadForecast forecast, MetricsSnapshot currentMetrics);
}
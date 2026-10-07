package com.harsh.cloudsim.agent.prediction;

import com.harsh.cloudsim.agent.prediction.model.WorkloadForecast;
import com.harsh.cloudsim.agent.monitoring.model.MetricsSnapshot;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;

public interface WorkloadPredictor {
    WorkloadForecast predict(EventImpactAssessment assessment, EventVerificationResult verificationResult, MetricsSnapshot currentMetrics);
}
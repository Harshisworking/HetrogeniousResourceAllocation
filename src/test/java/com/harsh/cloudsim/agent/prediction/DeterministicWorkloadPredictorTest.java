package com.harsh.cloudsim.agent.prediction;

import com.harsh.cloudsim.agent.prediction.model.WorkloadForecast;
import com.harsh.cloudsim.agent.monitoring.model.MetricsSnapshot;
import com.harsh.cloudsim.agent.monitoring.model.WorkloadMetrics;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeterministicWorkloadPredictorTest {

    private DeterministicWorkloadPredictor predictor;

    @BeforeEach
    void setUp() {
        predictor = new DeterministicWorkloadPredictor();
    }

    @Test
    void testRecordValidationRejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> 
            new WorkloadForecast(-5, 100.0, 50, 0.5, 1024.0, 2, 0.9, "Source", "Reason")
        );
    }

    @Test
    void testPredictThrowsOnNullInputs() {
        assertThrows(IllegalArgumentException.class, () -> predictor.predict(null, null, null));
    }

    @Test
    void testPredictFallbackOnUnverifiedEvent() {
        EventType safeEnum = EventType.values()[0]; 
        EventImpactAssessment dummyAssessment = new EventImpactAssessment("Fake", safeEnum, "web", 0, 1.0, 0, 0.0, "None");
        
        EventVerificationResult unverifiedResult = new EventVerificationResult(
            VerificationStatus.REJECTED, 0.1, "unknown.com", List.of("Failed source check")
        );
        
        // Provided valid WorkloadMetrics instead of null
        WorkloadMetrics workload = new WorkloadMetrics(100, 10, 50, 40, 0, 10, 200.0, 2.0, 0.0);
        
        MetricsSnapshot metrics = new MetricsSnapshot(
                System.currentTimeMillis(), 100.0, 2, 2, 4, 4, 0, 10, 50, 0, 
                0.4, 0.4, 200.0, 10.0, 0.0, 
                List.of(), List.of(), workload
        );

        WorkloadForecast forecast = predictor.predict(dummyAssessment, unverifiedResult, metrics);

        assertEquals(0, forecast.forecastHorizonMinutes());
        assertEquals(200.0, forecast.expectedRequestsPerSecond());
        assertEquals(4, forecast.recommendedVmCount());
        assertEquals("Deterministic Fallback", forecast.predictionSource());
    }

    @Test
    void testPredictScalingOnVerifiedEvent() {
        EventType safeEnum = EventType.values()[0]; 
        
        EventImpactAssessment assessment = new EventImpactAssessment("Flash Sale", safeEnum, "web", 5000, 3.0, 60, 0.95, "Matches past sales");
        
        EventVerificationResult verifiedResult = new EventVerificationResult(
            VerificationStatus.VERIFIED, 0.95, "official.com", List.of("Verified via HTTPS")
        );
        
        // Provided valid WorkloadMetrics instead of null
        WorkloadMetrics workload = new WorkloadMetrics(200, 0, 100, 100, 0, 0, 150.0, 1.0, 0.0);
        
        MetricsSnapshot metrics = new MetricsSnapshot(
                System.currentTimeMillis(), 100.0, 2, 2, 4, 4, 0, 10, 50, 0, 
                0.3, 0.6, 150.0, 15.0, 0.0, 
                List.of(), List.of(), workload
        );

        WorkloadForecast forecast = predictor.predict(assessment, verifiedResult, metrics);

        assertEquals(60, forecast.forecastHorizonMinutes());
        assertEquals(450.0, forecast.expectedRequestsPerSecond());
        assertEquals(15000, forecast.expectedConcurrentUsers());
        
        assertTrue(forecast.predictedCpuDemand() > 1.0);
        assertTrue(forecast.recommendedVmCount() > 4); 
        assertEquals("Deterministic Baseline", forecast.predictionSource());
    }
}
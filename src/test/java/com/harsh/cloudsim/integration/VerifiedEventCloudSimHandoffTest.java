package com.harsh.cloudsim.integration;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;
import com.harsh.cloudsim.agent.execution.model.ExecutionStatus;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Controlled records exercise the handoff; tests make no live Gemini or HTTP calls. */
class VerifiedEventCloudSimHandoffTest {
    private static EventImpactAssessment assessment() {
        return new EventImpactAssessment("Controlled results event", EventType.RESULT,
                "test-service", 400L, 2.0, 20L, 0.95,
                "Synthetic test fixture for deterministic integration checks");
    }

    private static EventVerificationResult result(VerificationStatus status) {
        return new EventVerificationResult(status, 0.95, "ipu.ac.in",
                List.of("CONTROLLED TEST RECORD; not real source verification"));
    }

    @Test
    void verifiedFixtureReachesRealCloudSimHostAllocationButNoFakeSlo() {
        var run = VerifiedEventCloudSimHandoff.run(assessment(), result(VerificationStatus.VERIFIED));
        assertEquals(ExecutionStatus.EXECUTED, run.execution().status());
        assertTrue(run.execution().provisionedCount() > 0);
        assertEquals(run.execution().provisionedCount(), run.after().runningVms());
        assertNull(run.evaluation());
        assertEquals("NO_PERFORMANCE_OBSERVATION", run.observationSource());
    }

    @Test
    void manualReviewCannotReachAllocation() {
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEventCloudSimHandoff.run(assessment(),
                        result(VerificationStatus.MANUAL_REVIEW)));
    }

    @Test
    void rejectedEventCannotReachAllocation() {
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEventCloudSimHandoff.run(assessment(),
                        result(VerificationStatus.REJECTED)));
    }
}

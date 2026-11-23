package com.harsh.cloudsim.integration;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;
import com.harsh.cloudsim.agent.execution.model.ExecutionStatus;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Offline fixtures only: no Gemini, HTTP, Kubernetes, or real SLO measurements. */
class VerifiedEventCloudSimHandoffTest {
    private static EventImpactAssessment assessment(long leadTime) {
        return new EventImpactAssessment("Controlled results event", EventType.RESULT,
                "test-service", 400L, 2.0, leadTime, 0.95,
                "Synthetic test fixture, NOT a calibrated traffic estimate");
    }

    private static EventVerificationResult result(VerificationStatus status) {
        return new EventVerificationResult(status, 0.95, "ipu.ac.in",
                List.of("CONTROLLED FIXTURE, not live notice verification"));
    }

    @Test
    void verifiedEventRequiresExplicitSimulationAcknowledgement() {
        var verification = result(VerificationStatus.VERIFIED);
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEventCloudSimHandoff.run(assessment(20), verification, null));
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEventCloudSimHandoff.run(assessment(20), verification, "yes"));
    }

    @Test
    void verifiedFixtureCanRunOnlyAsAcknowledgedCloudSimExperiment() {
        var run = VerifiedEventCloudSimHandoff.run(assessment(20),
                result(VerificationStatus.VERIFIED), "SIMULATE");
        assertEquals(ExecutionStatus.EXECUTED, run.execution().status());
        assertTrue(run.execution().provisionedCount() > 0);
        assertEquals(run.execution().provisionedCount(), run.after().runningVms());
        assertNull(run.evaluation());
        assertEquals("NO_PERFORMANCE_OBSERVATION", run.observationSource());
    }

    @Test
    void manualReviewCannotReachAllocationEvenWhenAcknowledged() {
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEventCloudSimHandoff.run(assessment(20),
                        result(VerificationStatus.MANUAL_REVIEW), "SIMULATE"));
    }

    @Test
    void rejectedEventCannotReachAllocationEvenWhenAcknowledged() {
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEventCloudSimHandoff.run(assessment(20),
                        result(VerificationStatus.REJECTED), "SIMULATE"));
    }

    @Test
    void zeroLeadTimeIsOnlyAControlledDemonstrationNotProofOfProactivity() {
        var run = VerifiedEventCloudSimHandoff.run(assessment(0),
                result(VerificationStatus.VERIFIED), "SIMULATE");
        assertTrue(run.execution().provisionedCount() > 0);
        assertNull(run.evaluation());
    }
}

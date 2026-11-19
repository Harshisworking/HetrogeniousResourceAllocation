package com.harsh.cloudsim.integration;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;
import com.harsh.cloudsim.agent.evaluation.model.ObservedPerformance;
import com.harsh.cloudsim.agent.evaluation.model.SloTarget;
import com.harsh.cloudsim.agent.evaluation.persistence.SqliteDecisionMemoryRepository;
import com.harsh.cloudsim.agent.execution.model.ExecutionStatus;
import com.harsh.cloudsim.agent.model.ApprovalStatus;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ControlledPipelineDemoTest {
    @TempDir Path temp;

    private static EventImpactAssessment event(long users) {
        return new EventImpactAssessment("Test result announcement", EventType.RESULT,
                "demo-portal", users, 2.0, 20, 0.9, "Synthetic notice fixture");
    }

    private static EventVerificationResult verification(VerificationStatus status) {
        return new EventVerificationResult(status, status == VerificationStatus.VERIFIED ? 0.95 : 0.1,
                "example.invalid", List.of("Controlled source-verification fixture"));
    }

    @Test
    void verifiedFixtureProvisionsRealCloudSimVmsAndPersistsLabeledEvaluation() throws Exception {
        Path db = temp.resolve("demo.db");
        var repo = new SqliteDecisionMemoryRepository("jdbc:sqlite:" + db);
        var run = ControlledPipelineDemo.run(event(400), verification(VerificationStatus.VERIFIED),
                new ObservedPerformance(60000, 120.0, 0.0, 0.5, 3),
                new SloTarget("demo-portal", 200, .01, .70), repo);
        assertEquals(0, run.before().runningVms());
        assertTrue(run.forecast().recommendedVmCount() > 0);
        assertEquals(ExecutionStatus.EXECUTED, run.execution().status());
        assertEquals(run.execution().provisionedCount(), run.after().runningVms());
        assertTrue(run.execution().provisionedCount() > 0);
        assertTrue(run.execution().actionRecords().stream().allMatch(a -> a.success() && a.actualHostId() >= 0));
        assertNotNull(run.evaluation());
        assertEquals("SYNTHETIC_TEST_FIXTURE_NOT_MEASURED_LATENCY", run.observationSource());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT execution_id FROM decision_trace_memory")) {
            assertTrue(rows.next(), "Agent 8 must persist an evaluation trace");
            assertEquals(run.execution().executionId(), rows.getString("execution_id"));
            assertFalse(rows.next(), "Only one trace should be stored");
        }
    }

    @Test
    void rejectedFixtureCannotProvisionOrSavePerformanceEvaluation() {
        var repo = new SqliteDecisionMemoryRepository("jdbc:sqlite:" + temp.resolve("rejected.db"));
        var run = ControlledPipelineDemo.run(event(400), verification(VerificationStatus.REJECTED),
                new ObservedPerformance(60000, 120.0, 0.0, 0.5, 0),
                new SloTarget("demo-portal", 200, .01, .70), repo);
        assertEquals(ApprovalStatus.REJECTED, run.safety().approvalStatus());
        assertEquals(ExecutionStatus.BLOCKED_BY_SAFETY, run.execution().status());
        assertEquals(0, run.execution().provisionedCount());
        assertEquals(0, run.after().runningVms());
        assertNull(run.evaluation());
        assertEquals("NO_PERFORMANCE_OBSERVATION", run.observationSource());
    }

    @Test
    void excessiveDemandIsPartiallyApprovedWithoutHostOvercommit() {
        var run = ControlledPipelineDemo.run(event(20000), verification(VerificationStatus.VERIFIED),
                null, null, null);
        assertEquals(ApprovalStatus.PARTIALLY_APPROVED, run.safety().approvalStatus());
        assertEquals(run.safety().approvedProposals().size(), run.execution().provisionedCount());
        assertTrue(run.resourcePlan().scalingProposals().size() > run.safety().approvedProposals().size());
        assertEquals(run.execution().provisionedCount(), run.after().runningVms());
        assertNull(run.evaluation());
    }
}

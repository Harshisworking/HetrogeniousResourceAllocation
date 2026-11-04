package com.harsh.cloudsim.agent.evaluation;

import com.harsh.cloudsim.agent.evaluation.model.*;
import com.harsh.cloudsim.agent.evaluation.persistence.SqliteDecisionMemoryRepository;
import com.harsh.cloudsim.agent.execution.model.ExecutionEnvironment;
import com.harsh.cloudsim.agent.execution.model.ExecutionResult;
import com.harsh.cloudsim.agent.execution.model.ExecutionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultEvaluationAgentTest {

    private SqliteDecisionMemoryRepository repository;
    private DefaultEvaluationAgent agent;
    private ExecutionEnvironment mockEnv;
    private static final String TEST_DB_PATH = "test_memory.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + TEST_DB_PATH;

    @BeforeEach
    void setUp() {
        // Use a file-based DB for the test so the table persists across connections
        repository = new SqliteDecisionMemoryRepository(JDBC_URL);
        repository.initializeDatabase();
        agent = new DefaultEvaluationAgent(repository);
        
        mockEnv = ExecutionEnvironment.CLOUDSIM_SIMULATION;
    }

    @AfterEach
    void tearDown() {
        // Clean up the temporary test database file after each test
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    void evaluate_WhenSafetyBlocks_ShouldNotFlagBreach() {
        ExecutionResult result = new ExecutionResult(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                mockEnv, 
                ExecutionStatus.BLOCKED_BY_SAFETY,
                0, 
                0, 
                0, 
                Collections.emptyList(), 
                "Agent 6 Blocked Plan"
        );
        ObservedPerformance observed = new ObservedPerformance(60000, 100.0, 0.0, 0.4, 3);
        SloTarget target = new SloTarget("test-svc", 150.0, 0.01, 0.6);

        EvaluationOutcome outcome = agent.evaluate(result, observed, target);

        assertEquals(SloStatus.SAFETY_BLOCKED, outcome.sloStatus());
        assertFalse(outcome.rollbackRecommended());
        assertEquals(0.0, outcome.multiplierAdjustmentDelta());
    }

    @Test
    void evaluate_WhenOverProvisioned_ShouldRecommendCostReduction() {
        ExecutionResult result = new ExecutionResult(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                mockEnv,
                ExecutionStatus.EXECUTED,
                1,
                1,
                0,
                Collections.emptyList(),
                "Executed 5 Replicas"
        );
        ObservedPerformance observed = new ObservedPerformance(60000, 50.0, 0.0, 0.15, 5); 
        SloTarget target = new SloTarget("test-svc", 200.0, 0.01, 0.6);

        EvaluationOutcome outcome = agent.evaluate(result, observed, target);

        assertEquals(SloStatus.MET, outcome.sloStatus());
        assertEquals(-0.10, outcome.multiplierAdjustmentDelta());
        assertFalse(outcome.rollbackRecommended());
    }

    @Test
    void evaluate_WhenSloBreached_ShouldRecommendRollbackAndIncreaseMultiplier() {
        ExecutionResult result = new ExecutionResult(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                mockEnv,
                ExecutionStatus.EXECUTED,
                1,
                1,
                0,
                Collections.emptyList(),
                "Executed 2 Replicas"
        );
        ObservedPerformance observed = new ObservedPerformance(60000, 250.0, 0.0, 0.9, 2); 
        SloTarget target = new SloTarget("test-svc", 200.0, 0.01, 0.6);

        EvaluationOutcome outcome = agent.evaluate(result, observed, target);

        assertEquals(SloStatus.BREACHED, outcome.sloStatus());
        assertTrue(outcome.rollbackRecommended());
        assertEquals(0.25, outcome.multiplierAdjustmentDelta());
    }
}
package com.harsh.cloudsim.agent.evaluation;

import com.harsh.cloudsim.agent.evaluation.model.*;
import com.harsh.cloudsim.agent.evaluation.persistence.SqliteDecisionMemoryRepository;
import com.harsh.cloudsim.agent.execution.model.ExecutionResult;
import com.harsh.cloudsim.agent.execution.model.ExecutionStatus;

import java.util.Objects;
import java.util.UUID;

public class DefaultEvaluationAgent implements EvaluationAgent {

    private final SqliteDecisionMemoryRepository repository;

    public DefaultEvaluationAgent(SqliteDecisionMemoryRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public EvaluationOutcome evaluate(ExecutionResult executionResult,
                                      ObservedPerformance observed,
                                      SloTarget target) {
        Objects.requireNonNull(executionResult);
        Objects.requireNonNull(observed);
        Objects.requireNonNull(target);

        double latencyDelta = observed.observedP95LatencyMs() - target.maxP95LatencyMs();
        SloStatus status;
        boolean rollback = false;
        double adjustment = 0.0;

        // Use Agent 7's exact enum: BLOCKED_BY_SAFETY
        if (executionResult.status() == ExecutionStatus.NO_OP || executionResult.status() == ExecutionStatus.BLOCKED_BY_SAFETY) {
            status = SloStatus.SAFETY_BLOCKED;
            rollback = false;
            adjustment = 0.0;
        } else if (executionResult.status() == ExecutionStatus.FAILED_FALLBACK_ENGAGED || latencyDelta > 0) {
            status = SloStatus.BREACHED;
            rollback = true;
            adjustment = 0.25; 
        } else if (latencyDelta > -50.0) {
            status = SloStatus.AT_RISK;
            adjustment = 0.05;
        } else {
            status = SloStatus.MET;
            if (observed.actualCpuUtilization() < 0.20 && observed.activeReplicas() > 2) {
                adjustment = -0.10; 
            }
        }

        String summary = String.format("SLO %s: observed p95=%.1fms (target=%.1fms), latencyDelta=%.1fms, rollback=%b",
                status, observed.observedP95LatencyMs(), target.maxP95LatencyMs(), latencyDelta, rollback);

        EvaluationOutcome outcome = new EvaluationOutcome(
                UUID.randomUUID().toString(),
                executionResult.executionId(),
                System.currentTimeMillis(),
                status,
                latencyDelta,
                rollback,
                adjustment,
                summary
        );

        repository.saveTrace(outcome, executionResult.summaryReason());

        return outcome;
    }
}
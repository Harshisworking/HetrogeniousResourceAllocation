package com.harsh.cloudsim.agent.execution.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable data contract emitted by Agent 7 (Execution Agent) and consumed by Agent 8 (Evaluation).
 */
public record ExecutionResult(
        String executionId,
        long timestampEpochMs,
        ExecutionEnvironment environment,
        ExecutionStatus status,
        int provisionedCount,
        int deprovisionedCount,
        int failedCount,
        List<ExecutedActionRecord> actionRecords,
        String summaryReason
) {
    public ExecutionResult {
        Objects.requireNonNull(executionId, "Execution ID must not be null.");
        executionId = executionId.trim();
        if (executionId.isBlank()) {
            throw new IllegalArgumentException("Execution ID must not be blank.");
        }
        if (timestampEpochMs < 0) {
            throw new IllegalArgumentException("Timestamp must not be negative.");
        }
        Objects.requireNonNull(environment, "Execution environment must not be null.");
        Objects.requireNonNull(status, "Execution status must not be null.");
        if (provisionedCount < 0 || deprovisionedCount < 0 || failedCount < 0) {
            throw new IllegalArgumentException("Action counts must not be negative.");
        }
        Objects.requireNonNull(actionRecords, "Action records list must not be null.");
        actionRecords = List.copyOf(actionRecords);
        Objects.requireNonNull(summaryReason, "Summary reason must not be null.");
        summaryReason = summaryReason.trim();
        if (summaryReason.isBlank()) {
            throw new IllegalArgumentException("Summary reason must not be blank.");
        }
    }
}

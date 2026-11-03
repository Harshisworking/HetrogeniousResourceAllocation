package com.harsh.cloudsim.agent.execution.model;

/**
 * Overall outcome status produced by Agent 7 (Execution Agent).
 */
public enum ExecutionStatus {
    EXECUTED,
    PARTIALLY_EXECUTED,
    NO_OP,
    BLOCKED_BY_SAFETY,
    FAILED_FALLBACK_ENGAGED
}

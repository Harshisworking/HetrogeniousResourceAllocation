package com.harsh.cloudsim.agent.execution;

import com.harsh.cloudsim.agent.execution.model.ExecutionResult;
import com.harsh.cloudsim.agent.model.SafetyDecision;

/**
 * Contract for Agent 7 (Execution Agent).
 * Translates safety-approved resource plans into concrete infrastructure actions.
 */
public interface ExecutionAgent {

    /**
     * Executes the approved scaling proposals inside the target environment.
     *
     * @param safetyDecision validated decision produced by Agent 6 (Safety Firewall)
     * @return immutable ExecutionResult containing per-action audit records
     */
    ExecutionResult execute(SafetyDecision safetyDecision);
}

package com.harsh.cloudsim.agent.evaluation;

import com.harsh.cloudsim.agent.evaluation.model.EvaluationOutcome;
import com.harsh.cloudsim.agent.evaluation.model.ObservedPerformance;
import com.harsh.cloudsim.agent.evaluation.model.SloTarget;
import com.harsh.cloudsim.agent.execution.model.ExecutionResult;

public interface EvaluationAgent {
    EvaluationOutcome evaluate(ExecutionResult executionResult, ObservedPerformance observed, SloTarget target);
}
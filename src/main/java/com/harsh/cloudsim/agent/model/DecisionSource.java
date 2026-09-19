package com.harsh.cloudsim.agent.model;

/**
 * Identifies the component that produced a VM placement decision.
 *
 * This information will later be used for:
 * - explainability;
 * - experiment reports;
 * - debugging;
 * - comparing agentic and non-agentic allocation strategies.
 */
public enum DecisionSource {

    /**
     * A decision produced by an external optimisation agent.
     *
     * The agent may eventually use:
     * - live infrastructure metrics;
     * - predicted workload;
     * - event information;
     * - ML model output;
     * - LLM reasoning.
     */
    EXTERNAL_AGENT,

    /**
     * A deterministic decision produced by the built-in rules.
     *
     * Current example:
     * - heavy VM -> laptop;
     * - light VM -> phone.
     */
    STANDALONE_RULE,

    /**
     * A decision delegated to CloudSim Plus because neither the agent
     * nor the standalone rules could select a suitable host.
     */
    CLOUDSIM_FALLBACK
}
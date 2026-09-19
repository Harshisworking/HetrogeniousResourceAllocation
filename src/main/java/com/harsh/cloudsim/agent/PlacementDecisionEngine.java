package com.harsh.cloudsim.agent;

import com.harsh.cloudsim.agent.model.PlacementDecision;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;

/**
 * Contract for any component that decides where a VM should be placed.
 *
 * Implementations may use:
 * - deterministic rules;
 * - machine-learning predictions;
 * - LLM reasoning;
 * - Google ADK agents;
 * - multi-objective optimisation algorithms.
 *
 * The CloudSim allocation policy depends only on this interface.
 * Therefore, the policy does not need to know how the decision was made.
 */
@FunctionalInterface
public interface PlacementDecisionEngine {

    /**
     * Selects a target host for a VM.
     *
     * @param vm             VM that requires placement
     * @param availableHosts hosts that the engine may consider
     * @return structured placement decision
     */
    PlacementDecision decide(
            Vm vm,
            List<Host> availableHosts
    );
}
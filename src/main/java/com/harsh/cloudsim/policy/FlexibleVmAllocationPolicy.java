package com.harsh.cloudsim.policy;

import com.harsh.cloudsim.agent.PlacementDecisionEngine;
import com.harsh.cloudsim.agent.model.DecisionSource;
import com.harsh.cloudsim.agent.model.PlacementDecision;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * VM allocation policy supporting both:
 *
 * 1. structured external-agent decisions;
 * 2. deterministic standalone fallback rules.
 */
public class FlexibleVmAllocationPolicy
        extends VmAllocationPolicySimple {

    private PlacementDecisionEngine externalDecisionEngine;
    private PlacementDecision lastDecision;

    /**
     * Connects an external decision engine to the allocation policy.
     *
     * @param decisionEngine engine that will recommend a host
     */
    public void setExternalDecisionEngine(
            PlacementDecisionEngine decisionEngine
    ) {
        this.externalDecisionEngine = decisionEngine;
    }

    /**
     * Returns the most recent accepted placement decision.
     *
     * The Optional is empty when no host could be selected.
     *
     * @return most recent placement decision
     */
    public Optional<PlacementDecision> getLastDecision() {
        return Optional.ofNullable(lastDecision);
    }

    @Override
    public Optional<Host> defaultFindHostForVm(Vm vm) {
        /*
         * Prevent a decision from a previous VM from being accidentally
         * reported as the decision for the current VM.
         */
        lastDecision = null;

        Optional<Host> agentSelectedHost =
                findHostUsingExternalAgent(vm);

        if (agentSelectedHost.isPresent()) {
            return agentSelectedHost;
        }

        return findHostUsingFallbackRules(vm);
    }

    /**
     * Attempts to obtain and validate a structured external-agent decision.
     */
    private Optional<Host> findHostUsingExternalAgent(Vm vm) {
        if (externalDecisionEngine == null) {
            return Optional.empty();
        }

        try {
            /*
             * List.copyOf creates an unmodifiable list.
             * The agent may inspect the hosts but cannot add or remove hosts.
             */
            List<Host> availableHosts =
                    List.copyOf(getHostList());

            PlacementDecision decision =
                    Objects.requireNonNull(
                            externalDecisionEngine.decide(
                                    vm,
                                    availableHosts
                            ),
                            "External agent returned a null decision."
                    );

            if (decision.vmId() != vm.getId()) {
                throw new IllegalArgumentException(
                        "Agent decision VM ID "
                                + decision.vmId()
                                + " does not match requested VM ID "
                                + vm.getId()
                                + "."
                );
            }

            System.out.printf(
                    "%n[POLICY HOOK] Source=%s, VM=%d, "
                            + "requested Host=%d, confidence=%.2f%n",
                    decision.source(),
                    decision.vmId(),
                    decision.targetHostId(),
                    decision.confidence()
            );

            System.out.println(
                    "[POLICY HOOK] Reason: " + decision.reason()
            );

            Optional<Host> selectedHost =
                    findSuitableHostById(
                            vm,
                            decision.targetHostId()
                    );

            if (selectedHost.isPresent()) {
                lastDecision = decision;
                return selectedHost;
            }

            System.err.printf(
                    "[POLICY HOOK] Host #%d does not exist "
                            + "or is unsuitable. Using fallback rules.%n",
                    decision.targetHostId()
            );
        } catch (Exception exception) {
            System.err.println(
                    "Agent execution error, using fallback rules: "
                            + exception.getMessage()
            );
        }

        return Optional.empty();
    }

    /**
     * Applies deterministic rules and finally CloudSim's default policy.
     */
    private Optional<Host> findHostUsingFallbackRules(Vm vm) {
        Optional<Host> laptopHost =
                findSuitableHostById(vm, 0);

        if (vm.getMips() > 2_000 && laptopHost.isPresent()) {
            Host selectedHost = laptopHost.orElseThrow();

            lastDecision = new PlacementDecision(
                    vm.getId(),
                    selectedHost.getId(),
                    1.0,
                    "Heavy VM assigned to the high-performance x86 host.",
                    DecisionSource.STANDALONE_RULE
            );

            System.out.printf(
                    "[STANDALONE RULE] Assigned Heavy VM #%d "
                            + "to x86 Laptop (Host 0)%n",
                    vm.getId()
            );

            return Optional.of(selectedHost);
        }

        Optional<Host> phoneHost =
                findSuitableHostById(vm, 1);

        if (phoneHost.isPresent()) {
            Host selectedHost = phoneHost.orElseThrow();

            lastDecision = new PlacementDecision(
                    vm.getId(),
                    selectedHost.getId(),
                    1.0,
                    "Light VM assigned to the resource-constrained ARM host.",
                    DecisionSource.STANDALONE_RULE
            );

            System.out.printf(
                    "[STANDALONE RULE] Assigned Light VM #%d "
                            + "to ARM Phone (Host 1)%n",
                    vm.getId()
            );

            return Optional.of(selectedHost);
        }

        Optional<Host> cloudSimHost =
                super.defaultFindHostForVm(vm);

        cloudSimHost.ifPresent(host ->
                lastDecision = new PlacementDecision(
                        vm.getId(),
                        host.getId(),
                        1.0,
                        "Host selected by the default CloudSim allocation policy.",
                        DecisionSource.CLOUDSIM_FALLBACK
                )
        );

        return cloudSimHost;
    }

    /**
     * Finds a host by ID and ensures it has enough resources for the VM.
     */
    private Optional<Host> findSuitableHostById(
            Vm vm,
            long hostId
    ) {
        return getHostList()
                .stream()
                .filter(host -> host.getId() == hostId)
                .filter(host -> host.isSuitableForVm(vm))
                .findFirst();
    }
}
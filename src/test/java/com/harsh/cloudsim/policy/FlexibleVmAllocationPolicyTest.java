package com.harsh.cloudsim.policy;

import com.harsh.cloudsim.agent.model.DecisionSource;
import com.harsh.cloudsim.agent.model.PlacementDecision;
import com.harsh.cloudsim.model.HeterogeneousCluster;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlexibleVmAllocationPolicyTest {

    private FlexibleVmAllocationPolicy policy;

    @BeforeEach
    void setUpFreshClusterAndPolicy() {
        CloudSim simulation = new CloudSim();
        List<Host> hosts =
                HeterogeneousCluster.createClusterHosts();

        policy = new FlexibleVmAllocationPolicy();

        new DatacenterSimple(
                simulation,
                hosts,
                policy
        );
    }

    @Test
    void shouldAssignHeavyVmToLaptopUsingStandaloneRule() {
        Vm heavyVm = createVm(
                0,
                5_000,
                2,
                4_096
        );

        Optional<Host> selectedHost =
                policy.defaultFindHostForVm(heavyVm);

        PlacementDecision decision =
                policy.getLastDecision().orElseThrow();

        assertAll(
                () -> assertTrue(selectedHost.isPresent()),
                () -> assertEquals(
                        0,
                        selectedHost.orElseThrow().getId()
                ),
                () -> assertEquals(
                        0,
                        decision.targetHostId()
                ),
                () -> assertEquals(
                        DecisionSource.STANDALONE_RULE,
                        decision.source()
                )
        );
    }

    @Test
    void shouldAssignLightVmToPhoneUsingStandaloneRule() {
        Vm lightVm = createVm(
                1,
                1_000,
                1,
                1_024
        );

        Optional<Host> selectedHost =
                policy.defaultFindHostForVm(lightVm);

        PlacementDecision decision =
                policy.getLastDecision().orElseThrow();

        assertAll(
                () -> assertTrue(selectedHost.isPresent()),
                () -> assertEquals(
                        1,
                        selectedHost.orElseThrow().getId()
                ),
                () -> assertEquals(
                        DecisionSource.STANDALONE_RULE,
                        decision.source()
                )
        );
    }

    @Test
    void shouldAllowStructuredAgentDecisionToOverrideRule() {
        Vm lightVm = createVm(
                2,
                1_000,
                1,
                1_024
        );

        policy.setExternalDecisionEngine(
                (vm, availableHosts) ->
                        new PlacementDecision(
                                vm.getId(),
                                0,
                                0.92,
                                "Laptop selected to provide additional capacity.",
                                DecisionSource.EXTERNAL_AGENT
                        )
        );

        Optional<Host> selectedHost =
                policy.defaultFindHostForVm(lightVm);

        PlacementDecision acceptedDecision =
                policy.getLastDecision().orElseThrow();

        assertAll(
                () -> assertTrue(selectedHost.isPresent()),
                () -> assertEquals(
                        0,
                        selectedHost.orElseThrow().getId()
                ),
                () -> assertEquals(
                        0.92,
                        acceptedDecision.confidence()
                ),
                () -> assertEquals(
                        DecisionSource.EXTERNAL_AGENT,
                        acceptedDecision.source()
                ),
                () -> assertEquals(
                        "Laptop selected to provide additional capacity.",
                        acceptedDecision.reason()
                )
        );
    }

    @Test
    void shouldRejectUnknownAgentHostAndUseFallbackRule() {
        Vm lightVm = createVm(
                3,
                1_000,
                1,
                1_024
        );

        policy.setExternalDecisionEngine(
                (vm, availableHosts) ->
                        new PlacementDecision(
                                vm.getId(),
                                99,
                                0.75,
                                "Agent requested an unknown host.",
                                DecisionSource.EXTERNAL_AGENT
                        )
        );

        Optional<Host> selectedHost =
                policy.defaultFindHostForVm(lightVm);

        PlacementDecision fallbackDecision =
                policy.getLastDecision().orElseThrow();

        assertAll(
                () -> assertEquals(
                        1,
                        selectedHost.orElseThrow().getId()
                ),
                () -> assertEquals(
                        DecisionSource.STANDALONE_RULE,
                        fallbackDecision.source()
                )
        );
    }

    @Test
    void shouldRejectDecisionForDifferentVm() {
        Vm lightVm = createVm(
                4,
                1_000,
                1,
                1_024
        );

        policy.setExternalDecisionEngine(
                (vm, availableHosts) ->
                        new PlacementDecision(
                                999,
                                0,
                                0.90,
                                "Decision accidentally produced for another VM.",
                                DecisionSource.EXTERNAL_AGENT
                        )
        );

        Optional<Host> selectedHost =
                policy.defaultFindHostForVm(lightVm);

        PlacementDecision fallbackDecision =
                policy.getLastDecision().orElseThrow();

        assertAll(
                () -> assertEquals(
                        1,
                        selectedHost.orElseThrow().getId()
                ),
                () -> assertEquals(
                        DecisionSource.STANDALONE_RULE,
                        fallbackDecision.source()
                )
        );
    }

    @Test
    void shouldRecoverWhenExternalAgentThrowsException() {
        Vm lightVm = createVm(
                5,
                1_000,
                1,
                1_024
        );

        policy.setExternalDecisionEngine(
                (vm, availableHosts) -> {
                    throw new IllegalStateException(
                            "Simulated agent failure"
                    );
                }
        );

        Optional<Host> selectedHost =
                policy.defaultFindHostForVm(lightVm);

        PlacementDecision fallbackDecision =
                policy.getLastDecision().orElseThrow();

        assertAll(
                () -> assertEquals(
                        1,
                        selectedHost.orElseThrow().getId()
                ),
                () -> assertEquals(
                        DecisionSource.STANDALONE_RULE,
                        fallbackDecision.source()
                )
        );
    }

    @Test
    void shouldRecoverWhenExternalAgentReturnsNull() {
        Vm lightVm = createVm(
                6,
                1_000,
                1,
                1_024
        );

        policy.setExternalDecisionEngine(
                (vm, availableHosts) -> null
        );

        Optional<Host> selectedHost =
                policy.defaultFindHostForVm(lightVm);

        PlacementDecision fallbackDecision =
                policy.getLastDecision().orElseThrow();

        assertAll(
                () -> assertEquals(
                        1,
                        selectedHost.orElseThrow().getId()
                ),
                () -> assertEquals(
                        DecisionSource.STANDALONE_RULE,
                        fallbackDecision.source()
                )
        );
    }

    private Vm createVm(
            long id,
            double mips,
            long numberOfPes,
            long ram
    ) {
        return new VmSimple(id, mips, numberOfPes)
                .setRam(ram)
                .setBw(1_000)
                .setSize(10_000);
    }
}
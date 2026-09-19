package com.harsh.cloudsim.policy;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlexibleVmAllocationPolicyTest {

    private List<Host> hosts;
    private FlexibleVmAllocationPolicy policy;

    @BeforeEach
    void setUpFreshClusterAndPolicy() {
        CloudSim simulation = new CloudSim();

        hosts = HeterogeneousCluster.createClusterHosts();
        policy = new FlexibleVmAllocationPolicy();

        /*
         * Creating the datacenter connects:
         *
         * CloudSim simulation
         *       +
         * heterogeneous hosts
         *       +
         * allocation policy
         *
         * This also makes the hosts available through policy.getHostList().
         */
        new DatacenterSimple(simulation, hosts, policy);
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

        assertTrue(
                selectedHost.isPresent(),
                "The policy should find a suitable host for the heavy VM."
        );

        assertEquals(
                0,
                selectedHost.orElseThrow().getId(),
                "A heavy VM should be assigned to laptop Host 0."
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

        assertTrue(
                selectedHost.isPresent(),
                "The policy should find a suitable host for the light VM."
        );

        assertEquals(
                1,
                selectedHost.orElseThrow().getId(),
                "A light VM should be assigned to phone Host 1."
        );
    }

    @Test
    void shouldAllowExternalAgentToOverrideStandaloneRule() {
        Vm lightVm = createVm(
                2,
                1_000,
                1,
                1_024
        );

        /*
         * Without an external decision, the standalone rule would send this
         * light VM to Host 1.
         *
         * Here we simulate a future AI agent requesting Host 0.
         */
        policy.setExternalDecisionEngine(vm -> 0);

        Optional<Host> selectedHost =
                policy.defaultFindHostForVm(lightVm);

        assertTrue(
                selectedHost.isPresent(),
                "The agent-selected host should be accepted when suitable."
        );

        assertEquals(
                0,
                selectedHost.orElseThrow().getId(),
                "The valid external-agent decision should override the standalone rule."
        );
    }

    @Test
    void shouldRejectInvalidAgentHostAndUseStandaloneFallback() {
        Vm lightVm = createVm(
                3,
                1_000,
                1,
                1_024
        );

        /*
         * Host 99 does not exist.
         * The policy must not crash or blindly accept this decision.
         */
        policy.setExternalDecisionEngine(vm -> 99);

        Optional<Host> selectedHost =
                policy.defaultFindHostForVm(lightVm);

        assertTrue(
                selectedHost.isPresent(),
                "The standalone rule should recover from an invalid agent decision."
        );

        assertEquals(
                1,
                selectedHost.orElseThrow().getId(),
                "An invalid agent decision should fall back to phone Host 1."
        );
    }

    @Test
    void shouldRecoverWhenExternalAgentThrowsException() {
        Vm lightVm = createVm(
                4,
                1_000,
                1,
                1_024
        );

        /*
         * This deliberately simulates an LLM, ML model or agent service
         * failing while making its decision.
         */
        policy.setExternalDecisionEngine(vm -> {
            throw new IllegalStateException("Simulated agent failure");
        });

        Optional<Host> selectedHost =
                policy.defaultFindHostForVm(lightVm);

        assertTrue(
                selectedHost.isPresent(),
                "The policy should recover when the external agent fails."
        );

        assertEquals(
                1,
                selectedHost.orElseThrow().getId(),
                "Agent failure should activate the standalone fallback rule."
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
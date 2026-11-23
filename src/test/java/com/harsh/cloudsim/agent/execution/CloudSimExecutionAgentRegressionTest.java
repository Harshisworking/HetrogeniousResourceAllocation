package com.harsh.cloudsim.agent.execution;

import com.harsh.cloudsim.agent.execution.model.ExecutionResult;
import com.harsh.cloudsim.agent.execution.model.ExecutionStatus;
import com.harsh.cloudsim.agent.model.ApprovalStatus;
import com.harsh.cloudsim.agent.model.SafetyDecision;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingAction;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingProposal;
import com.harsh.cloudsim.model.HeterogeneousCluster;
import com.harsh.cloudsim.policy.FlexibleVmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CloudSimExecutionAgentRegressionTest {
    private static VmScalingProposal heavy(long host) {
        return new VmScalingProposal(0, host, 2, 4096,
                VmScalingAction.PROVISION, "Heavy VM");
    }

    private static VmScalingProposal light(long host, VmScalingAction action) {
        return new VmScalingProposal(1, host, 1, 1024, action, "Light VM");
    }

    private static SafetyDecision approved(VmScalingProposal... proposals) {
        return new SafetyDecision(ApprovalStatus.APPROVED, List.of(proposals), "Approved by safety firewall");
    }

    @Test
    void brokerQueueWithoutHostsMustNotBeReportedAsProvisioned() {
        CloudSim simulation = new CloudSim();
        FlexibleVmAllocationPolicy policy = new FlexibleVmAllocationPolicy();
        var broker = new DatacenterBrokerSimple(simulation);
        CloudSimExecutionAgent agent = new CloudSimExecutionAgent(policy, broker, 100);

        ExecutionResult result = agent.execute(approved(light(1, VmScalingAction.PROVISION)));
        assertEquals(ExecutionStatus.FAILED_FALLBACK_ENGAGED, result.status());
        assertEquals(0, result.provisionedCount());
        assertEquals(1, result.failedCount());
        assertFalse(result.actionRecords().getFirst().success());
        assertEquals(-1, result.actionRecords().getFirst().actualHostId());
        assertTrue(agent.getActiveManagedVms().isEmpty());
    }

    @Test
    void scaleDownMustNotRemoveVmFromWrongHostWhenPesMatch() {
        CloudSim simulation = new CloudSim();
        FlexibleVmAllocationPolicy policy = new FlexibleVmAllocationPolicy();
        new DatacenterSimple(simulation, HeterogeneousCluster.createClusterHosts(), policy);
        CloudSimExecutionAgent agent = new CloudSimExecutionAgent(policy);
        assertEquals(1, agent.execute(approved(light(1, VmScalingAction.PROVISION))).provisionedCount());

        ExecutionResult result = agent.execute(approved(light(0, VmScalingAction.DEPROVISION)));
        assertEquals(ExecutionStatus.FAILED_FALLBACK_ENGAGED, result.status());
        assertEquals(0, result.deprovisionedCount());
        assertEquals(1, result.failedCount());
        assertEquals(1, agent.getActiveManagedVms().size());
        assertEquals(1, agent.getActiveManagedVms().getFirst().getHost().getId());
    }

    @Test
    void scaleDownReleasesRealHostAndRemovesManagedVm() {
        CloudSim simulation = new CloudSim();
        FlexibleVmAllocationPolicy policy = new FlexibleVmAllocationPolicy();
        List<Host> hosts = HeterogeneousCluster.createClusterHosts();
        new DatacenterSimple(simulation, hosts, policy);
        CloudSimExecutionAgent agent = new CloudSimExecutionAgent(policy);
        assertEquals(1, agent.execute(approved(light(1, VmScalingAction.PROVISION))).provisionedCount());
        long ramBeforeRelease = hosts.get(1).getRam().getAvailableResource();

        ExecutionResult result = agent.execute(approved(light(1, VmScalingAction.DEPROVISION)));
        assertEquals(ExecutionStatus.EXECUTED, result.status());
        assertEquals(1, result.deprovisionedCount());
        assertTrue(agent.getActiveManagedVms().isEmpty());
        assertTrue(hosts.get(1).getRam().getAvailableResource() > ramBeforeRelease);
        assertTrue(hosts.get(1).getVmList().stream().noneMatch(vm -> vm.getId() == 100L),
                "Deallocated VM must be absent from the host's VM inventory");
    }

    @Test
    void mixedSuccessAndFailureProducesPartialStatusAndActualHostState() {
        CloudSim simulation = new CloudSim();
        FlexibleVmAllocationPolicy policy = new FlexibleVmAllocationPolicy();
        new DatacenterSimple(simulation, HeterogeneousCluster.createClusterHosts(), policy);
        CloudSimExecutionAgent agent = new CloudSimExecutionAgent(policy);

        // First VM fits; second exceeds the RAM of every host.
        VmScalingProposal impossible = new VmScalingProposal(
                0, 0, 2, 100000, VmScalingAction.PROVISION, "Impossible RAM request");
        ExecutionResult result = agent.execute(approved(light(1, VmScalingAction.PROVISION), impossible));
        assertEquals(ExecutionStatus.PARTIALLY_EXECUTED, result.status());
        assertEquals(1, result.provisionedCount());
        assertEquals(1, result.failedCount());
        assertEquals(1, agent.getActiveManagedVms().size());
        assertEquals(agent.getActiveManagedVms().getFirst().getHost().getId(),
                result.actionRecords().getFirst().actualHostId());
        assertFalse(result.actionRecords().get(1).success());
    }
}

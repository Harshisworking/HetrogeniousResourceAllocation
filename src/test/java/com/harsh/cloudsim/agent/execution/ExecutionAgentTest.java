package com.harsh.cloudsim.agent.execution;

import com.harsh.cloudsim.agent.execution.model.ExecutedActionRecord;
import com.harsh.cloudsim.agent.execution.model.ExecutionEnvironment;
import com.harsh.cloudsim.agent.execution.model.ExecutionResult;
import com.harsh.cloudsim.agent.execution.model.ExecutionStatus;
import com.harsh.cloudsim.agent.verification.Agent6SafetyFirewall;
import com.harsh.cloudsim.agent.model.ApprovalStatus;
import com.harsh.cloudsim.agent.model.DecisionSource;
import com.harsh.cloudsim.agent.model.HostCapacity;
import com.harsh.cloudsim.agent.model.SafetyDecision;
import com.harsh.cloudsim.agent.optimisation.model.ResourcePlan;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingAction;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingProposal;
import com.harsh.cloudsim.model.HeterogeneousCluster;
import com.harsh.cloudsim.policy.FlexibleVmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionAgentTest {

    private CloudSim simulation;
    private FlexibleVmAllocationPolicy policy;
    private CloudSimExecutionAgent cloudSimAgent;

    @BeforeEach
    void setUp() {
        simulation = new CloudSim();
        List<Host> hosts = HeterogeneousCluster.createClusterHosts();
        policy = new FlexibleVmAllocationPolicy();
        new DatacenterSimple(simulation, hosts, policy);
        cloudSimAgent = new CloudSimExecutionAgent(policy);
    }

    @Test
    void execute_shouldBlockImmediately_whenSafetyDecisionIsRejected() {
        SafetyDecision rejectedDecision = new SafetyDecision(
                ApprovalStatus.REJECTED,
                List.of(),
                "Host capacity exceeded on both x86 and ARM nodes."
        );

        ExecutionResult result = cloudSimAgent.execute(rejectedDecision);

        assertEquals(ExecutionStatus.BLOCKED_BY_SAFETY, result.status());
        assertEquals(ExecutionEnvironment.CLOUDSIM_SIMULATION, result.environment());
        assertEquals(0, result.provisionedCount());
        assertEquals(0, result.deprovisionedCount());
        assertTrue(result.actionRecords().isEmpty());
    }

    @Test
    void execute_shouldReturnNoOp_whenApprovedProposalsAreEmpty() {
        SafetyDecision maintainDecision = new SafetyDecision(
                ApprovalStatus.APPROVED,
                List.of(),
                "Processed 0 proposals. Rejected: 0"
        );

        ExecutionResult result = cloudSimAgent.execute(maintainDecision);

        assertEquals(ExecutionStatus.NO_OP, result.status());
        assertEquals(0, result.provisionedCount());
        assertEquals(0, cloudSimAgent.getActiveManagedVms().size());
    }

    @Test
    void execute_shouldProvisionHeavyX86AndLightArmVmsAcrossHeterogeneousHosts() {
        VmScalingProposal heavyProposal = new VmScalingProposal(
                0, 0L, 2L, 4096L, VmScalingAction.PROVISION, "Heavy spike workload on x86"
        );
        VmScalingProposal lightProposal = new VmScalingProposal(
                1, 1L, 1L, 1024L, VmScalingAction.PROVISION, "Lightweight edge task on ARM"
        );

        SafetyDecision approved = new SafetyDecision(
                ApprovalStatus.APPROVED,
                List.of(heavyProposal, lightProposal),
                "Approved both heterogeneous proposals."
        );

        ExecutionResult result = cloudSimAgent.execute(approved);

        assertEquals(ExecutionStatus.EXECUTED, result.status());
        assertEquals(2, result.provisionedCount());
        assertEquals(0, result.failedCount());
        assertEquals(2, cloudSimAgent.getActiveManagedVms().size());

        ExecutedActionRecord rec0 = result.actionRecords().get(0);
        ExecutedActionRecord rec1 = result.actionRecords().get(1);

        assertEquals(0L, rec0.actualHostId());
        assertEquals(DecisionSource.EXTERNAL_AGENT, rec0.decisionSource());
        assertEquals(1L, rec1.actualHostId());
        assertEquals(DecisionSource.EXTERNAL_AGENT, rec1.decisionSource());
    }

    @Test
    void execute_shouldDeprovisionActiveVmAndReleaseHostResources() {
        VmScalingProposal provisionLight = new VmScalingProposal(
                1, 1L, 1L, 1024L, VmScalingAction.PROVISION, "Initial ARM VM"
        );
        cloudSimAgent.execute(new SafetyDecision(ApprovalStatus.APPROVED, List.of(provisionLight), "Init"));
        assertEquals(1, cloudSimAgent.getActiveManagedVms().size());

        VmScalingProposal deprovisionLight = new VmScalingProposal(
                1, 1L, 1L, 1024L, VmScalingAction.DEPROVISION, "Scale down ARM edge"
        );
        ExecutionResult scaleDownResult = cloudSimAgent.execute(
                new SafetyDecision(ApprovalStatus.APPROVED, List.of(deprovisionLight), "Scale down")
        );

        assertEquals(ExecutionStatus.EXECUTED, scaleDownResult.status());
        assertEquals(1, scaleDownResult.deprovisionedCount());
        assertEquals(0, cloudSimAgent.getActiveManagedVms().size());
    }

    @Test
    void endToEnd_agent6ToAgent7PipelineIntegration() {
        Agent6SafetyFirewall firewall = new Agent6SafetyFirewall();
        List<HostCapacity> liveCapacities = List.of(
                new HostCapacity(0L, 0, 16384L, 4L, 4096L, 1L),
                new HostCapacity(1L, 1, 4096L, 2L, 1024L, 1L)
        );

        ResourcePlan plan = new ResourcePlan(
                "plan-e2e-01",
                2,
                4,
                0.75,
                0.65,
                List.of(
                        new VmScalingProposal(0, 0L, 2L, 4096L, VmScalingAction.PROVISION, "x86 scale up"),
                        new VmScalingProposal(1, 1L, 1L, 1024L, VmScalingAction.PROVISION, "ARM scale up")
                )
        );

        SafetyDecision safetyDecision = firewall.evaluatePlan(plan, liveCapacities);
        ExecutionResult executionResult = cloudSimAgent.execute(safetyDecision);

        assertEquals(ApprovalStatus.APPROVED, safetyDecision.approvalStatus());
        assertEquals(ExecutionStatus.EXECUTED, executionResult.status());
        assertEquals(2, executionResult.provisionedCount());
    }

    @Test
    void kubernetesAdapter_shouldEnforceRbacAndScaleMultiArchPods() {
        KubernetesExecutionAgent k8sAgent = new KubernetesExecutionAgent(
                "system:serviceaccount:default:agent7-execution-sa",
                1, 1, 1, 6
        );

        SafetyDecision decision = new SafetyDecision(
                ApprovalStatus.APPROVED,
                List.of(
                        new VmScalingProposal(0, 0L, 2L, 4096L, VmScalingAction.PROVISION, "K8s amd64 pod"),
                        new VmScalingProposal(1, 1L, 1L, 1024L, VmScalingAction.PROVISION, "K8s arm64 pod")
                ),
                "Approved"
        );

        ExecutionResult result = k8sAgent.execute(decision);

        assertEquals(ExecutionStatus.EXECUTED, result.status());
        assertEquals(2, k8sAgent.getCurrentAmd64Replicas());
        assertEquals(2, k8sAgent.getCurrentArm64Replicas());
    }

    @Test
    void kubernetesAdapter_shouldBlockUnauthorizedServiceAccount() {
        KubernetesExecutionAgent unauthorizedAgent = new KubernetesExecutionAgent(
                "system:serviceaccount:default:untrusted-sa",
                1, 1, 1, 6
        );

        SafetyDecision decision = new SafetyDecision(
                ApprovalStatus.APPROVED,
                List.of(new VmScalingProposal(0, 0L, 2L, 4096L, VmScalingAction.PROVISION, "Pod")),
                "Approved"
        );

        ExecutionResult result = unauthorizedAgent.execute(decision);
        assertEquals(ExecutionStatus.BLOCKED_BY_SAFETY, result.status());
    }

    @Test
    void executionRecords_shouldEnforceCompactConstructorInvariants() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutedActionRecord(
                1L, 99, VmScalingAction.PROVISION, 0L, 0L, true, DecisionSource.EXTERNAL_AGENT, "Invalid VM type"
        ));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionResult(
                "  ", 100L, ExecutionEnvironment.CLOUDSIM_SIMULATION, ExecutionStatus.NO_OP, 0, 0, 0, List.of(), "Blank ID"
        ));
    }
}

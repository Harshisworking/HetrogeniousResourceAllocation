package com.harsh.cloudsim.agent.verification;

import com.harsh.cloudsim.agent.model.ApprovalStatus;
import com.harsh.cloudsim.agent.model.HostCapacity;
import com.harsh.cloudsim.agent.model.SafetyDecision;
import com.harsh.cloudsim.agent.optimisation.model.ResourcePlan;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingAction;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingProposal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Agent6SafetyFirewallRegressionTest {
    private final Agent6SafetyFirewall firewall = new Agent6SafetyFirewall();

    private VmScalingProposal proposal(VmScalingAction action, long hostId, long ramMb, long pes) {
        return new VmScalingProposal(0, hostId, pes, ramMb, action, "Regression test");
    }

    private ResourcePlan plan(VmScalingProposal... proposals) {
        return new ResourcePlan("agent6-regression", 1, 2, 0.0, 0.0, List.of(proposals));
    }

    @Test
    void evaluatingTwiceDoesNotConsumeLiveCapacity() {
        HostCapacity host = new HostCapacity(0, 0, 8192, 4, 0, 0);
        ResourcePlan plan = plan(proposal(VmScalingAction.PROVISION, 0, 4096, 2));

        SafetyDecision first = firewall.evaluatePlan(plan, List.of(host));
        SafetyDecision second = firewall.evaluatePlan(plan, List.of(host));

        assertEquals(ApprovalStatus.APPROVED, first.approvalStatus());
        assertEquals(ApprovalStatus.APPROVED, second.approvalStatus());
        assertEquals(8192, host.getAvailableRamMb());
        assertEquals(4, host.getAvailablePes());
    }

    @Test
    void reservesCapacityWithinSinglePlanAndRejectsOvercommit() {
        HostCapacity host = new HostCapacity(0, 0, 8192, 4, 0, 0);
        ResourcePlan plan = plan(
                proposal(VmScalingAction.PROVISION, 0, 4096, 2),
                proposal(VmScalingAction.PROVISION, 0, 4096, 2),
                proposal(VmScalingAction.PROVISION, 0, 1024, 1)
        );

        SafetyDecision decision = firewall.evaluatePlan(plan, List.of(host));

        assertEquals(ApprovalStatus.PARTIALLY_APPROVED, decision.approvalStatus());
        assertEquals(2, decision.approvedProposals().size());
        assertEquals(8192, host.getAvailableRamMb());
        assertEquals(4, host.getAvailablePes());
    }

    @Test
    void speculativeDeprovisionCannotEnableOtherwiseUnsafeProvision() {
        HostCapacity host = new HostCapacity(0, 0, 4096, 2, 4096, 2);
        ResourcePlan plan = plan(
                proposal(VmScalingAction.DEPROVISION, 0, 4096, 2),
                proposal(VmScalingAction.PROVISION, 0, 4096, 2)
        );

        SafetyDecision decision = firewall.evaluatePlan(plan, List.of(host));

        assertEquals(ApprovalStatus.PARTIALLY_APPROVED, decision.approvalStatus());
        assertEquals(1, decision.approvedProposals().size());
        assertEquals(VmScalingAction.DEPROVISION, decision.approvedProposals().get(0).action());
        assertEquals(0, host.getAvailableRamMb());
        assertEquals(0, host.getAvailablePes());
    }

    @Test
    void maintainDoesNotReserveAdditionalCapacity() {
        HostCapacity host = new HostCapacity(0, 0, 4096, 2, 0, 0);
        SafetyDecision decision = firewall.evaluatePlan(plan(
                proposal(VmScalingAction.MAINTAIN, 0, 4096, 2),
                proposal(VmScalingAction.PROVISION, 0, 4096, 2)
        ), List.of(host));
        assertEquals(ApprovalStatus.APPROVED, decision.approvalStatus());
        assertEquals(2, decision.approvedProposals().size());
    }

    @Test
    void missingHostIsRejected() {
        SafetyDecision decision = firewall.evaluatePlan(
                plan(proposal(VmScalingAction.PROVISION, 99, 1024, 1)),
                List.of(new HostCapacity(0, 0, 4096, 2, 0, 0)));
        assertEquals(ApprovalStatus.REJECTED, decision.approvalStatus());
        assertTrue(decision.approvedProposals().isEmpty());
    }

    @Test
    void duplicateHostIdsAreRejectedAsAmbiguous() {
        assertThrows(IllegalArgumentException.class, () -> firewall.evaluatePlan(
                plan(), List.of(
                        new HostCapacity(0, 0, 4096, 2, 0, 0),
                        new HostCapacity(0, 0, 8192, 4, 0, 0))));
    }
}

package com.harsh.cloudsim.agent.verification;

import com.harsh.cloudsim.agent.model.ApprovalStatus;
import com.harsh.cloudsim.agent.model.HostCapacity;
import com.harsh.cloudsim.agent.model.SafetyDecision;
import com.harsh.cloudsim.agent.optimisation.model.ResourcePlan; // Corrected Import
import com.harsh.cloudsim.agent.optimisation.model.VmScalingAction; // Corrected Import
import com.harsh.cloudsim.agent.optimisation.model.VmScalingProposal; // Corrected Import

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Agent6SafetyFirewall {

    public SafetyDecision evaluatePlan(ResourcePlan plan, List<HostCapacity> liveHosts) {
        List<VmScalingProposal> approvedProposals = new ArrayList<>();
        int rejectedCount = 0;

        // Map hosts for O(1) lookup using standard OOP iteration
        Map<Long, HostCapacity> hostMap = new HashMap<>();
        for (int i = 0; i < liveHosts.size(); i++) {
            HostCapacity host = liveHosts.get(i);
            hostMap.put(host.getHostId(), host);
        }

        // Evaluate each proposal
        for (int i = 0; i < plan.scalingProposals().size(); i++) {
            VmScalingProposal proposal = plan.scalingProposals().get(i);
            HostCapacity targetHost = hostMap.get(proposal.targetHostId());

            if (targetHost == null) {
                rejectedCount++;
                continue; // Host doesn't exist, reject proposal
            }

            if (proposal.action() == VmScalingAction.DEPROVISION) {
                // Deprovisions are always safe and free up capacity
                targetHost.release(proposal.requestedRamMb(), proposal.requestedPes());
                approvedProposals.add(proposal);
            } 
            else if (proposal.action() == VmScalingAction.PROVISION || proposal.action() == VmScalingAction.MAINTAIN) {
                // Check if heterogeneous node has room
                if (targetHost.canAllocate(proposal.requestedRamMb(), proposal.requestedPes())) {
                    targetHost.allocate(proposal.requestedRamMb(), proposal.requestedPes());
                    approvedProposals.add(proposal);
                } else {
                    rejectedCount++;
                }
            }
        }

        ApprovalStatus finalStatus = determineStatus(plan.scalingProposals().size(), approvedProposals.size(), rejectedCount);
        String reason = "Processed " + plan.scalingProposals().size() + " proposals. Rejected: " + rejectedCount;

        return new SafetyDecision(finalStatus, approvedProposals, reason);
    }

    private ApprovalStatus determineStatus(int totalProposals, int approvedCount, int rejectedCount) {
        if (totalProposals == 0) {
            return ApprovalStatus.APPROVED;
        }
        if (rejectedCount == 0) {
            return ApprovalStatus.APPROVED;
        }
        if (approvedCount == 0) {
            return ApprovalStatus.REJECTED;
        }
        return ApprovalStatus.PARTIALLY_APPROVED;
    }
}
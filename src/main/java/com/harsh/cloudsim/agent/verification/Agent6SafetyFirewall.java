package com.harsh.cloudsim.agent.verification;

import com.harsh.cloudsim.agent.model.ApprovalStatus;
import com.harsh.cloudsim.agent.model.HostCapacity;
import com.harsh.cloudsim.agent.model.SafetyDecision;
import com.harsh.cloudsim.agent.optimisation.model.ResourcePlan;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingAction;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingProposal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A dry-run safety gate. Never mutates a HostCapacity supplied by monitoring.
 *
 * PROVISION proposals reserve capacity in a private ledger so multiple
 * proposals cannot overcommit one host. MAINTAIN does not reserve capacity.
 * DEPROVISION does NOT grant speculative free capacity: actual VM inventory
 * and successful release must be verified by the execution layer.
 */
public class Agent6SafetyFirewall {

    private static final class RemainingCapacity {
        long ramMb;
        long pes;

        RemainingCapacity(HostCapacity host) {
            ramMb = host.getAvailableRamMb();
            pes = host.getAvailablePes();
        }
    }

    public SafetyDecision evaluatePlan(ResourcePlan plan, List<HostCapacity> liveHosts) {
        Objects.requireNonNull(plan, "ResourcePlan must not be null.");
        Objects.requireNonNull(liveHosts, "Host capacity list must not be null.");

        Map<Long, RemainingCapacity> remainingByHost = new HashMap<>();
        for (HostCapacity host : liveHosts) {
            Objects.requireNonNull(host, "Host capacity entry must not be null.");
            RemainingCapacity previous = remainingByHost.putIfAbsent(
                    host.getHostId(), new RemainingCapacity(host));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate host ID: " + host.getHostId());
            }
        }

        List<VmScalingProposal> approved = new ArrayList<>();
        int rejected = 0;
        for (VmScalingProposal proposal : plan.scalingProposals()) {
            RemainingCapacity remaining = remainingByHost.get(proposal.targetHostId());
            if (remaining == null) {
                rejected++;
                continue;
            }

            if (proposal.action() == VmScalingAction.PROVISION) {
                if (remaining.ramMb >= proposal.requestedRamMb()
                        && remaining.pes >= proposal.requestedPes()) {
                    remaining.ramMb -= proposal.requestedRamMb();
                    remaining.pes -= proposal.requestedPes();
                    approved.add(proposal);
                } else {
                    rejected++;
                }
            } else if (proposal.action() == VmScalingAction.DEPROVISION) {
                // We cannot confirm VM ownership/existence with HostCapacity alone.
                // Do not count the proposed release toward allocations in this plan.
                // Agent 7 must confirm deprovision actually succeeds.
                approved.add(proposal);
            } else if (proposal.action() == VmScalingAction.MAINTAIN) {
                approved.add(proposal);
            } else {
                rejected++;
            }
        }

        ApprovalStatus status = rejected == 0
                ? ApprovalStatus.APPROVED
                : approved.isEmpty() ? ApprovalStatus.REJECTED : ApprovalStatus.PARTIALLY_APPROVED;
        String reason = "Processed " + plan.scalingProposals().size()
                + " proposals. Approved: " + approved.size()
                + ", rejected: " + rejected
                + ". Dry-run only; deprovision releases are not credited before execution.";
        return new SafetyDecision(status, List.copyOf(approved), reason);
    }
}

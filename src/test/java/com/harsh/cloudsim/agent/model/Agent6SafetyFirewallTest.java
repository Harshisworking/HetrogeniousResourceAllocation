package com.harsh.cloudsim.agent;

import com.harsh.cloudsim.agent.verification.Agent6SafetyFirewall;
import com.harsh.cloudsim.agent.model.HostCapacity;
import com.harsh.cloudsim.agent.model.SafetyDecision;
import com.harsh.cloudsim.agent.optimisation.model.ResourcePlan; // Corrected Import
import com.harsh.cloudsim.agent.optimisation.model.VmScalingAction; // Corrected Import
import com.harsh.cloudsim.agent.optimisation.model.VmScalingProposal; // Corrected Import

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Agent6SafetyFirewallTest {
    
    public static void main(String[] args) {
        // 1. Mock your live cluster capacities
        List<HostCapacity> liveCluster = new ArrayList<>();
        liveCluster.add(new HostCapacity(0L, 0, 16384, 8, 10240, 4)); 
        liveCluster.add(new HostCapacity(1L, 1, 8192, 4, 2048, 1));

        // 2. Mock Agent 5's Output
        List<VmScalingProposal> proposals = new ArrayList<>();
        proposals.add(new VmScalingProposal(0, 0L, 2, 4096, VmScalingAction.PROVISION, "Scale Up App"));
        proposals.add(new VmScalingProposal(0, 1L, 4, 8192, VmScalingAction.PROVISION, "Bad Routing"));
        
        ResourcePlan planFromAgent5 = new ResourcePlan(UUID.randomUUID().toString(), 10, 12, 12000, 12288, proposals);

        // 3. Run Agent 6
        Agent6SafetyFirewall firewall = new Agent6SafetyFirewall();
        SafetyDecision decision = firewall.evaluatePlan(planFromAgent5, liveCluster);

        // 4. Output the results
        System.out.println("--- AGENT 6 SAFETY REPORT ---");
        System.out.println("Status: " + decision.approvalStatus());
        System.out.println("Reason: " + decision.evaluationReason());
        System.out.println("Approved Moves:");
        
        for (int i = 0; i < decision.approvedProposals().size(); i++) {
            VmScalingProposal p = decision.approvedProposals().get(i);
            System.out.println(" -> " + p.action() + " VM on Host " + p.targetHostId() + " (" + p.requestedRamMb() + "MB)");
        }
    }
}
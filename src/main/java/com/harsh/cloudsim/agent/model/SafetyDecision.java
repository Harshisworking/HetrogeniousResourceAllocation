package com.harsh.cloudsim.agent.model;

import java.util.List;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingProposal; // Corrected Import

public record SafetyDecision(
    ApprovalStatus approvalStatus,
    List<VmScalingProposal> approvedProposals,
    String evaluationReason
) {}
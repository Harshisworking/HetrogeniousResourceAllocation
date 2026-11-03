package com.harsh.cloudsim.agent.execution;

import com.harsh.cloudsim.agent.execution.model.ExecutedActionRecord;
import com.harsh.cloudsim.agent.execution.model.ExecutionEnvironment;
import com.harsh.cloudsim.agent.execution.model.ExecutionResult;
import com.harsh.cloudsim.agent.execution.model.ExecutionStatus;
import com.harsh.cloudsim.agent.model.ApprovalStatus;
import com.harsh.cloudsim.agent.model.DecisionSource;
import com.harsh.cloudsim.agent.model.SafetyDecision;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingAction;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingProposal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agent 7 (Execution Agent) adapter for the target K3s / Kubernetes deployment.
 * Enforces RBAC service-account validation, replica bounds, and generates
 * architecture-aware (amd64 / arm64) Deployment scale operations.
 */
public class KubernetesExecutionAgent implements ExecutionAgent {

    private static final String EXPECTED_RBAC_ACCOUNT = "system:serviceaccount:default:agent7-execution-sa";
    private final String serviceAccountName;
    private final int minReplicas;
    private final int maxReplicas;
    private int currentAmd64Replicas;
    private int currentArm64Replicas;

    public KubernetesExecutionAgent(
            String serviceAccountName,
            int initialAmd64Replicas,
            int initialArm64Replicas,
            int minReplicas,
            int maxReplicas
    ) {
        this.serviceAccountName = Objects.requireNonNull(serviceAccountName, "ServiceAccount must not be null.").trim();
        if (minReplicas < 0 || maxReplicas < minReplicas) {
            throw new IllegalArgumentException("Invalid replica bounds.");
        }
        if (initialAmd64Replicas < 0 || initialArm64Replicas < 0) {
            throw new IllegalArgumentException("Initial replicas must not be negative.");
        }
        this.currentAmd64Replicas = initialAmd64Replicas;
        this.currentArm64Replicas = initialArm64Replicas;
        this.minReplicas = minReplicas;
        this.maxReplicas = maxReplicas;
    }

    @Override
    public ExecutionResult execute(SafetyDecision safetyDecision) {
        Objects.requireNonNull(safetyDecision, "SafetyDecision must not be null.");

        long now = System.currentTimeMillis();
        String execId = UUID.randomUUID().toString();

        if (!EXPECTED_RBAC_ACCOUNT.equals(serviceAccountName)) {
            return new ExecutionResult(
                    execId,
                    now,
                    ExecutionEnvironment.KUBERNETES_K3S,
                    ExecutionStatus.BLOCKED_BY_SAFETY,
                    0,
                    0,
                    0,
                    List.of(),
                    "RBAC authorization denied for ServiceAccount: " + serviceAccountName
            );
        }

        if (safetyDecision.approvalStatus() == ApprovalStatus.REJECTED) {
            return new ExecutionResult(
                    execId,
                    now,
                    ExecutionEnvironment.KUBERNETES_K3S,
                    ExecutionStatus.BLOCKED_BY_SAFETY,
                    0,
                    0,
                    0,
                    List.of(),
                    "Blocked by Agent 6 Safety Firewall: " + safetyDecision.evaluationReason()
            );
        }

        List<VmScalingProposal> proposals = safetyDecision.approvedProposals();
        if (proposals == null || proposals.isEmpty()) {
            return new ExecutionResult(
                    execId,
                    now,
                    ExecutionEnvironment.KUBERNETES_K3S,
                    ExecutionStatus.NO_OP,
                    0,
                    0,
                    0,
                    List.of(),
                    "No K3s Pod scaling proposals to execute."
            );
        }

        List<ExecutedActionRecord> records = new ArrayList<>();
        int provisioned = 0;
        int deprovisioned = 0;
        int failed = 0;

        for (int i = 0; i < proposals.size(); i++) {
            VmScalingProposal p = proposals.get(i);
            boolean isArmEdge = (p.vmType() == 1 || p.targetHostId() == 1L);
            String archLabel = isArmEdge ? "kubernetes.io/arch=arm64" : "kubernetes.io/arch=amd64";

            if (p.action() == VmScalingAction.PROVISION) {
                int totalAfter = currentAmd64Replicas + currentArm64Replicas + 1;
                if (totalAfter <= maxReplicas) {
                    if (isArmEdge) {
                        currentArm64Replicas++;
                    } else {
                        currentAmd64Replicas++;
                    }
                    provisioned++;
                    records.add(new ExecutedActionRecord(
                            i + 1L,
                            p.vmType(),
                            VmScalingAction.PROVISION,
                            p.targetHostId(),
                            p.targetHostId(),
                            true,
                            DecisionSource.EXTERNAL_AGENT,
                            "PATCH /apis/apps/v1/namespaces/default/deployments/portal/scale -> +1 Pod [" + archLabel + "]"
                    ));
                } else {
                    failed++;
                    records.add(new ExecutedActionRecord(
                            -1L,
                            p.vmType(),
                            VmScalingAction.PROVISION,
                            p.targetHostId(),
                            -1L,
                            false,
                            DecisionSource.STANDALONE_RULE,
                            "Rejected scale-up: exceeds maxReplicas bound (" + maxReplicas + ")."
                    ));
                }
            } else if (p.action() == VmScalingAction.DEPROVISION) {
                int totalAfter = currentAmd64Replicas + currentArm64Replicas - 1;
                if (totalAfter >= minReplicas && ((isArmEdge && currentArm64Replicas > 0) || (!isArmEdge && currentAmd64Replicas > 0))) {
                    if (isArmEdge) {
                        currentArm64Replicas--;
                    } else {
                        currentAmd64Replicas--;
                    }
                    deprovisioned++;
                    records.add(new ExecutedActionRecord(
                            i + 1L,
                            p.vmType(),
                            VmScalingAction.DEPROVISION,
                            p.targetHostId(),
                            p.targetHostId(),
                            true,
                            DecisionSource.EXTERNAL_AGENT,
                            "PATCH /apis/apps/v1/namespaces/default/deployments/portal/scale -> -1 Pod [" + archLabel + "]"
                    ));
                } else {
                    failed++;
                    records.add(new ExecutedActionRecord(
                            -1L,
                            p.vmType(),
                            VmScalingAction.DEPROVISION,
                            p.targetHostId(),
                            -1L,
                            false,
                            DecisionSource.STANDALONE_RULE,
                            "Rejected scale-down: violates minReplicas bound (" + minReplicas + ") or tier count."
                    ));
                }
            }
        }

        ExecutionStatus status = (failed == 0)
                ? ExecutionStatus.EXECUTED
                : (provisioned + deprovisioned > 0 ? ExecutionStatus.PARTIALLY_EXECUTED : ExecutionStatus.FAILED_FALLBACK_ENGAGED);

        return new ExecutionResult(
                execId,
                now,
                ExecutionEnvironment.KUBERNETES_K3S,
                status,
                provisioned,
                deprovisioned,
                failed,
                records,
                String.format("K3s scale completed: amd64=%d, arm64=%d", currentAmd64Replicas, currentArm64Replicas)
        );
    }

    public int getCurrentAmd64Replicas() {
        return currentAmd64Replicas;
    }

    public int getCurrentArm64Replicas() {
        return currentArm64Replicas;
    }
}

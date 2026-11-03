package com.harsh.cloudsim.agent.execution;

import com.harsh.cloudsim.agent.PlacementDecisionEngine;
import com.harsh.cloudsim.agent.execution.model.ExecutedActionRecord;
import com.harsh.cloudsim.agent.execution.model.ExecutionEnvironment;
import com.harsh.cloudsim.agent.execution.model.ExecutionResult;
import com.harsh.cloudsim.agent.execution.model.ExecutionStatus;
import com.harsh.cloudsim.agent.model.ApprovalStatus;
import com.harsh.cloudsim.agent.model.DecisionSource;
import com.harsh.cloudsim.agent.model.PlacementDecision;
import com.harsh.cloudsim.agent.model.SafetyDecision;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingAction;
import com.harsh.cloudsim.agent.optimisation.model.VmScalingProposal;
import com.harsh.cloudsim.policy.FlexibleVmAllocationPolicy;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 7 (Execution Agent) implementation for CloudSim Plus 7.3.0.
 *
 * Bridges the multi-agent intelligence pipeline with FlexibleVmAllocationPolicy
 * by acting as the PlacementDecisionEngine and executing approved VM provisioning
 * and de-provisioning operations.
 */
public class CloudSimExecutionAgent implements ExecutionAgent, PlacementDecisionEngine {

    private static final int HEAVY_VM_TYPE = 0;
    private static final double HEAVY_MIPS = 5000.0;
    private static final long HEAVY_BW = 1000L;
    private static final long HEAVY_SIZE = 10000L;

    private static final double LIGHT_MIPS = 1000.0;
    private static final long LIGHT_BW = 500L;
    private static final long LIGHT_SIZE = 5000L;

    private final FlexibleVmAllocationPolicy allocationPolicy;
    private final DatacenterBroker broker; // Optional: may be null in standalone policy tests
    private final AtomicLong vmIdSequence;
    private final Map<Long, PlacementDecision> pendingDecisions = new HashMap<>();
    private final List<Vm> activeManagedVms = new ArrayList<>();

    public CloudSimExecutionAgent(FlexibleVmAllocationPolicy allocationPolicy) {
        this(allocationPolicy, null, 100L);
    }

    public CloudSimExecutionAgent(
            FlexibleVmAllocationPolicy allocationPolicy,
            DatacenterBroker broker,
            long initialVmId
    ) {
        this.allocationPolicy = Objects.requireNonNull(
                allocationPolicy,
                "FlexibleVmAllocationPolicy must not be null."
        );
        if (initialVmId < 0) {
            throw new IllegalArgumentException("Initial VM ID must not be negative.");
        }
        this.broker = broker;
        this.vmIdSequence = new AtomicLong(initialVmId);
        this.allocationPolicy.setExternalDecisionEngine(this);
    }

    @Override
    public PlacementDecision decide(Vm vm, List<Host> availableHosts) {
        Objects.requireNonNull(vm, "VM must not be null.");
        PlacementDecision queued = pendingDecisions.get(vm.getId());
        if (queued != null) {
            return queued;
        }
        long fallbackHostId = vm.getMips() > 2000.0 ? 0L : 1L;
        return new PlacementDecision(
                vm.getId(),
                fallbackHostId,
                0.85,
                "Default hardware-tier routing applied by CloudSimExecutionAgent.",
                DecisionSource.EXTERNAL_AGENT
        );
    }

    @Override
    public ExecutionResult execute(SafetyDecision safetyDecision) {
        Objects.requireNonNull(safetyDecision, "SafetyDecision must not be null.");

        long now = System.currentTimeMillis();
        String execId = UUID.randomUUID().toString();

        if (safetyDecision.approvalStatus() == ApprovalStatus.REJECTED) {
            return new ExecutionResult(
                    execId,
                    now,
                    ExecutionEnvironment.CLOUDSIM_SIMULATION,
                    ExecutionStatus.BLOCKED_BY_SAFETY,
                    0,
                    0,
                    0,
                    List.of(),
                    "Execution blocked by Agent 6 Safety Firewall: " + safetyDecision.evaluationReason()
            );
        }

        List<VmScalingProposal> proposals = safetyDecision.approvedProposals();
        if (proposals == null || proposals.isEmpty()) {
            return new ExecutionResult(
                    execId,
                    now,
                    ExecutionEnvironment.CLOUDSIM_SIMULATION,
                    ExecutionStatus.NO_OP,
                    0,
                    0,
                    0,
                    List.of(),
                    "No scaling proposals required execution (MAINTAIN state)."
            );
        }

        List<ExecutedActionRecord> records = new ArrayList<>();
        int provisioned = 0;
        int deprovisioned = 0;
        int failed = 0;

        for (int i = 0; i < proposals.size(); i++) {
            VmScalingProposal proposal = proposals.get(i);
            if (proposal.action() == VmScalingAction.PROVISION) {
                ExecutedActionRecord record = executeProvision(proposal);
                records.add(record);
                if (record.success()) {
                    provisioned++;
                } else {
                    failed++;
                }
            } else if (proposal.action() == VmScalingAction.DEPROVISION) {
                ExecutedActionRecord record = executeDeprovision(proposal);
                records.add(record);
                if (record.success()) {
                    deprovisioned++;
                } else {
                    failed++;
                }
            } else {
                records.add(new ExecutedActionRecord(
                        -1L,
                        proposal.vmType(),
                        VmScalingAction.MAINTAIN,
                        proposal.targetHostId(),
                        proposal.targetHostId(),
                        true,
                        DecisionSource.EXTERNAL_AGENT,
                        "MAINTAIN proposal acknowledged: " + proposal.reason()
                ));
            }
        }

        ExecutionStatus finalStatus = resolveStatus(provisioned, deprovisioned, failed);
        String summary = String.format(
                "Executed %d proposals: provisioned=%d, deprovisioned=%d, failed=%d.",
                proposals.size(), provisioned, deprovisioned, failed
        );

        return new ExecutionResult(
                execId,
                now,
                ExecutionEnvironment.CLOUDSIM_SIMULATION,
                finalStatus,
                provisioned,
                deprovisioned,
                failed,
                records,
                summary
        );
    }

    private ExecutedActionRecord executeProvision(VmScalingProposal proposal) {
        long vmId = vmIdSequence.getAndIncrement();
        double mips = (proposal.vmType() == HEAVY_VM_TYPE) ? HEAVY_MIPS : LIGHT_MIPS;
        long bw = (proposal.vmType() == HEAVY_VM_TYPE) ? HEAVY_BW : LIGHT_BW;
        long size = (proposal.vmType() == HEAVY_VM_TYPE) ? HEAVY_SIZE : LIGHT_SIZE;

        Vm vm = new VmSimple(vmId, mips, proposal.requestedPes())
                .setRam(proposal.requestedRamMb())
                .setBw(bw)
                .setSize(size);

        PlacementDecision placementDecision = new PlacementDecision(
                vmId,
                proposal.targetHostId(),
                1.0,
                proposal.reason(),
                DecisionSource.EXTERNAL_AGENT
        );

        pendingDecisions.put(vmId, placementDecision);

        try {
            boolean allocated = false;
            long actualHostId = -1L;
            DecisionSource finalSource = DecisionSource.EXTERNAL_AGENT;

            if (!allocationPolicy.getHostList().isEmpty()) {
                allocationPolicy.allocateHostForVm(vm);
                allocated = (vm.getHost() != null && vm.getHost() != Host.NULL);
                Optional<PlacementDecision> policyDecision = allocationPolicy.getLastDecision();
                if (policyDecision.isPresent()) {
                    actualHostId = policyDecision.get().targetHostId();
                    finalSource = policyDecision.get().source();
                } else if (allocated) {
                    actualHostId = vm.getHost().getId();
                }
            }

            if (broker != null) {
                broker.submitVmList(List.of(vm));
                if (!allocated && allocationPolicy.getHostList().isEmpty()) {
                    allocated = true;
                    actualHostId = proposal.targetHostId();
                }
            }

            if (allocated) {
                activeManagedVms.add(vm);
                return new ExecutedActionRecord(
                        vmId,
                        proposal.vmType(),
                        VmScalingAction.PROVISION,
                        proposal.targetHostId(),
                        actualHostId,
                        true,
                        finalSource,
                        "Provisioned VM #" + vmId + " on Host #" + actualHostId + " via " + finalSource
                );
            } else {
                return new ExecutedActionRecord(
                        vmId,
                        proposal.vmType(),
                        VmScalingAction.PROVISION,
                        proposal.targetHostId(),
                        -1L,
                        false,
                        DecisionSource.CLOUDSIM_FALLBACK,
                        "Failed to allocate Host for VM #" + vmId + "; insufficient capacity across all hosts."
                );
            }
        } finally {
            pendingDecisions.remove(vmId);
        }
    }

    private ExecutedActionRecord executeDeprovision(VmScalingProposal proposal) {
        Vm targetVm = null;
        for (int i = activeManagedVms.size() - 1; i >= 0; i--) {
            Vm candidate = activeManagedVms.get(i);
            long hostId = (candidate.getHost() != null && candidate.getHost() != Host.NULL)
                    ? candidate.getHost().getId()
                    : -1L;
            if (hostId == proposal.targetHostId() || candidate.getNumberOfPes() == proposal.requestedPes()) {
                targetVm = candidate;
                activeManagedVms.remove(i);
                break;
            }
        }

        if (targetVm != null) {
            long previousHostId = (targetVm.getHost() != null && targetVm.getHost() != Host.NULL)
                    ? targetVm.getHost().getId()
                    : proposal.targetHostId();
            allocationPolicy.deallocateHostForVm(targetVm);
            return new ExecutedActionRecord(
                    targetVm.getId(),
                    proposal.vmType(),
                    VmScalingAction.DEPROVISION,
                    proposal.targetHostId(),
                    previousHostId,
                    true,
                    DecisionSource.EXTERNAL_AGENT,
                    "Deprovisioned VM #" + targetVm.getId() + " from Host #" + previousHostId
            );
        }

        return new ExecutedActionRecord(
                -1L,
                proposal.vmType(),
                VmScalingAction.DEPROVISION,
                proposal.targetHostId(),
                -1L,
                false,
                DecisionSource.STANDALONE_RULE,
                "No matching active VM found on Host #" + proposal.targetHostId() + " to deprovision."
        );
    }

    private ExecutionStatus resolveStatus(int provisioned, int deprovisioned, int failed) {
        int succeeded = provisioned + deprovisioned;
        if (failed == 0 && succeeded > 0) {
            return ExecutionStatus.EXECUTED;
        }
        if (succeeded > 0 && failed > 0) {
            return ExecutionStatus.PARTIALLY_EXECUTED;
        }
        if (failed > 0) {
            return ExecutionStatus.FAILED_FALLBACK_ENGAGED;
        }
        return ExecutionStatus.NO_OP;
    }

    public List<Vm> getActiveManagedVms() {
        return List.copyOf(activeManagedVms);
    }
}

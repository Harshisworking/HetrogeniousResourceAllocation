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
                allocated = vm.getHost() != null && vm.getHost() != Host.NULL;
                if (allocated) {
                    // The VM's actual host is authoritative, including when
                    // the policy selected a fallback host.
                    actualHostId = vm.getHost().getId();
                    Optional<PlacementDecision> policyDecision = allocationPolicy.getLastDecision();
                    if (policyDecision.isPresent()
                            && policyDecision.get().vmId() == vmId
                            && policyDecision.get().targetHostId() == actualHostId) {
                        finalSource = policyDecision.get().source();
                    } else {
                        finalSource = DecisionSource.CLOUDSIM_FALLBACK;
                    }
                }
            }

            // Submitting a VM to the broker is not proof of successful host
            // allocation. Never report EXECUTED for a merely queued VM.
            if (allocated && broker != null) {
                broker.submitVmList(List.of(vm));
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
        } catch (RuntimeException ex) {
            return new ExecutedActionRecord(
                    vmId, proposal.vmType(), VmScalingAction.PROVISION,
                    proposal.targetHostId(), -1L, false,
                    DecisionSource.EXTERNAL_AGENT,
                    "VM allocation failed: " + ex.getClass().getSimpleName());
        } finally {
            pendingDecisions.remove(vmId);
        }
    }

    private ExecutedActionRecord executeDeprovision(VmScalingProposal proposal) {
        // A proposal identifies a tier, host and VM shape, not an exact VM ID.
        // Never remove a VM from a different host simply because its PE count
        // happens to match. Inventory removes a VM only after host release.
        for (int i = activeManagedVms.size() - 1; i >= 0; i--) {
            Vm candidate = activeManagedVms.get(i);
            Host host = candidate.getHost();
            if (host == null || host == Host.NULL
                    || host.getId() != proposal.targetHostId()
                    || candidate.getNumberOfPes() != proposal.requestedPes()
                    || candidate.getRam().getCapacity() != proposal.requestedRamMb()
                    || (candidate.getMips() > 2000.0 ? HEAVY_VM_TYPE : 1) != proposal.vmType()) {
                continue;
            }

            long previousHostId = host.getId();
            // Some CloudSim versions keep the VM's previous host reference
            // after deallocation. Verify the host's authoritative resource
            // state instead of assuming candidate.getHost() becomes Host.NULL.
            boolean wasHosted = host.getVmList().contains(candidate);
            long availableRamBefore = host.getRam().getAvailableResource();
            if (!wasHosted) {
                return new ExecutedActionRecord(
                        candidate.getId(), proposal.vmType(), VmScalingAction.DEPROVISION,
                        proposal.targetHostId(), previousHostId, false,
                        DecisionSource.EXTERNAL_AGENT,
                        "VM #" + candidate.getId() + " was not present in Host #" + previousHostId + " inventory");
            }
            try {
                allocationPolicy.deallocateHostForVm(candidate);
                boolean removedFromHost = !host.getVmList().contains(candidate);
                boolean ramReleased = host.getRam().getAvailableResource() > availableRamBefore;
                if (removedFromHost && ramReleased) {
                    activeManagedVms.remove(i);
                    return new ExecutedActionRecord(
                            candidate.getId(), proposal.vmType(), VmScalingAction.DEPROVISION,
                            proposal.targetHostId(), previousHostId, true,
                            DecisionSource.EXTERNAL_AGENT,
                            "Deprovisioned VM #" + candidate.getId() + " from Host #" + previousHostId);
                }
                return new ExecutedActionRecord(
                        candidate.getId(), proposal.vmType(), VmScalingAction.DEPROVISION,
                        proposal.targetHostId(), previousHostId, false,
                        DecisionSource.EXTERNAL_AGENT,
                        "Deallocation could not confirm inventory removal and RAM release for VM #"
                                + candidate.getId() + " from Host #" + previousHostId);
            } catch (RuntimeException ex) {
                return new ExecutedActionRecord(
                        candidate.getId(), proposal.vmType(), VmScalingAction.DEPROVISION,
                        proposal.targetHostId(), previousHostId, false,
                        DecisionSource.EXTERNAL_AGENT,
                        "Deallocation failed for VM #" + candidate.getId() + ": " + ex.getClass().getSimpleName());
            }
        }

        return new ExecutedActionRecord(
                -1L, proposal.vmType(), VmScalingAction.DEPROVISION,
                proposal.targetHostId(), -1L, false,
                DecisionSource.EXTERNAL_AGENT,
                "No matching managed VM of requested type, host, PEs and RAM found on Host #"
                        + proposal.targetHostId());
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

package com.harsh.cloudsim.integration;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;
import com.harsh.cloudsim.agent.evaluation.DefaultEvaluationAgent;
import com.harsh.cloudsim.agent.evaluation.model.EvaluationOutcome;
import com.harsh.cloudsim.agent.evaluation.model.ObservedPerformance;
import com.harsh.cloudsim.agent.evaluation.model.SloTarget;
import com.harsh.cloudsim.agent.evaluation.persistence.SqliteDecisionMemoryRepository;
import com.harsh.cloudsim.agent.execution.CloudSimExecutionAgent;
import com.harsh.cloudsim.agent.execution.model.ExecutionResult;
import com.harsh.cloudsim.agent.execution.model.ExecutionStatus;
import com.harsh.cloudsim.agent.model.ApprovalStatus;
import com.harsh.cloudsim.agent.model.HostCapacity;
import com.harsh.cloudsim.agent.model.SafetyDecision;
import com.harsh.cloudsim.agent.monitoring.MonitoringAgent;
import com.harsh.cloudsim.agent.monitoring.model.MetricsSnapshot;
import com.harsh.cloudsim.agent.optimisation.DeterministicResourceOptimiser;
import com.harsh.cloudsim.agent.optimisation.model.ResourcePlan;
import com.harsh.cloudsim.agent.prediction.DeterministicWorkloadPredictor;
import com.harsh.cloudsim.agent.prediction.model.WorkloadForecast;
import com.harsh.cloudsim.agent.verification.Agent6SafetyFirewall;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import com.harsh.cloudsim.model.HeterogeneousCluster;
import com.harsh.cloudsim.policy.FlexibleVmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Controlled integration proof for the deterministic part of the pipeline.
 * Agents 1 and 2 are NOT invoked here: their validated output records are
 * injected test fixtures. No live HTTP, Gemini, K8s or real HTTP latency.
 *
 * Provisioning changes actual CloudSim host allocations (without starting a
 * timed cloudlet experiment). The SLO sample is explicitly user-supplied and
 * must never be described as measured CloudSim/real-world HTTP performance.
 */
public final class ControlledPipelineDemo {
    private ControlledPipelineDemo() {}

    public record RunResult(
            MetricsSnapshot before,
            WorkloadForecast forecast,
            ResourcePlan resourcePlan,
            SafetyDecision safety,
            ExecutionResult execution,
            MetricsSnapshot after,
            EvaluationOutcome evaluation,
            String observationSource
    ) {}

    public static RunResult run(EventImpactAssessment assessment,
                                EventVerificationResult verification,
                                ObservedPerformance syntheticObservation,
                                SloTarget sloTarget,
                                SqliteDecisionMemoryRepository repository) {
        Objects.requireNonNull(assessment, "Event assessment is required");
        Objects.requireNonNull(verification, "Verification result is required");
        if ((syntheticObservation == null) != (sloTarget == null)) {
            throw new IllegalArgumentException("Pass both SLO observation and target, or neither");
        }
        if (syntheticObservation != null) {
            Objects.requireNonNull(repository, "SQLite repository is required when evaluating");
        }

        CloudSim simulation = new CloudSim();
        List<Host> hosts = HeterogeneousCluster.createClusterHosts();
        FlexibleVmAllocationPolicy policy = new FlexibleVmAllocationPolicy();
        new DatacenterSimple(simulation, hosts, policy);
        CloudSimExecutionAgent executor = new CloudSimExecutionAgent(policy);
        MonitoringAgent monitor = new MonitoringAgent();

        MetricsSnapshot before = monitor.captureSnapshot(
                simulation, hosts, executor.getActiveManagedVms(), null);
        WorkloadForecast forecast = new DeterministicWorkloadPredictor()
                .predict(assessment, verification, before);
        ResourcePlan plan = new DeterministicResourceOptimiser().optimize(forecast, before);

        // Never treat the predictor's NO_OP as a trusted authorization to scale.
        // An unverified event always ends in an explicit safety block.
        SafetyDecision safety;
        if (verification.status() != VerificationStatus.VERIFIED) {
            safety = new SafetyDecision(ApprovalStatus.REJECTED, List.of(),
                    "Unverified event blocked: " + verification.status());
        } else {
            List<HostCapacity> capacities = new ArrayList<>();
            for (Host host : hosts) {
                long totalRam = host.getRam().getCapacity();
                long availableRam = host.getRam().getAvailableResource();
                long totalPes = host.getNumberOfPes();
                long freePes = host.getFreePesNumber();
                capacities.add(new HostCapacity(host.getId(), host.getId() == 1 ? 1 : 0,
                        totalRam, totalPes, totalRam - availableRam, totalPes - freePes));
            }
            safety = new Agent6SafetyFirewall().evaluatePlan(plan, capacities);
        }

        ExecutionResult execution = executor.execute(safety);
        MetricsSnapshot after = monitor.captureSnapshot(
                simulation, hosts, executor.getActiveManagedVms(), null);

        // Independent verification: a reported provision must exist on its
        // claimed physical CloudSim host, not only in an agent's log message.
        for (var action : execution.actionRecords()) {
            if (action.success() && action.action() ==
                    com.harsh.cloudsim.agent.optimisation.model.VmScalingAction.PROVISION) {
                boolean confirmed = hosts.stream()
                        .filter(host -> host.getId() == action.actualHostId())
                        .anyMatch(host -> host.getVmList().stream()
                                .anyMatch(vm -> vm.getId() == action.vmId()));
                if (!confirmed) {
                    throw new IllegalStateException(
                            "Execution claimed allocation without CloudSim host inventory: VM "
                            + action.vmId());
                }
            }
        }
        if (execution.provisionedCount() != executor.getActiveManagedVms().size()) {
            throw new IllegalStateException("Managed VM inventory differs from provisioned count");
        }

        EvaluationOutcome evaluation = null;
        String source = "NO_PERFORMANCE_OBSERVATION";
        // No evaluation for rejected/unexecuted operations; a synthetic
        // observation can only exercise Agent 8 code, never prove an SLO.
        if (syntheticObservation != null && (execution.status() == ExecutionStatus.EXECUTED
                || execution.status() == ExecutionStatus.PARTIALLY_EXECUTED)) {
            repository.initializeDatabase();
            evaluation = new DefaultEvaluationAgent(repository).evaluate(
                    execution, syntheticObservation, sloTarget);
            source = "SYNTHETIC_TEST_FIXTURE_NOT_MEASURED_LATENCY";
        }
        return new RunResult(before, forecast, plan, safety, execution,
                after, evaluation, source);
    }

    public static void main(String[] args) throws Exception {
        Path db = Path.of("target", "controlled-pipeline-demo.db");
        Files.createDirectories(db.toAbsolutePath().getParent());
        var repository = new SqliteDecisionMemoryRepository("jdbc:sqlite:" + db.toAbsolutePath());
        var assessment = new EventImpactAssessment("CONTROLLED exam-results fixture",
                EventType.RESULT, "demo-portal", 400, 2.0, 20, 0.9,
                "TEST FIXTURE ONLY: not extracted from a real notice");
        var verification = new EventVerificationResult(VerificationStatus.VERIFIED,
                0.95, "example.invalid", List.of("TEST FIXTURE: verification supplied, not independently performed"));
        var observation = new ObservedPerformance(60_000, 120.0, 0.0, 0.50, 3);
        var target = new SloTarget("demo-portal", 200.0, 0.01, 0.70);
        RunResult result = run(assessment, verification, observation, target, repository);
        System.out.println("CONTROLLED FIXTURE: A1/A2 records injected; no live event verification");
        System.out.println("CloudSim VMs before=" + result.before().runningVms()
                + ", after=" + result.after().runningVms());
        System.out.println("Forecast recommended VMs=" + result.forecast().recommendedVmCount());
        System.out.println("Safety=" + result.safety().approvalStatus()
                + ", approved proposals=" + result.safety().approvedProposals().size());
        System.out.println("Execution=" + result.execution().status()
                + ", confirmed provisioned=" + result.execution().provisionedCount()
                + ", failures=" + result.execution().failedCount());
        System.out.println("SLO sample provenance=" + result.observationSource());
        System.out.println("Evaluation record=" + (result.evaluation() == null
                ? "NOT EVALUATED" : result.evaluation().evaluationId()));
        System.out.println("SQLite file=" + db.toAbsolutePath());
        System.out.println("NOT A LIVE 8-AGENT OR REAL-WORLD PERFORMANCE BENCHMARK");
    }
}

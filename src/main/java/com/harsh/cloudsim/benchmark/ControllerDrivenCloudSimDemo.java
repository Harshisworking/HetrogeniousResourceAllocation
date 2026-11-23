package com.harsh.cloudsim.benchmark;

import com.harsh.cloudsim.model.HeterogeneousCluster;
import com.harsh.cloudsim.agent.verification.evidence.SourceEvidence;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import java.time.Clock;
import java.time.Instant;
import com.harsh.cloudsim.policy.FlexibleVmAllocationPolicy;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Phase 10 experimental runtime integration. Works on a CONTROLLED fixture,
 * not a fetched event, live Kubernetes system or calibrated traffic trace.
 *
 * Unlike Phase 9, scale-out is requested from observations obtained during
 * simulation; incoming tasks are assigned to VMs available at dispatch time.
 * A tiny, identical clock-driving workload creates observation opportunities
 * even when no user tasks are being processed. Marker cloudlets are excluded
 * from end-user latency metrics but consume a negligible amount of simulated CPU.
 */
public final class ControllerDrivenCloudSimDemo {
    private ControllerDrivenCloudSimDemo() {}

    public static final double FIXTURE_BURST_SECONDS = 20.0;
    public static final int USER_TASKS = 24;
    private static final int TASKS_PER_SIMULATED_SECOND = 4;
    private static final long USER_TASK_LENGTH_MI = 20_000;

    public record UserTask(long id, double arrivalSeconds, double dispatchSeconds,
                           double startSeconds, double finishSeconds, double responseSeconds, long vmId) {}
    public record Run(RuntimeScalingController.Mode mode, boolean timingApproved,
                      int submitted, int finished, int failed, double meanResponseSeconds,
                      double makespanSeconds, List<RuntimeScalingController.Decision> decisions,
                      List<UserTask> cloudlets, double extraVmFirstObservedSeconds,
                      double meanDispatchDelaySeconds, WorkloadProfile profile,
                      double baseVmObservedSeconds, double extraVmObservedSeconds,
                      double extraVmDestroyObservedSeconds, double scaleInExecutionRequestSeconds) {
        public Run {
            decisions = List.copyOf(decisions);
            cloudlets = List.copyOf(cloudlets);
        }
    }

    private static Vm baseVm() {
        return new VmSimple(0, 5000, 1).setRam(2048).setBw(1000).setSize(10_000);
    }
    private static Vm extraVm() {
        return new VmSimple(1, 2000, 1).setRam(1024).setBw(1000).setSize(10_000);
    }

    public static Run run(RuntimeScalingController.Mode mode, boolean timingApproved) {
        return run(mode, timingApproved, WorkloadProfile.BURST);
    }

    public static Run run(RuntimeScalingController.Mode mode, boolean timingApproved,
                          WorkloadProfile profile) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(profile);
        RuntimeScalingController.EventSignal fixture =
                new RuntimeScalingController.EventSignal(FIXTURE_BURST_SECONDS, timingApproved);
        return runConfigured(mode, timingApproved, profile, fixture);
    }

    /** Real evidence-gated entry point; rejected evidence falls back to observed-demand
     * scaling. The caller explicitly maps wall-clock event time to the simulation epoch;
     * this cannot be derived from a notice publication date or an LLM forecast.
     * Experimental only: no production infrastructure is provisioned.
     */
    public static Run runWithEvidence(EventVerificationResult verification,
                                      SourceEvidence source, String excerpt, String timestamp,
                                      Clock clock, Instant simulationEpoch, WorkloadProfile profile) {
        Objects.requireNonNull(simulationEpoch, "Explicit simulation epoch required");
        Objects.requireNonNull(profile, "Profile required");
        var gate = EvidenceBackedRuntimeSignal.evaluate(verification, source,
                excerpt, timestamp, clock, simulationEpoch);
        return runConfigured(RuntimeScalingController.Mode.PROACTIVE, gate.approved(),
                profile, gate.signal());
    }

    private static Run runConfigured(RuntimeScalingController.Mode mode, boolean timingApproved,
                                     WorkloadProfile profile,
                                     RuntimeScalingController.EventSignal eventSignal) {
        CloudSim sim = new CloudSim();
        new DatacenterSimple(sim, HeterogeneousCluster.createClusterHosts(),
                new FlexibleVmAllocationPolicy());
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);
        Vm base = baseVm();
        Vm extra = extraVm();
        broker.submitVm(base);
        RuntimeScalingController controller = new RuntimeScalingController(mode,
                RuntimeScalingController.Config.defaults());

        // Clock-driving tiny marker cloudlets: identical across all strategies.
        // No marker task is counted as a user request or in the response metrics.
        List<Cloudlet> markers = new ArrayList<>();
        for (int s = 15; s <= Math.max(120, (int) profile.lastArrivalSeconds() + 120); s++) {
            Cloudlet marker = new CloudletSimple(10_000L + s, 1, 1);
            marker.setSubmissionDelay(s);
            broker.bindCloudletToVm(marker, base);
            markers.add(marker);
        }
        broker.submitCloudletList(markers);

        ArrayDeque<Cloudlet> backlog = new ArrayDeque<>();
        List<Cloudlet> userTasks = new ArrayList<>();
        for (int i = 0; i < profile.taskCount(); i++) {
            Cloudlet task = new CloudletSimple(i, USER_TASK_LENGTH_MI, 1);
            task.setUtilizationModelCpu(new UtilizationModelFull());
            userTasks.add(task);
        }
        final int[] nextArrivalIndex = {0};
        final boolean[] extraRequested = {false};
        SimulationDispatchQuota dispatchQuota = new SimulationDispatchQuota(
                FIXTURE_BURST_SECONDS, 1.0, TASKS_PER_SIMULATED_SECOND);
        Map<Long, Double> dispatchTimes = new HashMap<>();
        final double[] extraVmFirstObserved = {Double.NaN};
        final double[] baseVmFirstObserved = {Double.NaN};
        final double[] extraVmDestroyObserved = {Double.NaN};
        final double[] scaleInExecutionRequest = {Double.NaN};
        final double[] lastProcessed = {-1};
        final List<RuntimeScalingController.Decision> actions = new ArrayList<>();
        sim.addOnClockTickListener(event -> {
            double now = sim.clock();
            // The clock listener may be notified several times at one instant.
            if (now - lastProcessed[0] < 0.000001) return;
            lastProcessed[0] = now;

            // Release each task only at its own scheduled fixture arrival.
            while (nextArrivalIndex[0] < userTasks.size() &&
                    profile.arrivalSeconds(nextArrivalIndex[0]) <= now + 1e-8) {
                backlog.addLast(userTasks.get(nextArrivalIndex[0]++));
            }

            if (base.isCreated() && Double.isNaN(baseVmFirstObserved[0])) {
                baseVmFirstObserved[0] = now;
            }
            if (extra.isCreated() && Double.isNaN(extraVmFirstObserved[0])) {
                extraVmFirstObserved[0] = now;
            }
            if (!extra.isCreated() && Double.isFinite(extraVmFirstObserved[0]) &&
                    Double.isNaN(extraVmDestroyObserved[0])) {
                extraVmDestroyObserved[0] = now;
            }

            int unfinished = backlog.size();
            for (Cloudlet task : userTasks) {
                if (!backlog.contains(task) && !task.isFinished() &&
                        task.getId() < nextArrivalIndex[0]) {
                    unfinished++;
                }
            }
            // CPU utilization comes from the CloudSim VM, not an invented trace.
            double measuredCpu = base.isCreated() ? base.getCpuPercentUtilization() : 0.0;
            if (!Double.isFinite(measuredCpu)) measuredCpu = 0.0;
            measuredCpu = Math.max(0.0, Math.min(1.0, measuredCpu));
            int actualVms = (base.isCreated() ? 1 : 0) + (extra.isCreated() ? 1 : 0);
            if (actualVms >= 1) {
                RuntimeScalingController.Observation snapshot =
                        new RuntimeScalingController.Observation(now, actualVms,
                                unfinished, measuredCpu,
                                mode == RuntimeScalingController.Mode.PROACTIVE ? eventSignal : null);
                RuntimeScalingController.Decision decision = controller.evaluate(snapshot);
                if (decision.action() != RuntimeScalingController.Action.NONE) {
                    actions.add(decision);
                    if (decision.action() == RuntimeScalingController.Action.SCALE_OUT
                            && !extraRequested[0]) {
                        // A real in-simulation request, not VM pre-submission.
                        broker.submitVm(extra);
                        extraRequested[0] = true;
                    }
                    if (decision.action() == RuntimeScalingController.Action.SCALE_IN
                            && extra.isCreated() && unfinished == 0) {
                        // Drain safety: no outstanding user tasks remain.
                        // Record actual broker API invocation separately from policy intent.
                        // An invocation is not proof that a VM was destroyed.
                        scaleInExecutionRequest[0] = now;
                        broker.destroyVm(extra);
                    }
                }
            }

            if (!backlog.isEmpty()) {
                List<Vm> ready = new ArrayList<>();
                if (base.isCreated()) ready.add(base);
                if (extra.isCreated()) ready.add(extra);
                // Callback frequency differs by VM lifecycle and cloudlet finish events.
                // Quota depends only on simulation time, so those extra callbacks
                // cannot grant one scenario more ingress capacity than another.
                int count = dispatchQuota.available(now, backlog.size());
                for (int i = 0; i < count && !ready.isEmpty(); i++) {
                    Cloudlet task = backlog.removeFirst();
                    // Deterministic least-normalized-outstanding-work routing.
                    // The already-submitted workload on each VM (in MI) is
                    // divided by its MIPS; tie breaks to lower VM ID.
                    Vm selected = ready.stream().min(Comparator
                            .comparingDouble((Vm vm) -> outstandingWork(userTasks, vm) / vm.getMips())
                            .thenComparingLong(Vm::getId)).orElseThrow();
                    dispatchTimes.put(task.getId(), now);
                    broker.bindCloudletToVm(task, selected);
                    broker.submitCloudlet(task);
                    dispatchQuota.recordDispatch(1);
                }
            }
        });
        sim.start();

        List<UserTask> completed = new ArrayList<>();
        double total = 0, totalDispatchDelay = 0, latest = FIXTURE_BURST_SECONDS;
        for (Cloudlet task : userTasks) {
            if (!task.isFinished()) continue;
            double start = task.getExecStartTime();
            double finish = task.getFinishTime();
            double arrival = profile.arrivalSeconds((int) task.getId());
            if (!Double.isFinite(start) || !Double.isFinite(finish)
                    || start < arrival - 0.00001 || finish < start) {
                throw new IllegalStateException("Invalid CloudSim timestamp: " + task.getId());
            }
            Double dispatchedAt = dispatchTimes.get(task.getId());
            if (dispatchedAt == null || dispatchedAt < arrival - 0.00001 ||
                    start + 0.00001 < dispatchedAt) {
                throw new IllegalStateException("Invalid dispatch/start ordering: " + task.getId());
            }
            double response = finish - arrival;
            completed.add(new UserTask(task.getId(), arrival,
                    dispatchedAt, start, finish, response, task.getVm().getId()));
            total += response;
            totalDispatchDelay += dispatchedAt - arrival;
            latest = Math.max(latest, finish);
        }
        completed.sort(Comparator.comparingLong(UserTask::id));
        // VM-seconds are observed availability within the measured user-workload window.
        // This is a sampled, non-billing approximation, not an energy model.
        double baseSeconds = observedVmSeconds(baseVmFirstObserved[0], Double.NaN, latest);
        double extraSeconds = observedVmSeconds(extraVmFirstObserved[0],
                extraVmDestroyObserved[0], latest);
        return new Run(mode, timingApproved, profile.taskCount(), completed.size(),
                profile.taskCount() - completed.size(),
                completed.isEmpty() ? Double.NaN : total / completed.size(),
                latest - FIXTURE_BURST_SECONDS, actions, completed,
                extraVmFirstObserved[0],
                completed.isEmpty() ? Double.NaN : totalDispatchDelay / completed.size(),
                profile, baseSeconds, extraSeconds, extraVmDestroyObserved[0],
                scaleInExecutionRequest[0]);
    }

    static double observedVmSeconds(double firstObserved, double destroyedObserved,
                                    double workloadCompletionSeconds) {
        if (!Double.isFinite(firstObserved)) return 0.0;
        double stop = Double.isFinite(destroyedObserved)
                ? Math.min(destroyedObserved, workloadCompletionSeconds)
                : workloadCompletionSeconds;
        return Math.max(0.0, stop - firstObserved);
    }

    private static double outstandingWork(List<Cloudlet> userTasks, Vm vm) {
        double outstandingMi = 0;
        for (Cloudlet task : userTasks) {
            if (!task.isFinished() && task.getVm() == vm) {
                outstandingMi += USER_TASK_LENGTH_MI;
            }
        }
        return outstandingMi;
    }

    public static List<Run> runAll() {
        return List.of(run(RuntimeScalingController.Mode.STATIC, false),
                run(RuntimeScalingController.Mode.REACTIVE, false),
                run(RuntimeScalingController.Mode.PROACTIVE, true),
                run(RuntimeScalingController.Mode.PROACTIVE, false));
    }

    public static void writeCsv(List<Run> results, Path destination) throws IOException {
        if (destination.toAbsolutePath().getParent() != null)
            Files.createDirectories(destination.toAbsolutePath().getParent());
        List<String> rows = new ArrayList<>();
        rows.add("mode,fixture_timing_approved,submitted,finished,failed,mean_response_seconds,makespan_seconds,scale_out_actions,extra_vm_first_observed_seconds,mean_dispatch_delay_seconds");
        for (Run result : results) {
            long scaleOuts = result.decisions().stream().filter(d ->
                    d.action() == RuntimeScalingController.Action.SCALE_OUT).count();
            rows.add(String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%.6f,%.6f,%d,%.6f,%.6f",
                    result.mode(), result.timingApproved(), result.submitted(),
                    result.finished(), result.failed(), result.meanResponseSeconds(),
                    result.makespanSeconds(), scaleOuts, result.extraVmFirstObservedSeconds(),
                    result.meanDispatchDelaySeconds()));
        }
        Files.write(destination, rows, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        List<Run> results = runAll();
        System.out.println("PHASE 10 EXPERIMENTAL RUNTIME CONTROLLER: CONTROLLED EVENT FIXTURE ONLY");
        for (Run r : results) {
            System.out.printf(Locale.ROOT,
                    "%s approved=%s finished=%d/%d meanResponse=%.3fs makespan=%.3fs vm1FirstObserved=%.3fs meanDispatchDelay=%.3fs decisions=%s%n",
                    r.mode(), r.timingApproved(), r.finished(), r.submitted(),
                    r.meanResponseSeconds(), r.makespanSeconds(),
                    r.extraVmFirstObservedSeconds(), r.meanDispatchDelaySeconds(), r.decisions());
            if (r.failed() != 0) throw new IllegalStateException("Incomplete user workload");
        }
        Path csv = Path.of("target", "controller-driven-cloudsim.csv");
        writeCsv(results, csv);
        System.out.println("CSV: " + csv.toAbsolutePath());
    }
}

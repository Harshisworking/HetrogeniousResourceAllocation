package com.harsh.cloudsim.benchmark;

import com.harsh.cloudsim.model.HeterogeneousCluster;
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
    private static final int MAX_DISPATCH_PER_TICK = 4;
    private static final long USER_TASK_LENGTH_MI = 20_000;

    public record UserTask(long id, double arrivalSeconds, double startSeconds,
                           double finishSeconds, double responseSeconds, long vmId) {}
    public record Run(RuntimeScalingController.Mode mode, boolean timingApproved,
                      int submitted, int finished, int failed, double meanResponseSeconds,
                      double makespanSeconds, List<RuntimeScalingController.Decision> decisions,
                      List<UserTask> cloudlets) {
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
        Objects.requireNonNull(mode);
        CloudSim sim = new CloudSim();
        new DatacenterSimple(sim, HeterogeneousCluster.createClusterHosts(),
                new FlexibleVmAllocationPolicy());
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);
        Vm base = baseVm();
        Vm extra = extraVm();
        broker.submitVm(base);
        RuntimeScalingController controller = new RuntimeScalingController(mode,
                RuntimeScalingController.Config.defaults());
        RuntimeScalingController.EventSignal fixture =
                new RuntimeScalingController.EventSignal(FIXTURE_BURST_SECONDS, timingApproved);

        // Clock-driving tiny marker cloudlets: identical across all strategies.
        // No marker task is counted as a user request or in the response metrics.
        List<Cloudlet> markers = new ArrayList<>();
        for (int s = 15; s <= 120; s++) {
            Cloudlet marker = new CloudletSimple(10_000L + s, 1, 1);
            marker.setSubmissionDelay(s);
            broker.bindCloudletToVm(marker, base);
            markers.add(marker);
        }
        broker.submitCloudletList(markers);

        ArrayDeque<Cloudlet> backlog = new ArrayDeque<>();
        List<Cloudlet> userTasks = new ArrayList<>();
        for (int i = 0; i < USER_TASKS; i++) {
            Cloudlet task = new CloudletSimple(i, USER_TASK_LENGTH_MI, 1);
            task.setUtilizationModelCpu(new UtilizationModelFull());
            userTasks.add(task);
        }
        final boolean[] burstArrived = {false};
        final boolean[] extraRequested = {false};
        final int[] routingIndex = {0};
        final double[] lastProcessed = {-1};
        final List<RuntimeScalingController.Decision> actions = new ArrayList<>();
        sim.addOnClockTickListener(event -> {
            double now = sim.clock();
            // The clock listener may be notified several times at one instant.
            if (now - lastProcessed[0] < 0.000001) return;
            lastProcessed[0] = now;

            if (!burstArrived[0] && now >= FIXTURE_BURST_SECONDS) {
                burstArrived[0] = true;
                backlog.addAll(userTasks);
            }

            int unfinished = backlog.size();
            for (Cloudlet task : userTasks) {
                if (!backlog.contains(task) && !task.isFinished() && burstArrived[0]) {
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
                                mode == RuntimeScalingController.Mode.PROACTIVE ? fixture : null);
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
                        broker.destroyVm(extra);
                    }
                }
            }

            if (burstArrived[0]) {
                List<Vm> ready = new ArrayList<>();
                if (base.isCreated()) ready.add(base);
                if (extra.isCreated()) ready.add(extra);
                int count = Math.min(MAX_DISPATCH_PER_TICK, backlog.size());
                for (int i = 0; i < count && !ready.isEmpty(); i++) {
                    Cloudlet task = backlog.removeFirst();
                    Vm selected = ready.get(routingIndex[0]++ % ready.size());
                    broker.bindCloudletToVm(task, selected);
                    // Submitted at actual simulated arrival/dispatch time.
                    broker.submitCloudlet(task);
                }
            }
        });
        sim.start();

        List<UserTask> completed = new ArrayList<>();
        double total = 0, latest = FIXTURE_BURST_SECONDS;
        for (Cloudlet task : userTasks) {
            if (!task.isFinished()) continue;
            double start = task.getExecStartTime();
            double finish = task.getFinishTime();
            if (!Double.isFinite(start) || !Double.isFinite(finish)
                    || start < FIXTURE_BURST_SECONDS - 0.00001 || finish < start) {
                throw new IllegalStateException("Invalid CloudSim timestamp: " + task.getId());
            }
            double response = finish - FIXTURE_BURST_SECONDS;
            completed.add(new UserTask(task.getId(), FIXTURE_BURST_SECONDS,
                    start, finish, response, task.getVm().getId()));
            total += response;
            latest = Math.max(latest, finish);
        }
        completed.sort(Comparator.comparingLong(UserTask::id));
        return new Run(mode, timingApproved, USER_TASKS, completed.size(),
                USER_TASKS - completed.size(),
                completed.isEmpty() ? Double.NaN : total / completed.size(),
                latest - FIXTURE_BURST_SECONDS, actions, completed);
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
        rows.add("mode,fixture_timing_approved,submitted,finished,failed,mean_response_seconds,makespan_seconds,scale_out_actions");
        for (Run result : results) {
            long scaleOuts = result.decisions().stream().filter(d ->
                    d.action() == RuntimeScalingController.Action.SCALE_OUT).count();
            rows.add(String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%.6f,%.6f,%d",
                    result.mode(), result.timingApproved(), result.submitted(),
                    result.finished(), result.failed(), result.meanResponseSeconds(),
                    result.makespanSeconds(), scaleOuts));
        }
        Files.write(destination, rows, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        List<Run> results = runAll();
        System.out.println("PHASE 10 EXPERIMENTAL RUNTIME CONTROLLER: CONTROLLED EVENT FIXTURE ONLY");
        for (Run r : results) {
            System.out.printf(Locale.ROOT,
                    "%s approved=%s finished=%d/%d meanResponse=%.3fs makespan=%.3fs decisions=%s%n",
                    r.mode(), r.timingApproved(), r.finished(), r.submitted(),
                    r.meanResponseSeconds(), r.makespanSeconds(), r.decisions());
            if (r.failed() != 0) throw new IllegalStateException("Incomplete user workload");
        }
        Path csv = Path.of("target", "controller-driven-cloudsim.csv");
        writeCsv(results, csv);
        System.out.println("CSV: " + csv.toAbsolutePath());
    }
}

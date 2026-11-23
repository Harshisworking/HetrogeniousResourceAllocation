package com.harsh.cloudsim.benchmark;

import com.harsh.cloudsim.model.HeterogeneousCluster;
import com.harsh.cloudsim.policy.FlexibleVmAllocationPolicy;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Measured, offline CloudSim Plus cloudlet benchmark. NO live event feed, HPA,
 * HTTP latency, or Kubernetes; the event at 20s is a controlled fixture.
 *
 * Workload and base VM are identical across policies. The extra VM exists
 * only for REACTIVE and PROACTIVE, with different creation times. A stable
 * subset of jobs is bound to the extra VM in both strategies; in STATIC those
 * same jobs are bound to the base VM. This is a capacity/timing comparison,
 * not an equivalent-routing production autoscaler experiment.
 */
public final class CloudSimScalingBenchmark {
    private CloudSimScalingBenchmark() {}

    public enum Strategy { STATIC, REACTIVE, PROACTIVE }

    public record TaskObservation(long cloudletId, double scheduledArrivalSeconds,
                                  double actualStartSeconds, double finishSeconds,
                                  double waitingSeconds, double responseSeconds,
                                  long vmId) {}

    public record Result(Strategy strategy, int submitted, int finished, int failed,
                         double meanWaitingSeconds, double meanResponseSeconds,
                         double p95ResponseSeconds, double makespanSeconds,
                         double extraVmRequestTimeSeconds,
                         List<TaskObservation> tasks) {
        public Result {
            tasks = List.copyOf(tasks);
        }
    }

    /** Controlled synthetic workload: 24 identical tasks at simulation t=20s. */
    public static final double BURST_TIME_SECONDS = 20.0;
    public static final int TASK_COUNT = 24;
    public static final long TASK_LENGTH_MI = 20_000;

    public static Result run(Strategy strategy) {
        if (strategy == null) throw new IllegalArgumentException("strategy required");
        CloudSim simulation = new CloudSim();
        new DatacenterSimple(simulation, HeterogeneousCluster.createClusterHosts(),
                new FlexibleVmAllocationPolicy());
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);

        // The base VM is identical for every strategy. With the existing
        // placement policy this high-MIPS VM is eligible for x86 Host 0.
        Vm baseVm = new VmSimple(0, 5000, 1)
                .setRam(2048).setBw(1000).setSize(10_000);
        broker.submitVm(baseVm);

        double extraVmTime = switch (strategy) {
            case STATIC -> -1.0;
            case PROACTIVE -> 15.0; // 5s before the controlled burst
            case REACTIVE -> 25.0;  // 5s after the controlled burst
        };

        Vm extraVm = null;
        if (strategy != Strategy.STATIC) {
            extraVm = new VmSimple(1, 2000, 1)
                    .setRam(1024).setBw(1000).setSize(10_000);
            extraVm.setSubmissionDelay(extraVmTime);
            broker.submitVm(extraVm);
        }

        List<Cloudlet> tasks = new ArrayList<>();
        for (int i = 0; i < TASK_COUNT; i++) {
            Cloudlet task = new CloudletSimple(i, TASK_LENGTH_MI, 1);
            // Fixed job-to-VM mapping for capacity-on strategies. The same
            // job IDs and lengths are submitted in all three scenarios.
            Vm assigned = (extraVm != null && (i % 3 == 0)) ? extraVm : baseVm;
            // CloudSim applies a bound cloudlet's submission delay AFTER its
            // VM becomes available. Use a VM-relative delay so pre-event VMs
            // receive jobs at the common t=20s burst, and a reactive VM that
            // only becomes available at t=25 receives its jobs immediately.
            // All latency metrics still use the original t=20s demand arrival.
            double vmRelativeDelay = assigned == extraVm
                    ? Math.max(0.0, BURST_TIME_SECONDS - extraVmTime)
                    : BURST_TIME_SECONDS;
            task.setSubmissionDelay(vmRelativeDelay);
            broker.bindCloudletToVm(task, assigned);
            tasks.add(task);
        }
        broker.submitCloudletList(tasks);
        simulation.start();

        List<TaskObservation> observations = new ArrayList<>();
        List<Double> responseTimes = new ArrayList<>();
        double totalWait = 0, totalResponse = 0, lastFinish = BURST_TIME_SECONDS;
        for (Cloudlet task : broker.getCloudletFinishedList()) {
            double start = task.getExecStartTime();
            double finish = task.getFinishTime();
            // CloudSim start/finish are measured simulation clock times;
            // arrival is the configured submission time, not real wall time.
            double wait = Math.max(0.0, start - BURST_TIME_SECONDS);
            double response = finish - BURST_TIME_SECONDS;
            if (!Double.isFinite(start) || !Double.isFinite(finish) || response < 0) {
                throw new IllegalStateException("Invalid CloudSim cloudlet timing: " + task.getId());
            }
            observations.add(new TaskObservation(task.getId(), BURST_TIME_SECONDS,
                    start, finish, wait, response, task.getVm().getId()));
            responseTimes.add(response);
            totalWait += wait;
            totalResponse += response;
            lastFinish = Math.max(lastFinish, finish);
        }
        observations.sort(Comparator.comparingLong(TaskObservation::cloudletId));
        responseTimes.sort(Double::compareTo);
        int finished = observations.size();
        int failed = TASK_COUNT - finished;
        double p95 = finished == 0 ? Double.NaN :
                responseTimes.get((int)Math.ceil(0.95 * finished) - 1);
        return new Result(strategy, TASK_COUNT, finished, failed,
                finished == 0 ? Double.NaN : totalWait / finished,
                finished == 0 ? Double.NaN : totalResponse / finished,
                p95, lastFinish - BURST_TIME_SECONDS, extraVmTime, observations);
    }

    public static List<Result> runAll() {
        return List.of(run(Strategy.STATIC), run(Strategy.REACTIVE), run(Strategy.PROACTIVE));
    }

    public static void writeCsv(List<Result> results, Path output) throws IOException {
        if (output.toAbsolutePath().getParent() != null) {
            Files.createDirectories(output.toAbsolutePath().getParent());
        }
        List<String> lines = new ArrayList<>();
        lines.add("strategy,submitted,finished,failed,mean_wait_seconds,mean_response_seconds,p95_response_seconds,makespan_seconds,extra_vm_request_time_seconds");
        for (Result r : results) {
            lines.add(String.format(Locale.ROOT, "%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.1f",
                    r.strategy(), r.submitted(), r.finished(), r.failed(),
                    r.meanWaitingSeconds(), r.meanResponseSeconds(),
                    r.p95ResponseSeconds(), r.makespanSeconds(),
                    r.extraVmRequestTimeSeconds()));
        }
        Files.write(output, lines, StandardCharsets.UTF_8);
    }

    public static void writeTaskCsv(List<Result> results, Path output) throws IOException {
        if (output.toAbsolutePath().getParent() != null) {
            Files.createDirectories(output.toAbsolutePath().getParent());
        }
        List<String> lines = new ArrayList<>();
        lines.add("strategy,cloudlet_id,arrival_seconds,start_seconds,finish_seconds,waiting_seconds,response_seconds,vm_id");
        for (Result r : results) {
            for (TaskObservation t : r.tasks()) {
                lines.add(String.format(Locale.ROOT, "%s,%d,%.3f,%.6f,%.6f,%.6f,%.6f,%d",
                        r.strategy(), t.cloudletId(), t.scheduledArrivalSeconds(),
                        t.actualStartSeconds(), t.finishSeconds(),
                        t.waitingSeconds(), t.responseSeconds(), t.vmId()));
            }
        }
        Files.write(output, lines, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        List<Result> results = runAll();
        System.out.println("CONTROLLED CLOUDSIM SCALING BENCHMARK — SIMULATION ONLY");
        System.out.println("Synthetic event at t=20s; 24 cloudlets; no live notice or HTTP latency.");
        System.out.println("STATIC=base VM only; PROACTIVE=extra VM requested at 15s; REACTIVE=at 25s.");
        for (Result r : results) {
            System.out.printf(Locale.ROOT,
                    "%s: finished=%d/%d failed=%d avgWait=%.3fs avgResponse=%.3fs p95Response=%.3fs makespan=%.3fs%n",
                    r.strategy(), r.finished(), r.submitted(), r.failed(),
                    r.meanWaitingSeconds(), r.meanResponseSeconds(),
                    r.p95ResponseSeconds(), r.makespanSeconds());
        }
        Path summary = Path.of("target", "cloudsim-scaling-benchmark.csv");
        Path tasks = Path.of("target", "cloudsim-scaling-cloudlets.csv");
        writeCsv(results, summary);
        writeTaskCsv(results, tasks);
        System.out.println("Summary CSV: " + summary.toAbsolutePath());
        System.out.println("Per-cloudlet CSV: " + tasks.toAbsolutePath());
        if (results.stream().anyMatch(r -> r.failed() != 0)) {
            throw new IllegalStateException("Benchmark incomplete: not all CloudSim cloudlets finished");
        }
    }
}

package com.harsh.cloudsim.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 11 controlled simulation matrix. Every mode receives identical task
 * arrivals for each profile, while VM decisions and routing remain dynamic.
 * Observed VM-seconds cover the user-workload completion window, not billing.
 */
public final class MultiWorkloadEvaluation {
    private MultiWorkloadEvaluation() {}

    public static List<ControllerDrivenCloudSimDemo.Run> runMatrix() {
        List<ControllerDrivenCloudSimDemo.Run> results = new ArrayList<>();
        for (WorkloadProfile profile : WorkloadProfile.values()) {
            results.add(ControllerDrivenCloudSimDemo.run(RuntimeScalingController.Mode.STATIC, false, profile));
            results.add(ControllerDrivenCloudSimDemo.run(RuntimeScalingController.Mode.REACTIVE, false, profile));
            results.add(ControllerDrivenCloudSimDemo.run(RuntimeScalingController.Mode.PROACTIVE, true, profile));
            results.add(ControllerDrivenCloudSimDemo.run(RuntimeScalingController.Mode.PROACTIVE, false, profile));
        }
        return List.copyOf(results);
    }

    public static void writeCsv(List<ControllerDrivenCloudSimDemo.Run> runs, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        List<String> lines = new ArrayList<>();
        lines.add("profile,mode,fixture_timing_approved,submitted,finished,failed,mean_response_seconds,makespan_seconds,mean_dispatch_delay_seconds,base_vm_observed_seconds,extra_vm_observed_seconds,total_vm_observed_seconds,extra_vm_first_observed_seconds,extra_vm_destroy_observed_seconds,scale_out_decisions,scale_in_decisions");
        for (var run : runs) {
            long outs = run.decisions().stream().filter(d ->
                    d.action() == RuntimeScalingController.Action.SCALE_OUT).count();
            long ins = run.decisions().stream().filter(d ->
                    d.action() == RuntimeScalingController.Action.SCALE_IN).count();
            lines.add(String.format(Locale.ROOT,
                    "%s,%s,%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d",
                    run.profile(), run.mode(), run.timingApproved(), run.submitted(),
                    run.finished(), run.failed(), run.meanResponseSeconds(), run.makespanSeconds(),
                    run.meanDispatchDelaySeconds(), run.baseVmObservedSeconds(),
                    run.extraVmObservedSeconds(), run.baseVmObservedSeconds() + run.extraVmObservedSeconds(),
                    run.extraVmFirstObservedSeconds(), run.extraVmDestroyObservedSeconds(), outs, ins));
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("PHASE 11 CONTROLLED SYNTHETIC WORKLOADS; NOT LIVE TRAFFIC OR BILLED COST");
        var runs = runMatrix();
        for (var r : runs) {
            if (r.failed() != 0 || r.finished() != r.submitted()) {
                throw new IllegalStateException("Incomplete workload: " + r.profile() + " " + r.mode());
            }
            System.out.printf(Locale.ROOT,
                    "%s %-9s approved=%s finished=%d/%d meanResponse=%.3fs makespan=%.3fs observedVmSeconds=%.3f%n",
                    r.profile(), r.mode(), r.timingApproved(), r.finished(), r.submitted(),
                    r.meanResponseSeconds(), r.makespanSeconds(),
                    r.baseVmObservedSeconds() + r.extraVmObservedSeconds());
        }
        Path output = Path.of("target", "phase11-multi-workload.csv");
        writeCsv(runs, output);
        System.out.println("CSV: " + output.toAbsolutePath());
    }
}

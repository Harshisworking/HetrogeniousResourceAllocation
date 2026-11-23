package com.harsh.cloudsim.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Summarizes controller intents separately from VM state observed by CloudSim.
 * An observation on a clock tick is not an exact VM lifecycle event timestamp.
 * This audit never equates a SCALE_IN decision with successful VM destruction.
 */
public final class VmLifecycleAudit {
    private VmLifecycleAudit() {}

    public enum ScaleInStatus {
        NOT_REQUESTED, REQUESTED_NOT_OBSERVED, OBSERVED_DESTROYED,
        DESTROYED_WITHOUT_SCALE_IN, DECIDED_NOT_EXECUTED, EXECUTED_NOT_OBSERVED
    }

    public record Observation(double scaleOutDecisionSeconds,
                              double extraVmFirstObservedSeconds,
                              double scaleInDecisionSeconds,
                              double extraVmDestroyObservedSeconds,
                              double scaleInExecutionRequestSeconds,
                              ScaleInStatus scaleInStatus) {}

    public static Observation analyze(ControllerDrivenCloudSimDemo.Run run) {
        Objects.requireNonNull(run);
        double scaleOut = firstDecision(run, RuntimeScalingController.Action.SCALE_OUT);
        double scaleIn = firstDecision(run, RuntimeScalingController.Action.SCALE_IN);
        double firstCreated = run.extraVmFirstObservedSeconds();
        double destroyed = run.extraVmDestroyObservedSeconds();
        if (Double.isFinite(destroyed) && !Double.isFinite(firstCreated)) {
            throw new IllegalStateException("VM destruction observed without an earlier VM creation observation");
        }
        if (Double.isFinite(destroyed) && destroyed < firstCreated) {
            throw new IllegalStateException("Observed VM destruction precedes its creation");
        }
        double enacted = run.scaleInExecutionRequestSeconds();
        if (Double.isFinite(enacted) && !Double.isFinite(scaleIn)) {
            throw new IllegalStateException("Destruction request without SCALE_IN decision");
        }
        if (Double.isFinite(enacted) && enacted < scaleIn - 0.00001) {
            throw new IllegalStateException("Destruction request precedes controller decision");
        }
        ScaleInStatus status;
        if (!Double.isFinite(scaleIn)) {
            status = Double.isFinite(destroyed)
                    ? ScaleInStatus.DESTROYED_WITHOUT_SCALE_IN : ScaleInStatus.NOT_REQUESTED;
        } else if (!Double.isFinite(enacted)) {
            status = ScaleInStatus.DECIDED_NOT_EXECUTED;
        } else {
            status = Double.isFinite(destroyed)
                    ? ScaleInStatus.OBSERVED_DESTROYED : ScaleInStatus.EXECUTED_NOT_OBSERVED;
        }
        return new Observation(scaleOut, firstCreated, scaleIn, destroyed, enacted, status);
    }

    private static double firstDecision(ControllerDrivenCloudSimDemo.Run run,
                                        RuntimeScalingController.Action action) {
        return run.decisions().stream().filter(d -> d.action() == action)
                .mapToDouble(RuntimeScalingController.Decision::simulationSeconds)
                .min().orElse(Double.NaN);
    }

    public static void writeCsv(List<ControllerDrivenCloudSimDemo.Run> runs, Path path)
            throws IOException {
        Objects.requireNonNull(runs);
        Objects.requireNonNull(path);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        List<String> rows = new ArrayList<>();
        rows.add("profile,mode,fixture_timing_approved,scale_out_decision_seconds,extra_vm_first_observed_seconds,scale_in_decision_seconds,extra_vm_destroy_observed_seconds,scale_in_execution_request_seconds,scale_in_status");
        for (var run : runs) {
            Observation audit = analyze(run);
            rows.add(String.format(Locale.ROOT, "%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    run.profile(), run.mode(), run.timingApproved(),
                    cell(audit.scaleOutDecisionSeconds()), cell(audit.extraVmFirstObservedSeconds()),
                    cell(audit.scaleInDecisionSeconds()), cell(audit.extraVmDestroyObservedSeconds()),
                    cell(audit.scaleInExecutionRequestSeconds()), audit.scaleInStatus()));
        }
        Files.write(path, rows, StandardCharsets.UTF_8);
    }

    private static String cell(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "";
    }
}

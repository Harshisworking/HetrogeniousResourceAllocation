package com.harsh.cloudsim.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MultiWorkloadEvaluationTest {
    @Test void everySyntheticScenarioCompletesAndRespectsTaskArrivals() {
        for (var profile : WorkloadProfile.values()) {
            for (var mode : RuntimeScalingController.Mode.values()) {
                var run = ControllerDrivenCloudSimDemo.run(mode, false, profile);
                assertEquals(profile, run.profile());
                assertEquals(profile.taskCount(), run.finished());
                assertEquals(0, run.failed());
                assertTrue(run.cloudlets().stream().allMatch(t ->
                        t.dispatchSeconds() + 1e-5 >= t.arrivalSeconds() &&
                        t.startSeconds() + 1e-5 >= t.dispatchSeconds() &&
                        t.finishSeconds() >= t.startSeconds()));
                assertTrue(run.baseVmObservedSeconds() >= 0);
                assertTrue(run.extraVmObservedSeconds() >= 0);
                if (mode == RuntimeScalingController.Mode.STATIC) {
                    assertEquals(0, run.extraVmObservedSeconds());
                }
            }
        }
    }

    @Test void unapprovedEventNeverTriggersPreEventScalingForAnyProfile() {
        for (var profile : WorkloadProfile.values()) {
            var run = ControllerDrivenCloudSimDemo.run(RuntimeScalingController.Mode.PROACTIVE, false, profile);
            assertTrue(run.decisions().stream().noneMatch(d ->
                    d.action() == RuntimeScalingController.Action.SCALE_OUT &&
                    d.simulationSeconds() < 20));
        }
    }

    @Test void vmSecondsAreClampedToMeasuredWorkloadWindow() {
        assertEquals(0, ControllerDrivenCloudSimDemo.observedVmSeconds(Double.NaN, Double.NaN, 50));
        assertEquals(25, ControllerDrivenCloudSimDemo.observedVmSeconds(15, Double.NaN, 40));
        assertEquals(5, ControllerDrivenCloudSimDemo.observedVmSeconds(15, 20, 40));
        assertEquals(0, ControllerDrivenCloudSimDemo.observedVmSeconds(45, Double.NaN, 40));
    }

    @Test void matrixExportsAllProfiles(@TempDir Path dir) throws Exception {
        var runs = MultiWorkloadEvaluation.runMatrix();
        assertEquals(16, runs.size());
        assertTrue(runs.stream().allMatch(r -> r.finished() == r.submitted()));
        Path csv = dir.resolve("matrix.csv");
        MultiWorkloadEvaluation.writeCsv(runs, csv);
        List<String> lines = Files.readAllLines(csv);
        assertEquals(17, lines.size());
        assertTrue(lines.getFirst().contains("total_vm_observed_seconds"));
        assertTrue(lines.stream().anyMatch(s -> s.startsWith("REPEATED_SPIKES,PROACTIVE,false,")));
    }
}

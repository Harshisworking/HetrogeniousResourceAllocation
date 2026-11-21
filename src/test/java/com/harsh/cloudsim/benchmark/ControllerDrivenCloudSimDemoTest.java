package com.harsh.cloudsim.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ControllerDrivenCloudSimDemoTest {
    @Test void controlledStaticBaselineCompletesUserWork() {
        var run = ControllerDrivenCloudSimDemo.run(RuntimeScalingController.Mode.STATIC, false);
        assertEquals(24, run.submitted());
        assertEquals(24, run.finished());
        assertEquals(0, run.failed());
        assertTrue(run.decisions().isEmpty());
        assertEquals(24, run.cloudlets().size());
        assertTrue(run.cloudlets().stream().allMatch(t -> t.vmId() == 0));
    }

    @Test void approvedFixtureCanTriggerProactiveScalingBeforeBurst() {
        var run = ControllerDrivenCloudSimDemo.run(RuntimeScalingController.Mode.PROACTIVE, true);
        assertEquals(24, run.finished());
        assertTrue(run.decisions().stream().anyMatch(d ->
                d.action() == RuntimeScalingController.Action.SCALE_OUT &&
                        d.simulationSeconds() < ControllerDrivenCloudSimDemo.FIXTURE_BURST_SECONDS));
        assertTrue(run.cloudlets().stream().anyMatch(t -> t.vmId() == 1));
    }

    @Test void unapprovedFixtureCannotCausePreEventScaleOut() {
        var run = ControllerDrivenCloudSimDemo.run(RuntimeScalingController.Mode.PROACTIVE, false);
        assertEquals(24, run.finished());
        assertTrue(run.decisions().stream().noneMatch(d ->
                d.action() == RuntimeScalingController.Action.SCALE_OUT &&
                        d.simulationSeconds() < ControllerDrivenCloudSimDemo.FIXTURE_BURST_SECONDS));
    }

    @Test void allUserTaskStartsOccurAfterFixtureArrival() {
        for (var result : ControllerDrivenCloudSimDemo.runAll()) {
            assertEquals(24, result.finished());
            assertTrue(result.cloudlets().stream().allMatch(t ->
                    t.startSeconds() >= t.arrivalSeconds() &&
                    t.finishSeconds() >= t.startSeconds() &&
                    t.responseSeconds() >= 0));
        }
    }

    @Test void dispatchTimesAndVmObservationAreExplicit() {
        for (var run : ControllerDrivenCloudSimDemo.runAll()) {
            assertEquals(24, run.finished());
            assertTrue(run.cloudlets().stream().allMatch(t ->
                    t.dispatchSeconds() >= t.arrivalSeconds() &&
                    t.startSeconds() >= t.dispatchSeconds()));
            assertTrue(run.meanDispatchDelaySeconds() >= 0);
            if (run.mode() == RuntimeScalingController.Mode.STATIC) {
                assertTrue(Double.isNaN(run.extraVmFirstObservedSeconds()));
            } else {
                assertTrue(Double.isFinite(run.extraVmFirstObservedSeconds()));
            }
        }
    }

    @Test void csvExportUsesFourDistinctScenarios(@TempDir Path temp) throws Exception {
        Path csv = temp.resolve("runtime.csv");
        ControllerDrivenCloudSimDemo.writeCsv(ControllerDrivenCloudSimDemo.runAll(), csv);
        List<String> lines = Files.readAllLines(csv);
        assertEquals(5, lines.size());
        assertTrue(lines.get(0).contains("fixture_timing_approved"));
        assertTrue(lines.get(0).contains("extra_vm_first_observed_seconds"));
        assertTrue(lines.stream().anyMatch(s -> s.startsWith("PROACTIVE,false,")));
        assertTrue(lines.stream().anyMatch(s -> s.startsWith("PROACTIVE,true,")));
    }
}

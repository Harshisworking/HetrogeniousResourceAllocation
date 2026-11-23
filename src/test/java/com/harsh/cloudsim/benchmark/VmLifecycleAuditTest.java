package com.harsh.cloudsim.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VmLifecycleAuditTest {
    @Test void staticBaselineHasNoExtraVmLifecycle() {
        var run = ControllerDrivenCloudSimDemo.run(RuntimeScalingController.Mode.STATIC, false);
        var a = VmLifecycleAudit.analyze(run);
        assertEquals(VmLifecycleAudit.ScaleInStatus.NOT_REQUESTED, a.scaleInStatus());
        assertTrue(Double.isNaN(a.scaleOutDecisionSeconds()));
        assertTrue(Double.isNaN(a.extraVmFirstObservedSeconds()));
    }

    @Test void scaleOutRequestAndObservedAvailabilityAreSeparateTimes() {
        var run = ControllerDrivenCloudSimDemo.run(RuntimeScalingController.Mode.REACTIVE, false);
        var a = VmLifecycleAudit.analyze(run);
        assertTrue(Double.isFinite(a.scaleOutDecisionSeconds()));
        assertTrue(Double.isFinite(a.extraVmFirstObservedSeconds()));
        assertTrue(a.extraVmFirstObservedSeconds() >= a.scaleOutDecisionSeconds());
    }

    @Test void approvedSyntheticProactiveRequestsCapacityBeforeArrival() {
        var run = ControllerDrivenCloudSimDemo.run(RuntimeScalingController.Mode.PROACTIVE, true);
        var a = VmLifecycleAudit.analyze(run);
        assertTrue(a.scaleOutDecisionSeconds() < ControllerDrivenCloudSimDemo.FIXTURE_BURST_SECONDS);
    }

    @Test void csvPreservesSeparateDecisionAndObservedLifecycleFields(@TempDir Path dir) throws Exception {
        var run = ControllerDrivenCloudSimDemo.run(RuntimeScalingController.Mode.REACTIVE, false);
        Path csv = dir.resolve("lifecycle.csv");
        VmLifecycleAudit.writeCsv(List.of(run), csv);
        var lines = Files.readAllLines(csv);
        assertEquals(2, lines.size());
        assertTrue(lines.getFirst().contains("scale_in_status"));
        assertTrue(lines.get(1).startsWith("BURST,REACTIVE,false,"));
    }
}

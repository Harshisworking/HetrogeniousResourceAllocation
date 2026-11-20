package com.harsh.cloudsim.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.harsh.cloudsim.benchmark.RuntimeScalingController.*;

class RuntimeScalingControllerTest {
    private static final Config CFG = Config.defaults();
    private static Observation snapshot(double t, int vms, int queue, double util, EventSignal event) {
        return new Observation(t, vms, queue, util, event);
    }
    @Test void staticNeverScalesDespiteHighDemand() {
        var controller = new RuntimeScalingController(Mode.STATIC, CFG);
        assertEquals(Action.NONE, controller.evaluate(snapshot(20, 1, 24, 1, null)).action());
    }
    @Test void reactiveUsesObservedLoadRatherThanFixedTime() {
        var controller = new RuntimeScalingController(Mode.REACTIVE, CFG);
        assertEquals(Action.NONE, controller.evaluate(snapshot(15, 1, 0, 0.1, null)).action());
        assertEquals(Action.NONE, controller.evaluate(snapshot(20, 1, 3, 1.0, null)).action());
        assertEquals(Action.SCALE_OUT, controller.evaluate(snapshot(21, 1, 4, 0.9, null)).action());
        assertEquals(Action.NONE, controller.evaluate(snapshot(22, 2, 0, 0.1, null)).action());
    }
    @Test void proactiveRequiresApprovedEventTime() {
        var controller = new RuntimeScalingController(Mode.PROACTIVE, CFG);
        assertEquals(Action.NONE, controller.evaluate(snapshot(15, 1, 0, 0.1, new EventSignal(20, false))).action());
        assertEquals(Action.SCALE_OUT, controller.evaluate(snapshot(16, 1, 0, 0.1, new EventSignal(20, true))).action());
    }
    @Test void proactiveCanFallbackToReactiveWithoutInventingEvidence() {
        var controller = new RuntimeScalingController(Mode.PROACTIVE, CFG);
        assertEquals(Action.SCALE_OUT, controller.evaluate(snapshot(20, 1, 24, 1, new EventSignal(30, false))).action());
    }
    @Test void cooldownAndMinimumCapacityPreventFlapping() {
        var controller = new RuntimeScalingController(Mode.REACTIVE, CFG);
        assertEquals(Action.SCALE_OUT, controller.evaluate(snapshot(20, 1, 24, 1, null)).action());
        assertEquals(Action.NONE, controller.evaluate(snapshot(21, 2, 0, 0.1, null)).action());
        assertEquals(Action.SCALE_IN, controller.evaluate(snapshot(31, 2, 0, 0.1, null)).action());
        assertEquals(Action.NONE, controller.evaluate(snapshot(42, 1, 0, 0.1, null)).action());
    }
    @Test void rejectsInvalidAndNonMonotonicObservations() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 1, 0, 1.2, null));
        var controller = new RuntimeScalingController(Mode.REACTIVE, CFG);
        controller.evaluate(snapshot(20, 1, 0, 0.1, null));
        assertThrows(IllegalArgumentException.class, () -> controller.evaluate(snapshot(19, 1, 0, 0.1, null)));
    }
    @Test void decisionsAreAppendOnlyAndHaveCapacityTargets() {
        var controller = new RuntimeScalingController(Mode.REACTIVE, CFG);
        var decision = controller.evaluate(snapshot(20, 1, 24, 1, null));
        assertEquals(2, decision.requestedVmCount());
        assertEquals(1, controller.history().size());
        assertThrows(UnsupportedOperationException.class, () -> controller.history().clear());
    }
}

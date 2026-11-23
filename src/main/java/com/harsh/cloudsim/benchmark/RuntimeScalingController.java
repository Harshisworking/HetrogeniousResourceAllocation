package com.harsh.cloudsim.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic scaling decision core. Feed it observations taken from a
 * simulation (or from a future adapter); this class does not create VMs.
 * All timestamps are simulated seconds and must be monotonic.
 */
public final class RuntimeScalingController {
    public enum Mode { STATIC, REACTIVE, PROACTIVE }
    public enum Action { NONE, SCALE_OUT, SCALE_IN }
    public record Config(double highUtilization, double lowUtilization,
                         int queuedTasksThreshold, double cooldownSeconds,
                         int minimumVms, int maximumVms, double proactiveLeadSeconds) {
        public Config {
            if (!(highUtilization > 0 && highUtilization <= 1) ||
                !(lowUtilization >= 0 && lowUtilization < highUtilization) ||
                queuedTasksThreshold < 1 || !(cooldownSeconds >= 0) ||
                minimumVms < 1 || maximumVms < minimumVms ||
                !(proactiveLeadSeconds >= 0)) {
                throw new IllegalArgumentException("Invalid controller configuration");
            }
        }
        public static Config defaults() {
            return new Config(0.80, 0.25, 4, 10.0, 1, 2, 5.0);
        }
    }
    /** A trusted event time exists only in a controlled fixture or after a real evidence gate. */
    public record EventSignal(double serviceOpeningSeconds, boolean timingEvidenceApproved) {
        public EventSignal {
            if (!Double.isFinite(serviceOpeningSeconds) || serviceOpeningSeconds < 0)
                throw new IllegalArgumentException("Invalid event time");
        }
    }
    public record Observation(double simulationSeconds, int activeVms, int queuedTasks,
                              double cpuUtilization, EventSignal event) {
        public Observation {
            if (!Double.isFinite(simulationSeconds) || simulationSeconds < 0 ||
                activeVms < 0 || queuedTasks < 0 ||
                !Double.isFinite(cpuUtilization) || cpuUtilization < 0 || cpuUtilization > 1) {
                throw new IllegalArgumentException("Invalid controller observation");
            }
        }
    }
    public record Decision(double simulationSeconds, Mode mode, Action action, String reason,
                           int requestedVmCount) {
        public Decision {
            Objects.requireNonNull(mode);
            Objects.requireNonNull(action);
            Objects.requireNonNull(reason);
        }
    }

    private final Mode mode;
    private final Config config;
    private final List<Decision> history = new ArrayList<>();
    private double lastObservation = -1;
    private double lastChange = Double.NEGATIVE_INFINITY;
    private boolean proactiveTriggered;

    public RuntimeScalingController(Mode mode, Config config) {
        this.mode = Objects.requireNonNull(mode);
        this.config = Objects.requireNonNull(config);
    }

    /** Evaluates one snapshot; the caller must enact and subsequently observe VM changes. */
    public Decision evaluate(Observation observation) {
        Objects.requireNonNull(observation);
        double now = observation.simulationSeconds();
        if (now < lastObservation) throw new IllegalArgumentException("Non-monotonic simulation time");
        lastObservation = now;

        Action action = Action.NONE;
        String reason;
        if (mode == Mode.STATIC) {
            reason = "Static baseline has no controller actions";
        } else if (now - lastChange < config.cooldownSeconds()) {
            reason = "Cooldown active";
        } else if (mode == Mode.PROACTIVE && !proactiveTriggered &&
                observation.event() != null && observation.event().timingEvidenceApproved() &&
                now <= observation.event().serviceOpeningSeconds() &&
                now + config.proactiveLeadSeconds() >= observation.event().serviceOpeningSeconds() &&
                observation.activeVms() < config.maximumVms()) {
            action = Action.SCALE_OUT;
            proactiveTriggered = true;
            reason = "Approved event timing is within proactive lead window";
        } else if (observation.queuedTasks() >= config.queuedTasksThreshold() &&
                observation.cpuUtilization() >= config.highUtilization() &&
                observation.activeVms() < config.maximumVms()) {
            action = Action.SCALE_OUT;
            reason = "Observed queue and CPU utilization exceed scale-out thresholds";
        } else if (observation.queuedTasks() == 0 &&
                observation.cpuUtilization() <= config.lowUtilization() &&
                observation.activeVms() > config.minimumVms()) {
            action = Action.SCALE_IN;
            reason = "Low utilization with empty queue";
        } else {
            reason = mode == Mode.PROACTIVE && observation.event() != null &&
                    !observation.event().timingEvidenceApproved()
                    ? "Event timing not approved; only observed-demand scaling is permitted"
                    : "No threshold crossing or capacity limit reached";
        }
        if (action != Action.NONE) lastChange = now;
        int requested = observation.activeVms() +
                (action == Action.SCALE_OUT ? 1 : action == Action.SCALE_IN ? -1 : 0);
        Decision decision = new Decision(now, mode, action, reason, requested);
        history.add(decision);
        return decision;
    }

    public List<Decision> history() { return List.copyOf(history); }
}

package com.harsh.cloudsim.benchmark;

/**
 * Controlled, deterministic arrivals measured in CloudSim seconds.
 * These fixtures are NOT observed website traces or event forecasts.
 */
public enum WorkloadProfile {
    QUIET, BURST, SUSTAINED, REPEATED_SPIKES;

    public int taskCount() {
        return switch (this) {
            case QUIET -> 8;
            case BURST -> 24;
            case SUSTAINED -> 48;
            case REPEATED_SPIKES -> 24;
        };
    }

    public double arrivalSeconds(int index) {
        if (index < 0 || index >= taskCount()) {
            throw new IllegalArgumentException("Invalid profile task index");
        }
        return switch (this) {
            case QUIET, BURST -> 20.0;
            case SUSTAINED -> 20.0 + index / 2;
            case REPEATED_SPIKES -> index < 12 ? 20.0 : 60.0;
        };
    }

    public double lastArrivalSeconds() { return arrivalSeconds(taskCount() - 1); }
}

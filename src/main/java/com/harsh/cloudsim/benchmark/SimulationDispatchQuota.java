package com.harsh.cloudsim.benchmark;

/**
 * Deterministic dispatch rate limiter based on simulated time rather than the
 * number of CloudSim callbacks. The first batch is allowed at burst arrival;
 * subsequent batches are released once per elapsed interval. This deliberately
 * models an external ingress throughput cap, not actual measured HTTP traffic.
 */
public final class SimulationDispatchQuota {
    private final double arrivalSeconds;
    private final double intervalSeconds;
    private final int tasksPerInterval;
    private int dispatched;
    private double lastTime = -1;

    public SimulationDispatchQuota(double arrivalSeconds, double intervalSeconds, int tasksPerInterval) {
        if (!Double.isFinite(arrivalSeconds) || arrivalSeconds < 0 ||
                !Double.isFinite(intervalSeconds) || intervalSeconds <= 0 ||
                tasksPerInterval <= 0) {
            throw new IllegalArgumentException("Invalid simulated dispatch schedule");
        }
        this.arrivalSeconds = arrivalSeconds;
        this.intervalSeconds = intervalSeconds;
        this.tasksPerInterval = tasksPerInterval;
    }

    public int available(double now, int pending) {
        if (!Double.isFinite(now) || now < 0 || now + 1e-8 < lastTime || pending < 0)
            throw new IllegalArgumentException("Invalid/retrograde dispatch observation");
        lastTime = now;
        if (now + 1e-8 < arrivalSeconds) return 0;
        long elapsedIntervals = (long) Math.floor((now - arrivalSeconds) / intervalSeconds + 1e-7);
        long unlocked = (elapsedIntervals + 1) * (long) tasksPerInterval;
        long remaining = Math.max(0L, unlocked - dispatched);
        return (int) Math.min(pending, Math.min(remaining, Integer.MAX_VALUE));
    }

    public void recordDispatch(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative dispatch count");
        dispatched = Math.addExact(dispatched, count);
    }

    public int dispatched() { return dispatched; }
}

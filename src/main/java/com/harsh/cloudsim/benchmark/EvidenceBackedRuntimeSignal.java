package com.harsh.cloudsim.benchmark;

import com.harsh.cloudsim.agent.verification.evidence.SourceEvidence;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.integration.EvidenceBackedTimingGate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Adapter from the real Phase 7 evidence gate to the experimental CloudSim time axis.
 * A denied gate produces NO proactive signal: the runtime controller may still
 * react to measured demand. No synthetic approval boolean is accepted here.
 */
public final class EvidenceBackedRuntimeSignal {
    private EvidenceBackedRuntimeSignal() {}

    public record Result(EvidenceBackedTimingGate.Decision gate,
                         RuntimeScalingController.EventSignal signal) {
        public boolean approved() { return signal != null; }
    }

    public static Result evaluate(EventVerificationResult verification,
                                  SourceEvidence source, String exactExcerpt,
                                  String exactTimestamp, Clock clock,
                                  Instant simulationEpoch) {
        Objects.requireNonNull(simulationEpoch, "Simulation epoch required");
        var result = EvidenceBackedTimingGate.evaluate(verification, source,
                exactExcerpt, exactTimestamp, clock);
        if (!result.eligible()) return new Result(result, null);
        double simulatedSeconds = Duration.between(simulationEpoch,
                result.serviceStart()).toMillis() / 1000.0;
        // Require the explicitly configured simulation interval to contain the
        // event; do not silently warp time or run a multi-day marker simulation.
        if (!(simulatedSeconds >= 0.0 && simulatedSeconds <= 120.0)) {
            throw new IllegalArgumentException(
                    "Verified event does not fit the configured 0..120s simulation window");
        }
        return new Result(result, new RuntimeScalingController.EventSignal(simulatedSeconds, true));
    }
}

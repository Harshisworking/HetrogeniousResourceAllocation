package com.harsh.cloudsim.benchmark;

import com.harsh.cloudsim.agent.verification.evidence.SourceEvidence;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceBackedRuntimeSignalTest {
    static final Instant NOW = Instant.parse("2026-11-22T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final String TIME = "2026-11-22T00:00:20+00:00";
    static final String EXCERPT = "Student registration portal opens at " + TIME + " for applications.";
    static SourceEvidence page() {
        return SourceEvidence.fetched("https://example.org/notice", "https://example.org/notice",
                200, "text/html", "Notice", EXCERPT);
    }
    static EventVerificationResult verification(VerificationStatus status) {
        return new EventVerificationResult(status, 0.95, "example.org", List.of("Fixture"));
    }
    @Test void verifiedPageEvidenceConvertsToExactSimulatedOpening() {
        var result = EvidenceBackedRuntimeSignal.evaluate(verification(VerificationStatus.VERIFIED),
                page(), EXCERPT, TIME, CLOCK, NOW);
        assertTrue(result.approved());
        assertEquals(20.0, result.signal().serviceOpeningSeconds(), 0.000001);
    }
    @Test void unverifiedSourceCannotAuthorizeSignal() {
        var result = EvidenceBackedRuntimeSignal.evaluate(verification(VerificationStatus.MANUAL_REVIEW),
                page(), EXCERPT, TIME, CLOCK, NOW);
        assertFalse(result.approved());
        assertNull(result.signal());
    }
    @Test void missingExcerptCannotAuthorizeSignal() {
        var result = EvidenceBackedRuntimeSignal.evaluate(verification(VerificationStatus.VERIFIED),
                page(), "", TIME, CLOCK, NOW);
        assertFalse(result.approved());
    }
    @Test void noSilentTimeWarpingOutsideSimulationWindow() {
        assertThrows(IllegalArgumentException.class, () ->
                EvidenceBackedRuntimeSignal.evaluate(verification(VerificationStatus.VERIFIED),
                    page(), EXCERPT, TIME, CLOCK, NOW.minusSeconds(500)));
    }
    @Test void runtimeDemoUsesActualGateApprovalForPreEventScaleOut() {
        var run = ControllerDrivenCloudSimDemo.runWithEvidence(
                verification(VerificationStatus.VERIFIED), page(), EXCERPT, TIME,
                CLOCK, NOW, WorkloadProfile.BURST);
        assertTrue(run.timingApproved());
        assertEquals(24, run.finished());
        assertTrue(run.decisions().stream().anyMatch(d ->
                d.action() == RuntimeScalingController.Action.SCALE_OUT &&
                d.simulationSeconds() < 20));
    }
    @Test void runtimeDemoRejectsPreEventScaleOutWithoutVerifiedTiming() {
        var run = ControllerDrivenCloudSimDemo.runWithEvidence(
                verification(VerificationStatus.MANUAL_REVIEW), page(), EXCERPT, TIME,
                CLOCK, NOW, WorkloadProfile.BURST);
        assertFalse(run.timingApproved());
        assertEquals(24, run.finished());
        assertTrue(run.decisions().stream().noneMatch(d ->
                d.action() == RuntimeScalingController.Action.SCALE_OUT &&
                d.simulationSeconds() < 20));
    }
}

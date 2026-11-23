package com.harsh.cloudsim.integration;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;

import java.util.Objects;
import java.time.Clock;
import com.harsh.cloudsim.agent.verification.evidence.SourceEvidence;

/**
 * Explicit, simulation-only handoff for a verified event.
 *
 * Verification means the event claim was supported by source evidence. It does
 * NOT establish that the LLM-estimated audience size, traffic multiplier, or
 * lead time are accurate. The caller must explicitly acknowledge that these
 * estimates are uncalibrated before running a sandbox CloudSim demonstration.
 *
 * This does not authorize Kubernetes or any physical infrastructure actions.
 */
public final class VerifiedEventCloudSimHandoff {
    public static final String ACKNOWLEDGEMENT = "SIMULATE";

    private VerifiedEventCloudSimHandoff() {}

    public static ControlledPipelineDemo.RunResult run(
            EventImpactAssessment assessment,
            EventVerificationResult finalVerification,
            String acknowledgement) {
        Objects.requireNonNull(assessment, "Assessment required");
        Objects.requireNonNull(finalVerification, "Final verification required");
        if (finalVerification.status() != VerificationStatus.VERIFIED) {
            throw new IllegalArgumentException(
                    "CloudSim handoff denied: final event status is "
                            + finalVerification.status());
        }
        if (!ACKNOWLEDGEMENT.equals(acknowledgement)) {
            throw new IllegalArgumentException(
                    "CloudSim handoff denied: explicit SIMULATE acknowledgement required. "
                            + "Event verification does not validate traffic forecasts or event timing.");
        }
        return ControlledPipelineDemo.run(
                assessment, finalVerification, null, null, null);
    }

    /** Proactive mode: stronger evidence gate than the legacy simulation-only run(). */
    public static ControlledPipelineDemo.RunResult runProactive(
            EventImpactAssessment assessment,
            EventVerificationResult finalVerification,
            SourceEvidence sourceEvidence,
            String sourceExcerpt,
            String sourceTimestamp,
            String acknowledgement,
            Clock clock) {
        // Enforce evidence and time before allocating any CloudSim VM.
        var decision = EvidenceBackedTimingGate.evaluate(
                finalVerification, sourceEvidence, sourceExcerpt, sourceTimestamp, clock);
        if (!decision.eligible()) {
            throw new IllegalArgumentException("Proactive handoff denied: "
                    + decision.status() + ": " + decision.explanation());
        }
        return run(assessment, finalVerification, acknowledgement);
    }

}

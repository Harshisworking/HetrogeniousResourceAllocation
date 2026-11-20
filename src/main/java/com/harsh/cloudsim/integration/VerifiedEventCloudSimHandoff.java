package com.harsh.cloudsim.integration;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;

import java.util.Objects;

/**
 * Trust-boundary handoff for the event CLI after FULL evidence-backed verification.
 *
 * This adapter does not perform Gemini/HTTP verification itself. The caller must
 * use the final EventVerificationAgent.verify(request, evidence, claimResult)
 * result, never an intermediate or user-supplied assertion of verification.
 *
 * CloudSim VMs are allocated in-memory; no K8s changes or performance metrics.
 */
public final class VerifiedEventCloudSimHandoff {
    private VerifiedEventCloudSimHandoff() {}

    public static ControlledPipelineDemo.RunResult run(
            EventImpactAssessment assessment,
            EventVerificationResult finalVerification) {
        Objects.requireNonNull(assessment, "Assessment required");
        Objects.requireNonNull(finalVerification, "Final verification required");
        if (finalVerification.status() != VerificationStatus.VERIFIED) {
            throw new IllegalArgumentException(
                    "CloudSim handoff denied: final event status is "
                            + finalVerification.status());
        }
        // No synthetic or fabricated SLO measurements are passed to Agent 8.
        return ControlledPipelineDemo.run(
                assessment, finalVerification, null, null, null);
    }
}

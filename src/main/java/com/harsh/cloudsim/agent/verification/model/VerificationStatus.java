package com.harsh.cloudsim.agent.verification.model;

/**
 * Final outcome produced by the Event Verification Agent.
 */
public enum VerificationStatus {

    /**
     * The assessment passed all automatic verification checks.
     */
    VERIFIED,

    /**
     * The assessment is plausible but requires human confirmation.
     */
    MANUAL_REVIEW,

    /**
     * The assessment failed important trust or consistency checks.
     */
    REJECTED
}
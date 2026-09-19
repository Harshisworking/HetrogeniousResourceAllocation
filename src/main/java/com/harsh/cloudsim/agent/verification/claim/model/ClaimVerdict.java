package com.harsh.cloudsim.agent.verification.claim.model;

/**
 * Describes how strongly the retrieved source evidence supports
 * the submitted event claim.
 */
public enum ClaimVerdict {

    /**
     * All important parts of the claim are present in the evidence.
     */
    SUPPORTED,

    /**
     * Some claim details are supported, while others are absent
     * or inconsistent.
     */
    PARTIALLY_SUPPORTED,

    /**
     * The evidence does not support the important event claims.
     */
    NOT_SUPPORTED
}
package com.harsh.cloudsim.agent.verification.claim;

/**
 * Thrown when the claim-matching LLM returns malformed,
 * incomplete or unsafe structured output.
 */
public final class ClaimVerificationParsingException
        extends IllegalArgumentException {

    public ClaimVerificationParsingException(
            String message
    ) {
        super(message);
    }

    public ClaimVerificationParsingException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
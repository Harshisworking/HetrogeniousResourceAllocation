package com.harsh.cloudsim.agent.verification.evidence;

/**
 * Retrieves webpage evidence for event verification.
 */
@FunctionalInterface
public interface SourceEvidenceFetcher {

    /**
     * Fetches evidence from the supplied source URL or domain.
     */
    SourceEvidence fetch(String sourceReference);
}
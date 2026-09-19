package com.harsh.cloudsim.agent.verification.evidence;

/**
 * Outcome of attempting to retrieve evidence from a source webpage.
 */
public enum EvidenceFetchStatus {

    /**
     * The webpage was fetched and visible text was extracted.
     */
    FETCHED,

    /**
     * The URL was rejected by security or trusted-domain rules.
     */
    BLOCKED,

    /**
     * The requested webpage returned HTTP 404.
     */
    NOT_FOUND,

    /**
     * The webpage could not be reached or returned an error.
     */
    UNREACHABLE,

    /**
     * The response was not a supported textual document.
     */
    UNSUPPORTED_CONTENT,

    /**
     * The webpage exceeded the configured response-size limit.
     */
    CONTENT_TOO_LARGE,

    /**
     * The redirect chain exceeded the safe limit.
     */
    TOO_MANY_REDIRECTS
}
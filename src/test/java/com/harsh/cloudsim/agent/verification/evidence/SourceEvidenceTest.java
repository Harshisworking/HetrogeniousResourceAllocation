package com.harsh.cloudsim.agent.verification.evidence;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceEvidenceTest {

    @Test
    void shouldCreateFetchedEvidence() {
        SourceEvidence evidence =
                SourceEvidence.fetched(
                        "https://ipu.ac.in/notice",
                        "https://ipu.ac.in/notice",
                        200,
                        "text/html",
                        "Admission Notice",
                        "Admission registration opens tomorrow."
                );

        assertAll(
                () -> assertTrue(evidence.wasFetched()),
                () -> assertEquals(
                        EvidenceFetchStatus.FETCHED,
                        evidence.status()
                ),
                () -> assertEquals(
                        200,
                        evidence.httpStatusCode()
                )
        );
    }

    @Test
    void shouldCreateFailureEvidence() {
        SourceEvidence evidence =
                SourceEvidence.failure(
                        "https://ipu.ac.in/missing",
                        "https://ipu.ac.in/missing",
                        EvidenceFetchStatus.NOT_FOUND,
                        404,
                        "Source webpage was not found."
                );

        assertAll(
                () -> assertFalse(evidence.wasFetched()),
                () -> assertEquals(
                        EvidenceFetchStatus.NOT_FOUND,
                        evidence.status()
                ),
                () -> assertEquals(
                        404,
                        evidence.httpStatusCode()
                )
        );
    }

    @Test
    void shouldRejectFetchedEvidenceWithoutText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceEvidence(
                        "https://ipu.ac.in",
                        "https://ipu.ac.in",
                        EvidenceFetchStatus.FETCHED,
                        200,
                        Instant.now(),
                        "text/html",
                        "IPU",
                        "   ",
                        "Fetched successfully."
                )
        );
    }

    @Test
    void shouldRejectFetchedEvidenceWithErrorStatus() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceEvidence(
                        "https://ipu.ac.in",
                        "https://ipu.ac.in",
                        EvidenceFetchStatus.FETCHED,
                        500,
                        Instant.now(),
                        "text/html",
                        "IPU",
                        "Some visible text",
                        "Fetched successfully."
                )
        );
    }

    @Test
    void shouldRejectInvalidHttpStatusCode() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> invalidStatus(-1)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> invalidStatus(600)
                )
        );
    }

    private SourceEvidence invalidStatus(int statusCode) {
        return new SourceEvidence(
                "https://ipu.ac.in",
                "unknown",
                EvidenceFetchStatus.UNREACHABLE,
                statusCode,
                Instant.now(),
                "",
                "",
                "",
                "Unable to fetch source."
        );
    }
}
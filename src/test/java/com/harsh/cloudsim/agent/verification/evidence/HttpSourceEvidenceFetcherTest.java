package com.harsh.cloudsim.agent.verification.evidence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpSourceEvidenceFetcherTest {

    private HttpSourceEvidenceFetcher fetcher;

    @BeforeEach
    void createFetcher() {
        fetcher = new HttpSourceEvidenceFetcher(
                Set.of("ipu.ac.in")
        );
    }

    @Test
    void shouldBlockMalformedSourceWithoutNetworkRequest() {
        SourceEvidence evidence =
                fetcher.fetch("not a valid source");

        assertAll(
                () -> assertEquals(
                        EvidenceFetchStatus.BLOCKED,
                        evidence.status()
                ),
                () -> assertFalse(
                        evidence.wasFetched()
                )
        );
    }

    @Test
    void shouldBlockNonHttpsSource() {
        SourceEvidence evidence =
                fetcher.fetch(
                        "http://ipu.ac.in/admission"
                );

        assertEquals(
                EvidenceFetchStatus.BLOCKED,
                evidence.status()
        );
    }

    @Test
    void shouldBlockUntrustedDomain() {
        SourceEvidence evidence =
                fetcher.fetch(
                        "https://attacker.example/event"
                );

        assertEquals(
                EvidenceFetchStatus.BLOCKED,
                evidence.status()
        );
    }

    @Test
    void shouldBlockCredentialStyleUrl() {
        SourceEvidence evidence =
                fetcher.fetch(
                        "https://ipu.ac.in@attacker.example/event"
                );

        assertEquals(
                EvidenceFetchStatus.BLOCKED,
                evidence.status()
        );
    }

    @Test
    void shouldRejectInvalidTrustedDomainConfiguration() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new HttpSourceEvidenceFetcher(
                                Set.of()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new HttpSourceEvidenceFetcher(
                                Set.of("not a valid domain")
                        )
                )
        );
    }
}
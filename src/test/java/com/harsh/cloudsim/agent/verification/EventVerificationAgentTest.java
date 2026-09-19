package com.harsh.cloudsim.agent.verification;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;
import com.harsh.cloudsim.agent.verification.model.EventVerificationRequest;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventVerificationAgentTest {

    private EventVerificationAgent agent;

    @BeforeEach
    void createAgent() {
        agent = new EventVerificationAgent(
                Set.of("ipu.ac.in")
        );
    }

    @Test
    void shouldRequireEvidenceForTrustedHighConfidenceEvent() {
        EventVerificationResult result = agent.verify(
                request(
                        assessment(0.90, 10_000),
                        "https://ipu.ac.in/admission"
                )
        );

        assertAll(
                () -> assertEquals(
                        VerificationStatus.MANUAL_REVIEW,
                        result.status()
                ),
                () -> assertEquals(
                        "ipu.ac.in",
                        result.normalizedSourceDomain()
                ),
                () -> assertTrue(
                        result.verificationScore() >= 0.75
                ),
                () -> assertTrue(
                        result.reasons().stream().anyMatch(
                                reason -> reason.contains(
                                        "evidence has not yet been"
                                )
                        )
                )
        );
    }

    @Test
    void shouldRecognizeRealSubdomainButRequireEvidence() {
        EventVerificationResult result = agent.verify(
                request(
                        assessment(0.90, 10_000),
                        "https://admissions.ipu.ac.in/register"
                )
        );

        assertAll(
                () -> assertEquals(
                        VerificationStatus.MANUAL_REVIEW,
                        result.status()
                ),
                () -> assertEquals(
                        "admissions.ipu.ac.in",
                        result.normalizedSourceDomain()
                )
        );
    }

    @Test
    void shouldNotTrustLookalikeDomain() {
        EventVerificationResult result = agent.verify(
                request(
                        assessment(0.90, 10_000),
                        "https://ipu.ac.in.attacker.example/event"
                )
        );

        assertAll(
                () -> assertNotEquals(
                        VerificationStatus.VERIFIED,
                        result.status()
                ),
                () -> assertEquals(
                        VerificationStatus.MANUAL_REVIEW,
                        result.status()
                )
        );
    }

    @Test
    void shouldRequireReviewForUntrustedSource() {
        EventVerificationResult result = agent.verify(
                request(
                        assessment(0.90, 10_000),
                        "https://news.example/event"
                )
        );

        assertEquals(
                VerificationStatus.MANUAL_REVIEW,
                result.status()
        );
    }

    @Test
    void shouldRejectLowConfidenceAssessment() {
        EventVerificationResult result = agent.verify(
                request(
                        assessment(0.30, 10_000),
                        "https://ipu.ac.in/admission"
                )
        );

        assertEquals(
                VerificationStatus.REJECTED,
                result.status()
        );
    }

    @Test
    void shouldRequireReviewWhenAudienceIsUnknown() {
        EventVerificationResult result = agent.verify(
                request(
                        assessment(0.90, 0),
                        "https://ipu.ac.in/admission"
                )
        );

        assertEquals(
                VerificationStatus.MANUAL_REVIEW,
                result.status()
        );
    }

    @Test
    void shouldRejectMalformedSource() {
        EventVerificationResult result = agent.verify(
                request(
                        assessment(0.90, 10_000),
                        "not a valid source"
                )
        );

        assertAll(
                () -> assertEquals(
                        VerificationStatus.REJECTED,
                        result.status()
                ),
                () -> assertEquals(
                        "unknown",
                        result.normalizedSourceDomain()
                )
        );
    }

    @Test
    void shouldRejectInvalidTrustedDomainConfiguration() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new EventVerificationAgent(Set.of())
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new EventVerificationAgent(
                                Set.of("not a valid domain")
                        )
                )
        );
    }

    private EventVerificationRequest request(
            EventImpactAssessment assessment,
            String sourceReference
    ) {
        return new EventVerificationRequest(
                assessment,
                sourceReference
        );
    }

    private EventImpactAssessment assessment(
            double confidence,
            long expectedUsers
    ) {
        return new EventImpactAssessment(
                "GGSIPU Admission Registration",
                EventType.ADMISSION,
                "ipu.ac.in",
                expectedUsers,
                8.5,
                1_440,
                confidence,
                "The announcement specifies timing, audience "
                        + "and affected online service."
        );
    }
}
package com.harsh.cloudsim.agent.verification;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;
import com.harsh.cloudsim.agent.verification.claim.model.ClaimVerdict;
import com.harsh.cloudsim.agent.verification.claim.model.ClaimVerificationResult;
import com.harsh.cloudsim.agent.verification.evidence.EvidenceFetchStatus;
import com.harsh.cloudsim.agent.verification.evidence.SourceEvidence;
import com.harsh.cloudsim.agent.verification.model.EventVerificationRequest;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventVerificationAgentTest {

    private static final String OFFICIAL_SOURCE =
            "https://ipu.ac.in/admission";

    private EventVerificationAgent agent;

    @BeforeEach
    void createAgent() {
        agent = new EventVerificationAgent(
                Set.of("ipu.ac.in")
        );
    }

    @Test
    void preliminaryCheckShouldNotVerifyWithoutEvidence() {
        EventVerificationResult result =
                agent.verify(
                        request(
                                assessment(0.90, 10_000),
                                OFFICIAL_SOURCE
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
                        result.reasons()
                                .stream()
                                .anyMatch(
                                        reason -> reason.contains(
                                                "cannot prove the event"
                                        )
                                )
                )
        );
    }

    @Test
    void shouldTrustRealSubdomainButStillRequireEvidence() {
        EventVerificationResult result =
                agent.verify(
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
        EventVerificationResult result =
                agent.verify(
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
        EventVerificationResult result =
                agent.verify(
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
        EventVerificationResult result =
                agent.verify(
                        request(
                                assessment(0.30, 10_000),
                                OFFICIAL_SOURCE
                        )
                );

        assertEquals(
                VerificationStatus.REJECTED,
                result.status()
        );
    }

    @Test
    void shouldRequireReviewWhenAudienceIsUnknown() {
        EventVerificationResult result =
                agent.verify(
                        request(
                                assessment(0.90, 0),
                                OFFICIAL_SOURCE
                        )
                );

        assertEquals(
                VerificationStatus.MANUAL_REVIEW,
                result.status()
        );
    }

    @Test
    void shouldRejectMalformedSource() {
        EventVerificationResult result =
                agent.verify(
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
                        () -> new EventVerificationAgent(
                                Set.of()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new EventVerificationAgent(
                                Set.of(
                                        "not a valid domain"
                                )
                        )
                )
        );
    }

    @Test
    void shouldRejectWhenEvidencePageWasNotFound() {
        EventVerificationResult result =
                agent.verify(
                        request(
                                assessment(0.90, 10_000),
                                OFFICIAL_SOURCE
                        ),
                        SourceEvidence.failure(
                                OFFICIAL_SOURCE,
                                OFFICIAL_SOURCE,
                                EvidenceFetchStatus.NOT_FOUND,
                                404,
                                "The requested page returned HTTP 404."
                        )
                );

        assertAll(
                () -> assertEquals(
                        VerificationStatus.REJECTED,
                        result.status()
                ),
                () -> assertTrue(
                        result.reasons()
                                .stream()
                                .anyMatch(
                                        reason -> reason.contains(
                                                "NOT_FOUND"
                                        )
                                )
                )
        );
    }

    @Test
    void shouldRequireClaimMatchingAfterEvidenceIsFetched() {
        EventVerificationResult result =
                agent.verify(
                        request(
                                assessment(0.90, 10_000),
                                OFFICIAL_SOURCE
                        ),
                        fetchedEvidence()
                );

        assertEquals(
                VerificationStatus.MANUAL_REVIEW,
                result.status()
        );
    }

    @Test
    void shouldVerifyFullySupportedClaim() {
        EventVerificationResult result =
                agent.verify(
                        request(
                                assessment(0.90, 10_000),
                                OFFICIAL_SOURCE
                        ),
                        fetchedEvidence(),
                        supportedClaim(0.92)
                );

        assertAll(
                () -> assertEquals(
                        VerificationStatus.VERIFIED,
                        result.status()
                ),
                () -> assertTrue(
                        result.verificationScore() >= 0.75
                ),
                () -> assertTrue(
                        result.reasons()
                                .stream()
                                .anyMatch(
                                        reason -> reason.contains(
                                                "Final verification "
                                                        + "status: VERIFIED"
                                        )
                                )
                )
        );
    }

    /**
     * Regression test for the live Gemini output.
     *
     * affectedService may be a human-readable name instead of a
     * domain. That must not prevent evidence-backed verification.
     */
    @Test
    void shouldAcceptHumanReadableServiceNameWhenClaimIsSupported() {
        EventImpactAssessment assessment =
                new EventImpactAssessment(
                        "GGSIPU Admission Registration",
                        EventType.ADMISSION,
                        "GGSIPU Admission Portal",
                        10_000,
                        8.5,
                        1_440,
                        0.90,
                        "The announcement specifies timing, audience "
                                + "and the affected online service."
                );

        EventVerificationResult result =
                agent.verify(
                        request(
                                assessment,
                                OFFICIAL_SOURCE
                        ),
                        fetchedEvidence(),
                        supportedClaim(0.92)
                );

        assertAll(
                () -> assertEquals(
                        VerificationStatus.VERIFIED,
                        result.status()
                ),
                () -> assertTrue(
                        result.verificationScore() >= 0.75
                )
        );
    }

    @Test
    void shouldRequireReviewForPartiallySupportedClaim() {
        EventVerificationResult result =
                agent.verify(
                        request(
                                assessment(0.90, 10_000),
                                OFFICIAL_SOURCE
                        ),
                        fetchedEvidence(),
                        partiallySupportedClaim()
                );

        assertAll(
                () -> assertEquals(
                        VerificationStatus.MANUAL_REVIEW,
                        result.status()
                ),
                () -> assertTrue(
                        result.verificationScore() < 0.75
                )
        );
    }

    @Test
    void shouldRejectUnsupportedClaim() {
        EventVerificationResult result =
                agent.verify(
                        request(
                                assessment(0.90, 10_000),
                                OFFICIAL_SOURCE
                        ),
                        fetchedEvidence(),
                        unsupportedClaim()
                );

        assertAll(
                () -> assertEquals(
                        VerificationStatus.REJECTED,
                        result.status()
                ),
                () -> assertTrue(
                        result.verificationScore() < 0.50
                )
        );
    }

    @Test
    void shouldRequireReviewWhenClaimConfidenceIsLow() {
        EventVerificationResult result =
                agent.verify(
                        request(
                                assessment(0.90, 10_000),
                                OFFICIAL_SOURCE
                        ),
                        fetchedEvidence(),
                        supportedClaim(0.65)
                );

        assertEquals(
                VerificationStatus.MANUAL_REVIEW,
                result.status()
        );
    }

    @Test
    void shouldNotAllowEvidenceToOverrideLowAssessmentConfidence() {
        EventVerificationResult result =
                agent.verify(
                        request(
                                assessment(0.30, 10_000),
                                OFFICIAL_SOURCE
                        ),
                        fetchedEvidence(),
                        supportedClaim(0.95)
                );

        assertEquals(
                VerificationStatus.REJECTED,
                result.status()
        );
    }

    @Test
    void shouldRejectEvidenceRedirectedToUntrustedDomain() {
        SourceEvidence redirectedEvidence =
                SourceEvidence.fetched(
                        OFFICIAL_SOURCE,
                        "https://attacker.example/fake-admission",
                        200,
                        "text/html",
                        "Fake Admission Notice",
                        "GGSIPU admission registration opens "
                                + "tomorrow at 10 AM."
                );

        EventVerificationResult result =
                agent.verify(
                        request(
                                assessment(0.90, 10_000),
                                OFFICIAL_SOURCE
                        ),
                        redirectedEvidence,
                        supportedClaim(0.95)
                );

        assertAll(
                () -> assertEquals(
                        VerificationStatus.REJECTED,
                        result.status()
                ),
                () -> assertTrue(
                        result.reasons()
                                .stream()
                                .anyMatch(
                                        reason -> reason.contains(
                                                "untrusted"
                                        )
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
                        + "and the affected online service."
        );
    }

    private SourceEvidence fetchedEvidence() {
        return SourceEvidence.fetched(
                OFFICIAL_SOURCE,
                OFFICIAL_SOURCE,
                200,
                "text/html",
                "GGSIPU Admission Registration",
                "GGSIPU online admission registration will open "
                        + "tomorrow at 10 AM for approximately "
                        + "10000 applicants."
        );
    }

    private ClaimVerificationResult supportedClaim(
            double confidence
    ) {
        return new ClaimVerificationResult(
                ClaimVerdict.SUPPORTED,
                confidence,
                List.of(
                        "Admission registration opens tomorrow.",
                        "The opening time is 10 AM.",
                        "The expected audience is 10000 applicants."
                ),
                List.of(),
                List.of(
                        "Admission registration will open tomorrow "
                                + "at 10 AM for approximately "
                                + "10000 applicants."
                ),
                "The source evidence explicitly supports the event "
                        + "type, timing and expected audience."
        );
    }

    private ClaimVerificationResult partiallySupportedClaim() {
        return new ClaimVerificationResult(
                ClaimVerdict.PARTIALLY_SUPPORTED,
                0.88,
                List.of(
                        "An online admission service exists."
                ),
                List.of(
                        "The page does not confirm tomorrow at 10 AM.",
                        "The page does not confirm 10000 applicants."
                ),
                List.of(
                        "Online admission portal."
                ),
                "The page mentions admission services but does not "
                        + "support the claimed timing or audience."
        );
    }

    private ClaimVerificationResult unsupportedClaim() {
        return new ClaimVerificationResult(
                ClaimVerdict.NOT_SUPPORTED,
                0.95,
                List.of(),
                List.of(
                        "The claimed registration announcement "
                                + "does not appear in the evidence.",
                        "The claimed opening time is unsupported.",
                        "The claimed audience size is unsupported."
                ),
                List.of(),
                "The retrieved webpage contains no evidence for the "
                        + "specific registration announcement."
        );
    }
}
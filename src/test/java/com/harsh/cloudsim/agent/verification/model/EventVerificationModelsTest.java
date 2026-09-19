package com.harsh.cloudsim.agent.verification.model;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventVerificationModelsTest {

    private final EventImpactAssessment assessment =
            new EventImpactAssessment(
                    "GGSIPU Admission Registration",
                    EventType.ADMISSION,
                    "ipu.ac.in",
                    10_000,
                    8.5,
                    1_440,
                    0.90,
                    "A large number of applicants is expected."
            );

    @Test
    void shouldCreateAndNormalizeVerificationRequest() {
        EventVerificationRequest request =
                new EventVerificationRequest(
                        assessment,
                        "  https://ipu.ac.in/admission  "
                );

        assertAll(
                () -> assertEquals(
                        assessment,
                        request.assessment()
                ),
                () -> assertEquals(
                        "https://ipu.ac.in/admission",
                        request.sourceReference()
                )
        );
    }

    @Test
    void shouldRejectNullAssessment() {
        assertThrows(
                NullPointerException.class,
                () -> new EventVerificationRequest(
                        null,
                        "ipu.ac.in"
                )
        );
    }

    @Test
    void shouldRejectBlankSourceReference() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EventVerificationRequest(
                        assessment,
                        "   "
                )
        );
    }

    @Test
    void shouldCreateValidVerificationResult() {
        EventVerificationResult result =
                new EventVerificationResult(
                        VerificationStatus.VERIFIED,
                        0.90,
                        "IPU.AC.IN",
                        List.of(
                                "Source domain is trusted.",
                                "Confidence is sufficient."
                        )
                );

        assertAll(
                () -> assertEquals(
                        VerificationStatus.VERIFIED,
                        result.status()
                ),
                () -> assertEquals(
                        0.90,
                        result.verificationScore()
                ),
                () -> assertEquals(
                        "ipu.ac.in",
                        result.normalizedSourceDomain()
                ),
                () -> assertEquals(
                        2,
                        result.reasons().size()
                )
        );
    }

    @Test
    void shouldCreateImmutableReasonsList() {
        ArrayList<String> mutableReasons =
                new ArrayList<>();

        mutableReasons.add(
                "Source domain is trusted."
        );

        EventVerificationResult result =
                new EventVerificationResult(
                        VerificationStatus.VERIFIED,
                        0.90,
                        "ipu.ac.in",
                        mutableReasons
                );

        mutableReasons.add(
                "This must not enter the result."
        );

        assertAll(
                () -> assertEquals(
                        1,
                        result.reasons().size()
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> result.reasons().add(
                                "Illegal modification"
                        )
                )
        );
    }

    @Test
    void shouldRejectInvalidVerificationScores() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult(-0.01)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult(1.01)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult(Double.NaN)
                )
        );
    }

    @Test
    void shouldRejectBlankNormalizedDomain() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EventVerificationResult(
                        VerificationStatus.MANUAL_REVIEW,
                        0.50,
                        "   ",
                        List.of("Source requires review.")
                )
        );
    }

    @Test
    void shouldRejectInvalidReasons() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new EventVerificationResult(
                                VerificationStatus.REJECTED,
                                0.20,
                                "unknown",
                                List.of()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new EventVerificationResult(
                                VerificationStatus.REJECTED,
                                0.20,
                                "unknown",
                                List.of("   ")
                        )
                ),
                () -> assertTrue(
                        assessment.confidence() > 0.0
                )
        );
    }

    private EventVerificationResult createResult(double score) {
        return new EventVerificationResult(
                VerificationStatus.MANUAL_REVIEW,
                score,
                "ipu.ac.in",
                List.of("Verification completed.")
        );
    }
}
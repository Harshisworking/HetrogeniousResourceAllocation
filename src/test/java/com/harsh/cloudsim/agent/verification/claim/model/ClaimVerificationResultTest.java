package com.harsh.cloudsim.agent.verification.claim.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimVerificationResultTest {

    @Test
    void shouldCreateSupportedResult() {
        ClaimVerificationResult result =
                new ClaimVerificationResult(
                        ClaimVerdict.SUPPORTED,
                        0.95,
                        List.of(
                                "Admission registration date is confirmed.",
                                "The affected service is confirmed."
                        ),
                        List.of(),
                        List.of(
                                "Online admission registration opens "
                                        + "on 20 September."
                        ),
                        "The official source supports all important claims."
                );

        assertAll(
                () -> assertEquals(
                        ClaimVerdict.SUPPORTED,
                        result.verdict()
                ),
                () -> assertEquals(
                        0.95,
                        result.confidence()
                ),
                () -> assertEquals(
                        2,
                        result.supportedFacts().size()
                )
        );
    }

    @Test
    void shouldCreatePartiallySupportedResult() {
        ClaimVerificationResult result =
                new ClaimVerificationResult(
                        ClaimVerdict.PARTIALLY_SUPPORTED,
                        0.70,
                        List.of(
                                "An admission announcement exists."
                        ),
                        List.of(
                                "The expected user count is not stated."
                        ),
                        List.of(
                                "Admission registration is now available."
                        ),
                        "The event exists, but important details are missing."
                );

        assertEquals(
                ClaimVerdict.PARTIALLY_SUPPORTED,
                result.verdict()
        );
    }

    @Test
    void shouldCreateNotSupportedResult() {
        ClaimVerificationResult result =
                new ClaimVerificationResult(
                        ClaimVerdict.NOT_SUPPORTED,
                        0.90,
                        List.of(),
                        List.of(
                                "No current admission-opening notice "
                                        + "was found."
                        ),
                        List.of(),
                        "The supplied webpage does not support the claim."
                );

        assertEquals(
                ClaimVerdict.NOT_SUPPORTED,
                result.verdict()
        );
    }

    @Test
    void shouldRejectInvalidConfidence() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> supportedResult(-0.01)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> supportedResult(1.01)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> supportedResult(Double.NaN)
                )
        );
    }

    @Test
    void shouldValidateSupportedVerdictConsistency() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ClaimVerificationResult(
                                ClaimVerdict.SUPPORTED,
                                0.90,
                                List.of(),
                                List.of(),
                                List.of("Evidence excerpt"),
                                "Reasoning"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ClaimVerificationResult(
                                ClaimVerdict.SUPPORTED,
                                0.90,
                                List.of("Supported fact"),
                                List.of("Unexpected missing fact"),
                                List.of("Evidence excerpt"),
                                "Reasoning"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ClaimVerificationResult(
                                ClaimVerdict.SUPPORTED,
                                0.90,
                                List.of("Supported fact"),
                                List.of(),
                                List.of(),
                                "Reasoning"
                        )
                )
        );
    }

    @Test
    void shouldValidatePartialVerdictConsistency() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ClaimVerificationResult(
                                ClaimVerdict.PARTIALLY_SUPPORTED,
                                0.70,
                                List.of(),
                                List.of("Missing fact"),
                                List.of("Evidence excerpt"),
                                "Reasoning"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ClaimVerificationResult(
                                ClaimVerdict.PARTIALLY_SUPPORTED,
                                0.70,
                                List.of("Supported fact"),
                                List.of(),
                                List.of("Evidence excerpt"),
                                "Reasoning"
                        )
                )
        );
    }

    @Test
    void shouldValidateNotSupportedVerdictConsistency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClaimVerificationResult(
                        ClaimVerdict.NOT_SUPPORTED,
                        0.90,
                        List.of(),
                        List.of(),
                        List.of(),
                        "Reasoning"
                )
        );
    }

    @Test
    void shouldDefensivelyCopyLists() {
        ArrayList<String> supportedFacts =
                new ArrayList<>(
                        List.of("Admission notice exists.")
                );

        ClaimVerificationResult result =
                new ClaimVerificationResult(
                        ClaimVerdict.SUPPORTED,
                        0.90,
                        supportedFacts,
                        List.of(),
                        List.of("Admission notice"),
                        "The claim is supported."
                );

        supportedFacts.add(
                "External modification"
        );

        assertAll(
                () -> assertEquals(
                        1,
                        result.supportedFacts().size()
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> result.supportedFacts().add(
                                "Illegal modification"
                        )
                )
        );
    }

    private ClaimVerificationResult supportedResult(
            double confidence
    ) {
        return new ClaimVerificationResult(
                ClaimVerdict.SUPPORTED,
                confidence,
                List.of("Supported fact"),
                List.of(),
                List.of("Evidence excerpt"),
                "The claim is supported."
        );
    }
}
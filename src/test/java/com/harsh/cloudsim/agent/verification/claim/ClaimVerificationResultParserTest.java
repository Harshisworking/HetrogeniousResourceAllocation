package com.harsh.cloudsim.agent.verification.claim;

import com.harsh.cloudsim.agent.verification.claim.model.ClaimVerdict;
import com.harsh.cloudsim.agent.verification.claim.model.ClaimVerificationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimVerificationResultParserTest {

    private static final String SUPPORTED_JSON = """
            {
              "verdict": "SUPPORTED",
              "confidence": 0.95,
              "supportedFacts": [
                "The admission registration event is announced.",
                "The affected service is ipu.ac.in."
              ],
              "missingOrContradictedFacts": [],
              "evidenceExcerpts": [
                "Online admission registration opens tomorrow."
              ],
              "reasoning": "Official evidence supports the complete claim."
            }
            """;

    private static final String PARTIAL_JSON = """
            {
              "verdict": "PARTIALLY_SUPPORTED",
              "confidence": 0.70,
              "supportedFacts": [
                "An admission announcement exists."
              ],
              "missingOrContradictedFacts": [
                "The expected user count is not stated."
              ],
              "evidenceExcerpts": [
                "Online admission registration is available."
              ],
              "reasoning": "The event exists but important details are missing."
            }
            """;

    private static final String NOT_SUPPORTED_JSON = """
            {
              "verdict": "NOT_SUPPORTED",
              "confidence": 0.90,
              "supportedFacts": [],
              "missingOrContradictedFacts": [
                "No current admission-opening notice was found."
              ],
              "evidenceExcerpts": [],
              "reasoning": "The source does not support the event claim."
            }
            """;

    @Test
    void shouldParseSupportedResult() {
        ClaimVerificationResult result =
                ClaimVerificationResultParser.parse(
                        SUPPORTED_JSON
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
                ),
                () -> assertEquals(
                        1,
                        result.evidenceExcerpts().size()
                )
        );
    }

    @Test
    void shouldAcceptMarkdownFencedJson() {
        String fencedResponse = """
                ```json
                %s
                ```
                """.formatted(SUPPORTED_JSON);

        ClaimVerificationResult result =
                ClaimVerificationResultParser.parse(
                        fencedResponse
                );

        assertEquals(
                ClaimVerdict.SUPPORTED,
                result.verdict()
        );
    }

    @Test
    void shouldParsePartiallySupportedResult() {
        ClaimVerificationResult result =
                ClaimVerificationResultParser.parse(
                        PARTIAL_JSON
                );

        assertAll(
                () -> assertEquals(
                        ClaimVerdict.PARTIALLY_SUPPORTED,
                        result.verdict()
                ),
                () -> assertEquals(
                        1,
                        result.supportedFacts().size()
                ),
                () -> assertEquals(
                        1,
                        result.missingOrContradictedFacts().size()
                )
        );
    }

    @Test
    void shouldParseNotSupportedResult() {
        ClaimVerificationResult result =
                ClaimVerificationResultParser.parse(
                        NOT_SUPPORTED_JSON
                );

        assertEquals(
                ClaimVerdict.NOT_SUPPORTED,
                result.verdict()
        );
    }

    @Test
    void shouldRejectBlankAndMalformedResponses() {
        assertAll(
                () -> assertThrows(
                        ClaimVerificationParsingException.class,
                        () -> ClaimVerificationResultParser.parse(
                                "   "
                        )
                ),
                () -> assertThrows(
                        ClaimVerificationParsingException.class,
                        () -> ClaimVerificationResultParser.parse(
                                "{\"verdict\":\"SUPPORTED\""
                        )
                )
        );
    }

    @Test
    void shouldRejectMissingField() {
        String missingReasoning = """
                {
                  "verdict": "NOT_SUPPORTED",
                  "confidence": 0.90,
                  "supportedFacts": [],
                  "missingOrContradictedFacts": [
                    "No supporting announcement was found."
                  ],
                  "evidenceExcerpts": []
                }
                """;

        assertThrows(
                ClaimVerificationParsingException.class,
                () -> ClaimVerificationResultParser.parse(
                        missingReasoning
                )
        );
    }

    @Test
    void shouldRejectUnknownField() {
        String unknownField = """
                {
                  "verdict": "NOT_SUPPORTED",
                  "confidence": 0.90,
                  "supportedFacts": [],
                  "missingOrContradictedFacts": [
                    "No supporting announcement was found."
                  ],
                  "evidenceExcerpts": [],
                  "reasoning": "The claim is unsupported.",
                  "hostId": 0
                }
                """;

        assertThrows(
                ClaimVerificationParsingException.class,
                () -> ClaimVerificationResultParser.parse(
                        unknownField
                )
        );
    }

    @Test
    void shouldRejectUnsupportedVerdict() {
        String invalidVerdict =
                SUPPORTED_JSON.replace(
                        "\"SUPPORTED\"",
                        "\"PROBABLY_TRUE\""
                );

        assertThrows(
                ClaimVerificationParsingException.class,
                () -> ClaimVerificationResultParser.parse(
                        invalidVerdict
                )
        );
    }

    @Test
    void shouldRejectInvalidConfidence() {
        String invalidConfidence =
                SUPPORTED_JSON.replace(
                        "0.95",
                        "1.20"
                );

        assertThrows(
                ClaimVerificationParsingException.class,
                () -> ClaimVerificationResultParser.parse(
                        invalidConfidence
                )
        );
    }

    @Test
    void shouldRejectNonStringArrayItem() {
        String invalidArray = """
                {
                  "verdict": "SUPPORTED",
                  "confidence": 0.95,
                  "supportedFacts": [42],
                  "missingOrContradictedFacts": [],
                  "evidenceExcerpts": [
                    "Online admission registration opens tomorrow."
                  ],
                  "reasoning": "The source supports the complete claim."
                }
                """;

        assertThrows(
                ClaimVerificationParsingException.class,
                () -> ClaimVerificationResultParser.parse(
                        invalidArray
                )
        );
    }
}
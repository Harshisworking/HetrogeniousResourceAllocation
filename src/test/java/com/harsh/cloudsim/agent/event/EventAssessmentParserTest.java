package com.harsh.cloudsim.agent.event;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventAssessmentParserTest {

    private static final String VALID_JSON = """
            {
              "eventName": "GGSIPU Admission Registration",
              "eventType": "ADMISSION",
              "affectedService": "ipu.ac.in",
              "expectedUsers": 10000,
              "trafficMultiplier": 15.0,
              "leadTimeMinutes": 1440,
              "confidence": 0.90,
              "reasoning": "A large applicant population is expected."
            }
            """;

    @Test
    void shouldParseValidAssessment() {
        EventImpactAssessment assessment =
                EventAssessmentParser.parse(VALID_JSON);

        assertAll(
                () -> assertEquals(
                        "GGSIPU Admission Registration",
                        assessment.eventName()
                ),
                () -> assertEquals(
                        EventType.ADMISSION,
                        assessment.eventType()
                ),
                () -> assertEquals(
                        "ipu.ac.in",
                        assessment.affectedService()
                ),
                () -> assertEquals(
                        10_000,
                        assessment.expectedUsers()
                ),
                () -> assertEquals(
                        15.0,
                        assessment.trafficMultiplier()
                ),
                () -> assertEquals(
                        1_440,
                        assessment.leadTimeMinutes()
                ),
                () -> assertEquals(
                        0.90,
                        assessment.confidence()
                )
        );
    }

    @Test
    void shouldAcceptJsonInsideMarkdownFence() {
        String fencedResponse = """
                ```json
                %s
                ```
                """.formatted(VALID_JSON);

        EventImpactAssessment assessment =
                EventAssessmentParser.parse(fencedResponse);

        assertEquals(
                EventType.ADMISSION,
                assessment.eventType()
        );
    }

    @Test
    void shouldRejectBlankResponse() {
        assertThrows(
                EventAssessmentParsingException.class,
                () -> EventAssessmentParser.parse("   ")
        );
    }

    @Test
    void shouldRejectMalformedJson() {
        String malformedJson = """
                {
                  "eventName": "Incomplete event"
                """;

        assertThrows(
                EventAssessmentParsingException.class,
                () -> EventAssessmentParser.parse(malformedJson)
        );
    }

    @Test
    void shouldRejectMissingField() {
        String missingConfidence = VALID_JSON.replace(
                "  \"confidence\": 0.90,\n",
                ""
        );

        assertThrows(
                EventAssessmentParsingException.class,
                () -> EventAssessmentParser.parse(missingConfidence)
        );
    }

    @Test
    void shouldRejectUnknownField() {
        String unknownField = VALID_JSON.replace(
                "\"reasoning\": \"A large applicant population is expected.\"",
                """
                "reasoning": "A large applicant population is expected.",
                  "hostId": 0
                """
        );

        assertThrows(
                EventAssessmentParsingException.class,
                () -> EventAssessmentParser.parse(unknownField)
        );
    }

    @Test
    void shouldRejectUnsupportedEventType() {
        String invalidEventType = VALID_JSON.replace(
                "\"ADMISSION\"",
                "\"CELEBRATION\""
        );

        assertThrows(
                EventAssessmentParsingException.class,
                () -> EventAssessmentParser.parse(invalidEventType)
        );
    }

    @Test
    void shouldRejectDecimalIntegerField() {
        String decimalExpectedUsers = VALID_JSON.replace(
                "\"expectedUsers\": 10000",
                "\"expectedUsers\": 10000.5"
        );

        assertThrows(
                EventAssessmentParsingException.class,
                () -> EventAssessmentParser.parse(decimalExpectedUsers)
        );
    }

    @Test
    void shouldRejectDomainValueOutsideAllowedRange() {
        String invalidMultiplier = VALID_JSON.replace(
                "\"trafficMultiplier\": 15.0",
                "\"trafficMultiplier\": 0.5"
        );

        assertThrows(
                EventAssessmentParsingException.class,
                () -> EventAssessmentParser.parse(invalidMultiplier)
        );
    }
}
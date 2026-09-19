package com.harsh.cloudsim.agent.event.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventImpactAssessmentTest {

    @Test
    void shouldCreateValidAssessmentAndTrimText() {
        EventImpactAssessment assessment = createAssessment(
                "  GGSIPU Admission Registration  ",
                EventType.ADMISSION,
                "  ipu.ac.in  ",
                10_000,
                15.0,
                1_440,
                0.90,
                "  Registration may create a concentrated traffic surge.  "
        );

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
                ),
                () -> assertEquals(
                        "Registration may create a concentrated traffic surge.",
                        assessment.reasoning()
                )
        );
    }

    @Test
    void shouldRejectBlankTextFields() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                " ",
                                EventType.ADMISSION,
                                "ipu.ac.in",
                                10_000,
                                15.0,
                                1_440,
                                0.90,
                                "Valid reasoning"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                "Admission",
                                EventType.ADMISSION,
                                " ",
                                10_000,
                                15.0,
                                1_440,
                                0.90,
                                "Valid reasoning"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                "Admission",
                                EventType.ADMISSION,
                                "ipu.ac.in",
                                10_000,
                                15.0,
                                1_440,
                                0.90,
                                " "
                        )
                )
        );
    }

    @Test
    void shouldRejectMissingEventType() {
        assertThrows(
                NullPointerException.class,
                () -> createAssessment(
                        "Admission",
                        null,
                        "ipu.ac.in",
                        10_000,
                        15.0,
                        1_440,
                        0.90,
                        "Valid reasoning"
                )
        );
    }

    @Test
    void shouldRejectExpectedUsersOutsideAllowedRange() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                "Admission",
                                EventType.ADMISSION,
                                "ipu.ac.in",
                                -1,
                                15.0,
                                1_440,
                                0.90,
                                "Valid reasoning"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                "Admission",
                                EventType.ADMISSION,
                                "ipu.ac.in",
                                EventImpactAssessment
                                        .MAX_EXPECTED_USERS + 1,
                                15.0,
                                1_440,
                                0.90,
                                "Valid reasoning"
                        )
                )
        );
    }

    @Test
    void shouldRejectInvalidTrafficMultiplier() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                "Admission",
                                EventType.ADMISSION,
                                "ipu.ac.in",
                                10_000,
                                0.99,
                                1_440,
                                0.90,
                                "Valid reasoning"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                "Admission",
                                EventType.ADMISSION,
                                "ipu.ac.in",
                                10_000,
                                EventImpactAssessment
                                        .MAX_TRAFFIC_MULTIPLIER + 0.01,
                                1_440,
                                0.90,
                                "Valid reasoning"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                "Admission",
                                EventType.ADMISSION,
                                "ipu.ac.in",
                                10_000,
                                Double.NaN,
                                1_440,
                                0.90,
                                "Valid reasoning"
                        )
                )
        );
    }

    @Test
    void shouldRejectInvalidLeadTime() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                "Admission",
                                EventType.ADMISSION,
                                "ipu.ac.in",
                                10_000,
                                15.0,
                                -1,
                                0.90,
                                "Valid reasoning"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                "Admission",
                                EventType.ADMISSION,
                                "ipu.ac.in",
                                10_000,
                                15.0,
                                EventImpactAssessment
                                        .MAX_LEAD_TIME_MINUTES + 1,
                                0.90,
                                "Valid reasoning"
                        )
                )
        );
    }

    @Test
    void shouldRejectInvalidConfidence() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                "Admission",
                                EventType.ADMISSION,
                                "ipu.ac.in",
                                10_000,
                                15.0,
                                1_440,
                                -0.01,
                                "Valid reasoning"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                "Admission",
                                EventType.ADMISSION,
                                "ipu.ac.in",
                                10_000,
                                15.0,
                                1_440,
                                1.01,
                                "Valid reasoning"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createAssessment(
                                "Admission",
                                EventType.ADMISSION,
                                "ipu.ac.in",
                                10_000,
                                15.0,
                                1_440,
                                Double.NaN,
                                "Valid reasoning"
                        )
                )
        );
    }

    private EventImpactAssessment createAssessment(
            String eventName,
            EventType eventType,
            String affectedService,
            long expectedUsers,
            double trafficMultiplier,
            long leadTimeMinutes,
            double confidence,
            String reasoning
    ) {
        return new EventImpactAssessment(
                eventName,
                eventType,
                affectedService,
                expectedUsers,
                trafficMultiplier,
                leadTimeMinutes,
                confidence,
                reasoning
        );
    }
}
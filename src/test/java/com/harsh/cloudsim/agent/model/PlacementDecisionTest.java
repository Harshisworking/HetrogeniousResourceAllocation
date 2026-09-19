package com.harsh.cloudsim.agent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlacementDecisionTest {

    @Test
    void shouldCreateValidStructuredDecision() {
        PlacementDecision decision = new PlacementDecision(
                2,
                0,
                0.91,
                "  Laptop has sufficient CPU and memory capacity.  ",
                DecisionSource.EXTERNAL_AGENT
        );

        assertAll(
                () -> assertEquals(2, decision.vmId()),
                () -> assertEquals(0, decision.targetHostId()),
                () -> assertEquals(0.91, decision.confidence()),
                () -> assertEquals(
                        "Laptop has sufficient CPU and memory capacity.",
                        decision.reason()
                ),
                () -> assertEquals(
                        DecisionSource.EXTERNAL_AGENT,
                        decision.source()
                )
        );
    }

    @Test
    void shouldRejectNegativeVmId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlacementDecision(
                        -1,
                        0,
                        0.90,
                        "Invalid VM ID test.",
                        DecisionSource.EXTERNAL_AGENT
                )
        );
    }

    @Test
    void shouldRejectNegativeHostId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlacementDecision(
                        1,
                        -1,
                        0.90,
                        "Invalid host ID test.",
                        DecisionSource.EXTERNAL_AGENT
                )
        );
    }

    @Test
    void shouldRejectConfidenceBelowZero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlacementDecision(
                        1,
                        0,
                        -0.01,
                        "Invalid confidence test.",
                        DecisionSource.EXTERNAL_AGENT
                )
        );
    }

    @Test
    void shouldRejectConfidenceAboveOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlacementDecision(
                        1,
                        0,
                        1.01,
                        "Invalid confidence test.",
                        DecisionSource.EXTERNAL_AGENT
                )
        );
    }

    @Test
    void shouldRejectNonFiniteConfidence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlacementDecision(
                        1,
                        0,
                        Double.NaN,
                        "Invalid confidence test.",
                        DecisionSource.EXTERNAL_AGENT
                )
        );
    }

    @Test
    void shouldRejectBlankReason() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlacementDecision(
                        1,
                        0,
                        0.90,
                        "   ",
                        DecisionSource.EXTERNAL_AGENT
                )
        );
    }

    @Test
    void shouldRejectMissingDecisionSource() {
        assertThrows(
                NullPointerException.class,
                () -> new PlacementDecision(
                        1,
                        0,
                        0.90,
                        "Missing source test.",
                        null
                )
        );
    }
}
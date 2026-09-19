package com.harsh.cloudsim.agent.event.model;

import java.util.Objects;

/**
 * Validated assessment of how a real-world event may affect
 * an online service's workload.
 *
 * This record forms the trust boundary between the LLM and the
 * cloud-management system.
 *
 * @param eventName         short descriptive name of the event
 * @param eventType         classified event category
 * @param affectedService   affected website or online service
 * @param expectedUsers     estimated number of users
 * @param trafficMultiplier expected traffic relative to normal traffic
 * @param leadTimeMinutes   time remaining before the event
 * @param confidence        confidence score between 0.0 and 1.0
 * @param reasoning         explanation for the estimate
 */
public record EventImpactAssessment(
        String eventName,
        EventType eventType,
        String affectedService,
        long expectedUsers,
        double trafficMultiplier,
        long leadTimeMinutes,
        double confidence,
        String reasoning
) {

    /**
     * Domain limits prevent extreme or malformed LLM output from
     * entering the prediction and simulation layers.
     */
    public static final long MAX_EXPECTED_USERS =
            100_000_000L;

    public static final double MAX_TRAFFIC_MULTIPLIER =
            100.0;

    public static final long MAX_LEAD_TIME_MINUTES =
            525_600L;

    /**
     * Compact constructor that validates and normalises every field.
     */
    public EventImpactAssessment {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException(
                    "Event name must not be blank."
            );
        }

        Objects.requireNonNull(
                eventType,
                "Event type must not be null."
        );

        if (affectedService == null
                || affectedService.isBlank()) {
            throw new IllegalArgumentException(
                    "Affected service must not be blank."
            );
        }

        if (expectedUsers < 0
                || expectedUsers > MAX_EXPECTED_USERS) {
            throw new IllegalArgumentException(
                    "Expected users must be between 0 and "
                            + MAX_EXPECTED_USERS
                            + "."
            );
        }

        if (!Double.isFinite(trafficMultiplier)
                || trafficMultiplier < 1.0
                || trafficMultiplier
                > MAX_TRAFFIC_MULTIPLIER) {
            throw new IllegalArgumentException(
                    "Traffic multiplier must be a finite number "
                            + "between 1.0 and "
                            + MAX_TRAFFIC_MULTIPLIER
                            + "."
            );
        }

        if (leadTimeMinutes < 0
                || leadTimeMinutes > MAX_LEAD_TIME_MINUTES) {
            throw new IllegalArgumentException(
                    "Lead time must be between 0 and "
                            + MAX_LEAD_TIME_MINUTES
                            + " minutes."
            );
        }

        if (!Double.isFinite(confidence)
                || confidence < 0.0
                || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "Confidence must be a finite number "
                            + "between 0.0 and 1.0."
            );
        }

        if (reasoning == null || reasoning.isBlank()) {
            throw new IllegalArgumentException(
                    "Reasoning must not be blank."
            );
        }

        /*
         * Remove unwanted leading and trailing whitespace before
         * assigning the values to the immutable record fields.
         */
        eventName = eventName.trim();
        affectedService = affectedService.trim();
        reasoning = reasoning.trim();
    }
}
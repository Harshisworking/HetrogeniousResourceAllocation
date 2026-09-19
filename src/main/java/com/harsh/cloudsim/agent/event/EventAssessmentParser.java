package com.harsh.cloudsim.agent.event;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Converts the JSON returned by the Gemini Event Intelligence Agent
 * into a validated EventImpactAssessment.
 *
 * The parser deliberately validates every expected field instead of
 * directly trusting automatic JSON-to-object conversion.
 */
public final class EventAssessmentParser {

    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "eventName",
            "eventType",
            "affectedService",
            "expectedUsers",
            "trafficMultiplier",
            "leadTimeMinutes",
            "confidence",
            "reasoning"
    );

    private static final Pattern INTEGER_PATTERN =
            Pattern.compile("-?(0|[1-9][0-9]*)");

    private EventAssessmentParser() {
        // Utility class; instances are unnecessary.
    }

    /**
     * Parses one Gemini response.
     *
     * @param response raw response returned by the LLM
     * @return validated event-impact assessment
     * @throws EventAssessmentParsingException when the response is invalid
     */
    public static EventImpactAssessment parse(String response) {
        if (response == null || response.isBlank()) {
            throw new EventAssessmentParsingException(
                    "Agent response must not be blank."
            );
        }

        String normalizedResponse = removeMarkdownFence(response);

        try {
            JsonElement root = JsonParser.parseString(normalizedResponse);

            if (!root.isJsonObject()) {
                throw new EventAssessmentParsingException(
                        "Agent response must contain one JSON object."
                );
            }

            JsonObject jsonObject = root.getAsJsonObject();
            validateFields(jsonObject);

            String eventName = requireString(
                    jsonObject,
                    "eventName"
            );

            EventType eventType = parseEventType(
                    requireString(jsonObject, "eventType")
            );

            String affectedService = requireString(
                    jsonObject,
                    "affectedService"
            );

            long expectedUsers = requireInteger(
                    jsonObject,
                    "expectedUsers"
            );

            double trafficMultiplier = requireNumber(
                    jsonObject,
                    "trafficMultiplier"
            );

            long leadTimeMinutes = requireInteger(
                    jsonObject,
                    "leadTimeMinutes"
            );

            double confidence = requireNumber(
                    jsonObject,
                    "confidence"
            );

            String reasoning = requireString(
                    jsonObject,
                    "reasoning"
            );

            /*
             * The record constructor performs the final domain validation:
             * ranges, blank values, confidence limits and safe upper bounds.
             */
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
        } catch (EventAssessmentParsingException exception) {
            throw exception;
        } catch (JsonParseException exception) {
            throw new EventAssessmentParsingException(
                    "Agent response is not valid JSON.",
                    exception
            );
        } catch (RuntimeException exception) {
            throw new EventAssessmentParsingException(
                    "Agent response contains invalid event data: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private static String removeMarkdownFence(String response) {
        String normalized = response.strip();

        if (!normalized.startsWith("```")) {
            return normalized;
        }

        int firstLineBreak = normalized.indexOf('\n');

        if (firstLineBreak < 0 || !normalized.endsWith("```")) {
            throw new EventAssessmentParsingException(
                    "Agent response contains an incomplete Markdown fence."
            );
        }

        return normalized.substring(
                firstLineBreak + 1,
                normalized.length() - 3
        ).strip();
    }

    private static void validateFields(JsonObject jsonObject) {
        Set<String> actualFields = jsonObject.keySet();

        TreeSet<String> missingFields =
                new TreeSet<>(EXPECTED_FIELDS);
        missingFields.removeAll(actualFields);

        TreeSet<String> unknownFields =
                new TreeSet<>(actualFields);
        unknownFields.removeAll(EXPECTED_FIELDS);

        if (!missingFields.isEmpty()) {
            throw new EventAssessmentParsingException(
                    "Missing required fields: " + missingFields
            );
        }

        if (!unknownFields.isEmpty()) {
            throw new EventAssessmentParsingException(
                    "Unknown fields are not permitted: " + unknownFields
            );
        }
    }

    private static String requireString(
            JsonObject jsonObject,
            String fieldName
    ) {
        JsonElement element = jsonObject.get(fieldName);

        if (!element.isJsonPrimitive()) {
            throw invalidField(fieldName, "must be a string");
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();

        if (!primitive.isString()) {
            throw invalidField(fieldName, "must be a string");
        }

        String value = primitive.getAsString();

        if (value.isBlank()) {
            throw invalidField(fieldName, "must not be blank");
        }

        return value;
    }

    private static long requireInteger(
            JsonObject jsonObject,
            String fieldName
    ) {
        JsonElement element = jsonObject.get(fieldName);

        if (!element.isJsonPrimitive()) {
            throw invalidField(fieldName, "must be an integer");
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();

        if (!primitive.isNumber()) {
            throw invalidField(fieldName, "must be an integer");
        }

        String rawValue = primitive.getAsString();

        if (!INTEGER_PATTERN.matcher(rawValue).matches()) {
            throw invalidField(
                    fieldName,
                    "must be an integer without a decimal component"
            );
        }

        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException exception) {
            throw new EventAssessmentParsingException(
                    "Field '" + fieldName
                            + "' is outside the supported integer range.",
                    exception
            );
        }
    }

    private static double requireNumber(
            JsonObject jsonObject,
            String fieldName
    ) {
        JsonElement element = jsonObject.get(fieldName);

        if (!element.isJsonPrimitive()) {
            throw invalidField(fieldName, "must be a number");
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();

        if (!primitive.isNumber()) {
            throw invalidField(fieldName, "must be a number");
        }

        try {
            double value = primitive.getAsDouble();

            if (!Double.isFinite(value)) {
                throw invalidField(
                        fieldName,
                        "must be a finite number"
                );
            }

            return value;
        } catch (NumberFormatException exception) {
            throw new EventAssessmentParsingException(
                    "Field '" + fieldName + "' is not a valid number.",
                    exception
            );
        }
    }

    private static EventType parseEventType(String rawEventType) {
        try {
            return EventType.valueOf(
                    rawEventType.strip().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new EventAssessmentParsingException(
                    "Unsupported eventType: " + rawEventType,
                    exception
            );
        }
    }

    private static EventAssessmentParsingException invalidField(
            String fieldName,
            String requirement
    ) {
        return new EventAssessmentParsingException(
                "Field '" + fieldName + "' " + requirement + "."
        );
    }
}
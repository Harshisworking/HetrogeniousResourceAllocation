package com.harsh.cloudsim.agent.verification.claim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.harsh.cloudsim.agent.verification.claim.model.ClaimVerdict;
import com.harsh.cloudsim.agent.verification.claim.model.ClaimVerificationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Converts the claim-matching LLM's JSON output into a validated
 * ClaimVerificationResult.
 */
public final class ClaimVerificationResultParser {

    private static final Set<String> EXPECTED_FIELDS =
            Set.of(
                    "verdict",
                    "confidence",
                    "supportedFacts",
                    "missingOrContradictedFacts",
                    "evidenceExcerpts",
                    "reasoning"
            );

    private ClaimVerificationResultParser() {
        // Utility class; instances are unnecessary.
    }

    public static ClaimVerificationResult parse(
            String response
    ) {
        if (response == null || response.isBlank()) {
            throw new ClaimVerificationParsingException(
                    "Claim-verification response must not be blank."
            );
        }

        String normalizedResponse =
                removeMarkdownFence(response);

        try {
            JsonElement root =
                    JsonParser.parseString(
                            normalizedResponse
                    );

            if (!root.isJsonObject()) {
                throw new ClaimVerificationParsingException(
                        "Claim-verification response must contain "
                                + "one JSON object."
                );
            }

            JsonObject jsonObject =
                    root.getAsJsonObject();

            validateFields(jsonObject);

            ClaimVerdict verdict =
                    parseVerdict(
                            requireString(
                                    jsonObject,
                                    "verdict"
                            )
                    );

            double confidence =
                    requireNumber(
                            jsonObject,
                            "confidence"
                    );

            List<String> supportedFacts =
                    requireStringList(
                            jsonObject,
                            "supportedFacts"
                    );

            List<String> missingOrContradictedFacts =
                    requireStringList(
                            jsonObject,
                            "missingOrContradictedFacts"
                    );

            List<String> evidenceExcerpts =
                    requireStringList(
                            jsonObject,
                            "evidenceExcerpts"
                    );

            String reasoning =
                    requireString(
                            jsonObject,
                            "reasoning"
                    );

            return new ClaimVerificationResult(
                    verdict,
                    confidence,
                    supportedFacts,
                    missingOrContradictedFacts,
                    evidenceExcerpts,
                    reasoning
            );
        } catch (ClaimVerificationParsingException exception) {
            throw exception;
        } catch (JsonParseException exception) {
            throw new ClaimVerificationParsingException(
                    "Claim-verification response is not valid JSON.",
                    exception
            );
        } catch (RuntimeException exception) {
            throw new ClaimVerificationParsingException(
                    "Claim-verification response contains invalid data: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private static String removeMarkdownFence(
            String response
    ) {
        String normalized = response.strip();

        if (!normalized.startsWith("```")) {
            return normalized;
        }

        int firstLineBreak =
                normalized.indexOf('\n');

        if (firstLineBreak < 0
                || !normalized.endsWith("```")) {
            throw new ClaimVerificationParsingException(
                    "Claim-verification response contains "
                            + "an incomplete Markdown fence."
            );
        }

        return normalized.substring(
                firstLineBreak + 1,
                normalized.length() - 3
        ).strip();
    }

    private static void validateFields(
            JsonObject jsonObject
    ) {
        Set<String> actualFields =
                jsonObject.keySet();

        TreeSet<String> missingFields =
                new TreeSet<>(EXPECTED_FIELDS);

        missingFields.removeAll(actualFields);

        TreeSet<String> unknownFields =
                new TreeSet<>(actualFields);

        unknownFields.removeAll(EXPECTED_FIELDS);

        if (!missingFields.isEmpty()) {
            throw new ClaimVerificationParsingException(
                    "Missing required claim-verification fields: "
                            + missingFields
            );
        }

        if (!unknownFields.isEmpty()) {
            throw new ClaimVerificationParsingException(
                    "Unknown claim-verification fields are not "
                            + "permitted: "
                            + unknownFields
            );
        }
    }

    private static String requireString(
            JsonObject jsonObject,
            String fieldName
    ) {
        JsonElement element =
                jsonObject.get(fieldName);

        if (!element.isJsonPrimitive()) {
            throw invalidField(
                    fieldName,
                    "must be a string"
            );
        }

        JsonPrimitive primitive =
                element.getAsJsonPrimitive();

        if (!primitive.isString()) {
            throw invalidField(
                    fieldName,
                    "must be a string"
            );
        }

        String value = primitive.getAsString();

        if (value.isBlank()) {
            throw invalidField(
                    fieldName,
                    "must not be blank"
            );
        }

        return value.trim();
    }

    private static double requireNumber(
            JsonObject jsonObject,
            String fieldName
    ) {
        JsonElement element =
                jsonObject.get(fieldName);

        if (!element.isJsonPrimitive()) {
            throw invalidField(
                    fieldName,
                    "must be a number"
            );
        }

        JsonPrimitive primitive =
                element.getAsJsonPrimitive();

        if (!primitive.isNumber()) {
            throw invalidField(
                    fieldName,
                    "must be a number"
            );
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
            throw new ClaimVerificationParsingException(
                    "Field '" + fieldName
                            + "' is not a valid number.",
                    exception
            );
        }
    }

    private static List<String> requireStringList(
            JsonObject jsonObject,
            String fieldName
    ) {
        JsonElement element =
                jsonObject.get(fieldName);

        if (!element.isJsonArray()) {
            throw invalidField(
                    fieldName,
                    "must be an array"
            );
        }

        ArrayList<String> values =
                new ArrayList<>();

        for (JsonElement item
                : element.getAsJsonArray()) {

            if (!item.isJsonPrimitive()) {
                throw invalidField(
                        fieldName,
                        "must contain only strings"
                );
            }

            JsonPrimitive primitive =
                    item.getAsJsonPrimitive();

            if (!primitive.isString()) {
                throw invalidField(
                        fieldName,
                        "must contain only strings"
                );
            }

            String value = primitive.getAsString();

            if (value.isBlank()) {
                throw invalidField(
                        fieldName,
                        "must not contain blank strings"
                );
            }

            values.add(value.trim());
        }

        return List.copyOf(values);
    }

    private static ClaimVerdict parseVerdict(
            String rawVerdict
    ) {
        try {
            return ClaimVerdict.valueOf(
                    rawVerdict
                            .trim()
                            .toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new ClaimVerificationParsingException(
                    "Unsupported claim verdict: "
                            + rawVerdict,
                    exception
            );
        }
    }

    private static ClaimVerificationParsingException
    invalidField(
            String fieldName,
            String requirement
    ) {
        return new ClaimVerificationParsingException(
                "Field '" + fieldName + "' "
                        + requirement + "."
        );
    }
}
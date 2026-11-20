package com.harsh.cloudsim.integration;

import com.harsh.cloudsim.agent.verification.evidence.SourceEvidence;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Conservative, deterministic gate for a *proactive* CloudSim demonstration.
 *
 * The caller must quote a service-opening sentence and the EXACT ISO-8601
 * offset timestamp that appears in that sentence on the fetched source page.
 * Neither the LLM-provided lead time nor the notice publication date suffices.
 * This is a strict textual guard, NOT automatic semantic verification of a
 * notice; ambiguous formats must be manually reviewed and cannot auto-qualify.
 */
public final class EvidenceBackedTimingGate {
    private static final Duration MAX_HORIZON = Duration.ofDays(30);
    private static final Pattern SERVICE_START = Pattern.compile(
            "(?i)\\b(?:portal|service|registration|application|result(?:s)?|admit[ -]?cards?)\\b"
            + ".{0,100}\\b(?:opens?|opening|available|goes[ ]live|released|published|starts?|begins?)\\b"
    );

    public enum Status {
        ELIGIBLE_FUTURE, MISSING_EVIDENCE, UNVERIFIED_EVENT,
        UNSUPPORTED_TIMESTAMP, TIMESTAMP_NOT_IN_EXCERPT,
        EXCERPT_NOT_ON_PAGE, NOT_A_SERVICE_OPENING,
        ALREADY_STARTED_OR_EXPIRED, TOO_FAR_IN_FUTURE
    }

    public record Decision(Status status, Instant serviceStart, String explanation) {
        public boolean eligible() { return status == Status.ELIGIBLE_FUTURE; }
    }

    private EvidenceBackedTimingGate() {}

    public static Decision evaluate(
            EventVerificationResult finalVerification,
            SourceEvidence sourceEvidence,
            String quotedServiceStartExcerpt,
            String literalTimestamp,
            Clock clock) {
        Objects.requireNonNull(clock, "Clock required");
        if (finalVerification == null ||
                finalVerification.status() != VerificationStatus.VERIFIED) {
            return denied(Status.UNVERIFIED_EVENT, "Final notice verification must be VERIFIED.");
        }
        if (sourceEvidence == null || !sourceEvidence.wasFetched()) {
            return denied(Status.MISSING_EVIDENCE, "Successfully fetched source text is required.");
        }
        if (quotedServiceStartExcerpt == null || quotedServiceStartExcerpt.isBlank() ||
                literalTimestamp == null || literalTimestamp.isBlank()) {
            return denied(Status.MISSING_EVIDENCE, "Service-opening excerpt and timestamp are required.");
        }
        String excerpt = quotedServiceStartExcerpt.trim().replaceAll("\\s+", " ");
        String page = sourceEvidence.visibleText().replaceAll("\\s+", " ");
        if (excerpt.length() < 25 || !page.contains(excerpt)) {
            return denied(Status.EXCERPT_NOT_ON_PAGE, "The exact quoted excerpt is not in fetched visible text.");
        }
        String rawTimestamp = literalTimestamp.trim();
        if (!excerpt.contains(rawTimestamp)) {
            return denied(Status.TIMESTAMP_NOT_IN_EXCERPT,
                    "Timestamp must appear verbatim in the source excerpt, not just in user input.");
        }
        Instant start;
        try {
            // Require explicit timezone offset. Local dates and offset-free times are unsafe.
            start = OffsetDateTime.parse(rawTimestamp).toInstant();
        } catch (DateTimeParseException exception) {
            return denied(Status.UNSUPPORTED_TIMESTAMP,
                    "Require a verbatim ISO-8601 timestamp with offset (e.g. 2026-12-01T10:00:00+05:30).");
        }
        if (!SERVICE_START.matcher(excerpt.toLowerCase(Locale.ROOT)).find()) {
            return denied(Status.NOT_A_SERVICE_OPENING,
                    "Excerpt must explicitly link a service to its opening/availability time; exam dates alone do not qualify.");
        }
        Instant now = clock.instant();
        if (!start.isAfter(now)) {
            return new Decision(Status.ALREADY_STARTED_OR_EXPIRED, start,
                    "Service opening is now or in the past; not proactive.");
        }
        if (Duration.between(now, start).compareTo(MAX_HORIZON) > 0) {
            return new Decision(Status.TOO_FAR_IN_FUTURE, start,
                    "Timestamp is more than 30 days ahead; require a fresh assessment.");
        }
        return new Decision(Status.ELIGIBLE_FUTURE, start,
                "Future service-opening timestamp appears verbatim in fetched source text. Traffic demand remains uncalibrated.");
    }

    private static Decision denied(Status status, String message) {
        return new Decision(status, null, message);
    }
}

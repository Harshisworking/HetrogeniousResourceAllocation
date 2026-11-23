package com.harsh.cloudsim.integration;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;
import com.harsh.cloudsim.agent.verification.evidence.EvidenceFetchStatus;
import com.harsh.cloudsim.agent.verification.evidence.SourceEvidence;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceBackedTimingGateTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"), ZoneOffset.UTC);
    private static final String DATE = "2026-11-25T10:00:00+05:30";
    private static final String EXCERPT = "Student registration portal opens at " + DATE + " for applications.";

    private static SourceEvidence page(String text) {
        return SourceEvidence.fetched("https://www.ipu.ac.in/notices.php",
                "https://www.ipu.ac.in/notices.php", 200, "text/html",
                "Official notices", text);
    }

    private static EventVerificationResult verified() {
        return new EventVerificationResult(VerificationStatus.VERIFIED, 0.95, "ipu.ac.in",
                List.of("Controlled verification fixture"));
    }

    private static EventImpactAssessment assessment() {
        return new EventImpactAssessment("Registration opening", EventType.DEADLINE,
                "registration portal", 400L, 2.0, 0, 0.9,
                "Controlled demand estimate, not real measured usage");
    }

    private static EvidenceBackedTimingGate.Decision check(String excerpt, String time,
                                                               String pageText, Clock clock) {
        return EvidenceBackedTimingGate.evaluate(verified(), page(pageText), excerpt, time, clock);
    }

    @Test void futureSourceBackedOpeningIsEligible() {
        var decision = check(EXCERPT, DATE, "Notice: " + EXCERPT + " Please prepare.", CLOCK);
        assertEquals(EvidenceBackedTimingGate.Status.ELIGIBLE_FUTURE, decision.status());
        assertEquals(Instant.parse("2026-11-25T04:30:00Z"), decision.serviceStart());
    }

    @Test void missingExcerptFailsClosed() {
        assertEquals(EvidenceBackedTimingGate.Status.MISSING_EVIDENCE,
                check("", DATE, EXCERPT, CLOCK).status());
    }

    @Test void timestampNotInQuotedExcerptFailsClosed() {
        assertEquals(EvidenceBackedTimingGate.Status.TIMESTAMP_NOT_IN_EXCERPT,
                check("Student registration portal opens tomorrow for applications.", DATE,
                        "Student registration portal opens tomorrow for applications.", CLOCK).status());
    }

    @Test void excerptMustReallyAppearOnFetchedPage() {
        assertEquals(EvidenceBackedTimingGate.Status.EXCERPT_NOT_ON_PAGE,
                check(EXCERPT, DATE, "The university published an unrelated notice.", CLOCK).status());
    }

    @Test void examinationDateOrPublicationDateDoesNotProvePortalOpening() {
        String exam = "End semester examination scheduled at " + DATE + " for all students.";
        assertEquals(EvidenceBackedTimingGate.Status.NOT_A_SERVICE_OPENING,
                check(exam, DATE, exam, CLOCK).status());
    }

    @Test void localTimeWithoutOffsetIsRejected() {
        String excerpt = "Student registration portal opens at 2026-11-25T10:00:00 for applications.";
        assertEquals(EvidenceBackedTimingGate.Status.UNSUPPORTED_TIMESTAMP,
                check(excerpt, "2026-11-25T10:00:00", excerpt, CLOCK).status());
    }

    @Test void alreadyStartedCannotBeProactive() {
        Clock later = Clock.fixed(Instant.parse("2026-11-25T05:00:00Z"), ZoneOffset.UTC);
        assertEquals(EvidenceBackedTimingGate.Status.ALREADY_STARTED_OR_EXPIRED,
                check(EXCERPT, DATE, EXCERPT, later).status());
    }

    @Test void tooFarAheadIsNotAutomaticallyEligible() {
        Clock early = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
        assertEquals(EvidenceBackedTimingGate.Status.TOO_FAR_IN_FUTURE,
                check(EXCERPT, DATE, EXCERPT, early).status());
    }

    @Test void finalManualReviewCannotPass() {
        var manual = new EventVerificationResult(VerificationStatus.MANUAL_REVIEW,
                0.5, "ipu.ac.in", List.of("Not verified"));
        assertEquals(EvidenceBackedTimingGate.Status.UNVERIFIED_EVENT,
                EvidenceBackedTimingGate.evaluate(manual, page(EXCERPT),
                        EXCERPT, DATE, CLOCK).status());
    }

    @Test void failedFetchCannotPass() {
        var failed = SourceEvidence.failure("https://www.ipu.ac.in/notices.php",
                "https://www.ipu.ac.in/notices.php", EvidenceFetchStatus.UNREACHABLE,
                503, "Unavailable");
        assertEquals(EvidenceBackedTimingGate.Status.MISSING_EVIDENCE,
                EvidenceBackedTimingGate.evaluate(verified(), failed,
                        EXCERPT, DATE, CLOCK).status());
    }

    @Test void deniedTimingCannotTriggerProactiveHandoffEvenWithSimulateAck() {
        assertThrows(IllegalArgumentException.class, () ->
                VerifiedEventCloudSimHandoff.runProactive(assessment(), verified(), page(EXCERPT),
                        EXCERPT, DATE, "SIMULATE",
                        Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC)));
    }
}

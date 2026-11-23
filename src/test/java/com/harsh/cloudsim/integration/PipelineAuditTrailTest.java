package com.harsh.cloudsim.integration;

import com.google.gson.JsonParser;
import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.event.model.EventType;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Offline, deterministic audit tests; no Gemini, network or actual HTTP latency. */
class PipelineAuditTrailTest {
    @TempDir Path directory;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"), ZoneOffset.UTC);

    private PipelineAuditTrail newTrail() {
        var trail = new PipelineAuditTrail("jdbc:sqlite:" + directory.resolve("audit.db"), CLOCK);
        trail.initialize();
        return trail;
    }

    @Test
    void rejectedNoticeIsPersistedAsStructuredJsonWithoutExecution() {
        var trail = newTrail();
        var verification = new EventVerificationResult(VerificationStatus.REJECTED,
                0.1, "ipu.ac.in", List.of("Claim not supported"));
        String id = trail.begin("notice", "https://www.ipu.ac.in/exam_notices.php", "PROACTIVE_CLOUDSIM")
                .verification(verification).decision("VERIFICATION_REJECTED").save();
        var json = JsonParser.parseString(trail.readDetails(id)).getAsJsonObject();
        assertEquals("VERIFICATION_REJECTED", json.get("gateDecision").getAsString());
        assertEquals("REJECTED", json.getAsJsonObject("verification").get("status").getAsString());
        assertFalse(json.has("execution"));
        assertEquals("NO_PERFORMANCE_OBSERVATION", json.get("performanceProvenance").getAsString());
        assertEquals(1, trail.count());
    }

    @Test
    void missingTimingEvidenceIsAuditedAndCannotBeMistakenForExecution() {
        var trail = newTrail();
        var verified = new EventVerificationResult(VerificationStatus.VERIFIED,
                0.85, "ipu.ac.in", List.of("Official notice matched"));
        var timing = new EvidenceBackedTimingGate.Decision(
                EvidenceBackedTimingGate.Status.MISSING_EVIDENCE, null,
                "No service-opening timestamp");
        String id = trail.begin("notice", "https://www.ipu.ac.in/exam_notices.php", "PROACTIVE_CLOUDSIM")
                .verification(verified).timing(timing, "", "").decision("TIMING_BLOCKED").save();
        var json = JsonParser.parseString(trail.readDetails(id)).getAsJsonObject();
        assertEquals("TIMING_BLOCKED", json.get("gateDecision").getAsString());
        assertEquals("MISSING_EVIDENCE", json.getAsJsonObject("timing").get("status").getAsString());
        assertFalse(json.has("execution"));
    }

    @Test
    void successfulCloudSimRunLinksForecastSafetyAndConfirmedVmActions() {
        var trail = newTrail();
        var assessment = new EventImpactAssessment("Controlled release", EventType.RESULT,
                "sandbox", 400, 2, 20, 0.9, "Uncalibrated test fixture");
        var verification = new EventVerificationResult(VerificationStatus.VERIFIED,
                0.95, "example.invalid", List.of("TEST FIXTURE"));
        var result = VerifiedEventCloudSimHandoff.run(assessment, verification, "SIMULATE");
        String id = trail.begin("Controlled release", "https://example.invalid", "CLOUDSIM")
                .assessment(assessment).verification(verification).acknowledgement(true)
                .execution(result).decision("CLOUDSIM_EXECUTED").save();
        var json = JsonParser.parseString(trail.readDetails(id)).getAsJsonObject();
        assertTrue(json.getAsJsonObject("forecast").get("recommendedVmCount").getAsInt() > 0);
        assertEquals(result.safety().approvalStatus().name(),
                json.getAsJsonObject("safety").get("status").getAsString());
        assertEquals(result.execution().provisionedCount(),
                json.getAsJsonObject("execution").get("provisioned").getAsInt());
        assertEquals(result.execution().actionRecords().size(),
                json.getAsJsonObject("execution").getAsJsonArray("actions").size());
        assertEquals("NO_PERFORMANCE_OBSERVATION", json.get("performanceProvenance").getAsString());
    }

    @Test
    void declinedAcknowledgementIsRecordedWithoutProvisioning() {
        var trail = newTrail();
        String id = trail.begin("notice", "https://example.invalid", "CLOUDSIM")
                .acknowledgement(false).decision("ACKNOWLEDGEMENT_DECLINED").save();
        var json = JsonParser.parseString(trail.readDetails(id)).getAsJsonObject();
        assertFalse(json.get("simulationAcknowledged").getAsBoolean());
        assertFalse(json.has("execution"));
    }

    @Test
    void recordsAreAppendOnlyAndDoubleSaveIsRefused() {
        var trail = newTrail();
        var attempt = trail.begin("one", "https://example.invalid", "VERIFY_ONLY")
                .decision("VERIFIED_NO_SCALING");
        String first = attempt.save();
        assertThrows(IllegalStateException.class, attempt::save);
        String second = trail.begin("two", "https://example.invalid", "VERIFY_ONLY")
                .decision("EVIDENCE_REJECTED").save();
        assertNotEquals(first, second);
        assertEquals(2, trail.count());
    }
}

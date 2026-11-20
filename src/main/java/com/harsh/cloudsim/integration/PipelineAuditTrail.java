package com.harsh.cloudsim.integration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.verification.evidence.SourceEvidence;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.claim.model.ClaimVerificationResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only event-level decision audit. Separate from Agent 8's legacy SLO
 * table, whose raw_trace_json column historically contains plain text.
 * No credentials, full fetched HTML, or synthetic latency measurements are stored.
 */
public final class PipelineAuditTrail {
    private static final Gson GSON = new Gson();
    private final String jdbcUrl;
    private final Clock clock;

    public PipelineAuditTrail(String jdbcUrl, Clock clock) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl);
        this.clock = Objects.requireNonNull(clock);
    }

    public static PipelineAuditTrail defaultTrail() {
        Path path = Path.of("target", "event-pipeline-audit.db").toAbsolutePath();
        try { Files.createDirectories(path.getParent()); }
        catch (java.io.IOException e) { throw new IllegalStateException("Audit directory unavailable", e); }
        return new PipelineAuditTrail("jdbc:sqlite:" + path, Clock.systemUTC());
    }

    public void initialize() {
        String ddl = """
            CREATE TABLE IF NOT EXISTS event_pipeline_audit (
                audit_id TEXT PRIMARY KEY,
                created_at_epoch_ms INTEGER NOT NULL,
                decision TEXT NOT NULL,
                verification_status TEXT,
                timing_status TEXT,
                execution_id TEXT,
                details_json TEXT NOT NULL CHECK (json_valid(details_json))
            )
            """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        } catch (SQLException e) { throw new IllegalStateException("Cannot initialize event audit database", e); }
    }

    public Attempt begin(String originalClaim, String sourceUrl, String mode) {
        return new Attempt(this, originalClaim, sourceUrl, mode);
    }

    public long count() {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM event_pipeline_audit")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) { throw new IllegalStateException("Cannot count audit records", e); }
    }

    public String readDetails(String auditId) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT details_json FROM event_pipeline_audit WHERE audit_id = ?")) {
            stmt.setString(1, auditId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) { throw new IllegalStateException("Cannot retrieve audit record", e); }
    }

    public static final class Attempt {
        private final PipelineAuditTrail trail;
        private final String id = UUID.randomUUID().toString();
        private final JsonObject details = new JsonObject();
        private String decision = "STARTED";
        private String verificationStatus;
        private String timingStatus;
        private String executionId;
        private boolean saved;

        private Attempt(PipelineAuditTrail trail, String claim, String sourceUrl, String mode) {
            this.trail = trail;
            details.addProperty("schemaVersion", 1);
            details.addProperty("originalClaim", Objects.requireNonNull(claim));
            details.addProperty("requestedSourceUrl", Objects.requireNonNull(sourceUrl));
            details.addProperty("mode", Objects.requireNonNull(mode));
            details.addProperty("performanceProvenance", "NO_PERFORMANCE_OBSERVATION");
            details.addProperty("demandProvenance", "LLM_ESTIMATE_UNCALIBRATED");
        }

        public String id() { return id; }
        public Attempt decision(String value) {
            decision = Objects.requireNonNull(value);
            details.addProperty("gateDecision", value);
            return this;
        }
        public Attempt assessment(EventImpactAssessment a) {
            if (a != null) {
                JsonObject v = new JsonObject();
                v.addProperty("eventName", a.eventName());
                v.addProperty("eventType", a.eventType().name());
                v.addProperty("affectedService", a.affectedService());
                v.addProperty("expectedUsers", a.expectedUsers());
                v.addProperty("trafficMultiplier", a.trafficMultiplier());
                v.addProperty("leadTimeMinutes", a.leadTimeMinutes());
                v.addProperty("confidence", a.confidence());
                v.addProperty("reasoning", a.reasoning());
                v.addProperty("provenance", "GEMINI_ESTIMATE_UNVALIDATED");
                details.add("assessment", v);
            }
            return this;
        }
        public Attempt verification(EventVerificationResult v) {
            if (v != null) {
                verificationStatus = v.status().name();
                JsonObject o = new JsonObject();
                o.addProperty("status", verificationStatus);
                o.addProperty("score", v.verificationScore());
                o.addProperty("sourceDomain", v.normalizedSourceDomain());
                o.add("findings", GSON.toJsonTree(v.reasons()));
                details.add("verification", o);
            }
            return this;
        }
        public Attempt evidence(SourceEvidence e) {
            if (e != null) {
                JsonObject o = new JsonObject();
                o.addProperty("fetchStatus", e.status().name());
                o.addProperty("httpStatus", e.httpStatusCode());
                o.addProperty("finalUrl", e.finalUrl());
                o.addProperty("retrievedAt", e.retrievedAt().toString());
                o.addProperty("pageTitle", e.pageTitle());
                o.addProperty("details", e.details());
                // The whole fetched page is deliberately not stored.
                details.add("evidence", o);
            }
            return this;
        }
        public Attempt claimVerification(ClaimVerificationResult v) {
            if (v != null) {
                JsonObject o = new JsonObject();
                o.addProperty("verdict", v.verdict().name());
                o.addProperty("confidence", v.confidence());
                o.add("supportedFacts", GSON.toJsonTree(v.supportedFacts()));
                o.add("missingOrContradictedFacts", GSON.toJsonTree(v.missingOrContradictedFacts()));
                o.add("evidenceExcerpts", GSON.toJsonTree(v.evidenceExcerpts()));
                details.add("semanticClaimVerification", o);
            }
            return this;
        }
        public Attempt timing(EvidenceBackedTimingGate.Decision v, String excerpt, String literalTimestamp) {
            if (v != null) {
                timingStatus = v.status().name();
                JsonObject o = new JsonObject();
                o.addProperty("status", timingStatus);
                o.addProperty("explanation", v.explanation());
                if (v.serviceStart() != null) o.addProperty("serviceStartUtc", v.serviceStart().toString());
                if (excerpt != null && !excerpt.isBlank()) o.addProperty("sourceExcerpt", excerpt);
                if (literalTimestamp != null && !literalTimestamp.isBlank())
                    o.addProperty("literalTimestamp", literalTimestamp);
                details.add("timing", o);
            }
            return this;
        }
        public Attempt acknowledgement(boolean acknowledged) {
            details.addProperty("simulationAcknowledged", acknowledged);
            return this;
        }
        public Attempt execution(ControlledPipelineDemo.RunResult result) {
            if (result != null) {
                executionId = result.execution().executionId();
                JsonObject f = new JsonObject();
                f.addProperty("recommendedVmCount", result.forecast().recommendedVmCount());
                f.addProperty("expectedRequestsPerSecond", result.forecast().expectedRequestsPerSecond());
                f.addProperty("predictionConfidence", result.forecast().predictionConfidence());
                f.addProperty("predictionSource", result.forecast().predictionSource());
                details.add("forecast", f);
                JsonObject plan = new JsonObject();
                plan.addProperty("planId", result.resourcePlan().planId());
                plan.addProperty("currentVmCount", result.resourcePlan().currentVmCount());
                plan.addProperty("targetVmCount", result.resourcePlan().targetVmCount());
                plan.addProperty("proposalCount", result.resourcePlan().scalingProposals().size());
                details.add("resourcePlan", plan);
                JsonObject safety = new JsonObject();
                safety.addProperty("status", result.safety().approvalStatus().name());
                safety.addProperty("reason", result.safety().evaluationReason());
                safety.addProperty("approvedProposals", result.safety().approvedProposals().size());
                details.add("safety", safety);
                JsonObject exec = new JsonObject();
                exec.addProperty("executionId", executionId);
                exec.addProperty("status", result.execution().status().name());
                exec.addProperty("environment", result.execution().environment().name());
                exec.addProperty("provisioned", result.execution().provisionedCount());
                exec.addProperty("deprovisioned", result.execution().deprovisionedCount());
                exec.addProperty("failed", result.execution().failedCount());
                exec.addProperty("vmsBefore", result.before().runningVms());
                exec.addProperty("vmsAfter", result.after().runningVms());
                com.google.gson.JsonArray actions = new com.google.gson.JsonArray();
                for (var action : result.execution().actionRecords()) {
                    JsonObject a = new JsonObject();
                    a.addProperty("vmId", action.vmId());
                    a.addProperty("vmType", action.vmType());
                    a.addProperty("action", action.action().name());
                    a.addProperty("requestedHostId", action.requestedHostId());
                    a.addProperty("actualHostId", action.actualHostId());
                    a.addProperty("success", action.success());
                    a.addProperty("decisionSource", action.decisionSource().name());
                    a.addProperty("auditMessage", action.auditMessage());
                    actions.add(a);
                }
                exec.add("actions", actions);
                details.add("execution", exec);
                if (result.evaluation() != null)
                    details.addProperty("evaluationId", result.evaluation().evaluationId());
                details.addProperty("performanceProvenance", result.observationSource());
            }
            return this;
        }
        public Attempt failure(String category, String explanation) {
            details.addProperty("failureCategory", category);
            details.addProperty("failureExplanation", explanation);
            return decision("ERROR_BLOCKED");
        }
        public String save() {
            if (saved) throw new IllegalStateException("Attempt already saved");
            details.addProperty("gateDecision", decision);
            String sql = """
                INSERT INTO event_pipeline_audit
                    (audit_id, created_at_epoch_ms, decision, verification_status, timing_status, execution_id, details_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            try (Connection conn = DriverManager.getConnection(trail.jdbcUrl);
                 PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, id);
                statement.setLong(2, trail.clock.millis());
                statement.setString(3, decision);
                statement.setString(4, verificationStatus);
                statement.setString(5, timingStatus);
                statement.setString(6, executionId);
                statement.setString(7, GSON.toJson(details));
                statement.executeUpdate();
                saved = true;
                return id;
            } catch (SQLException e) { throw new IllegalStateException("Cannot persist pipeline audit", e); }
        }
    }
}

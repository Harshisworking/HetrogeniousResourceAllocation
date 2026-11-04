package com.harsh.cloudsim.agent.evaluation.persistence;

import com.harsh.cloudsim.agent.evaluation.model.EvaluationOutcome;
import com.harsh.cloudsim.agent.evaluation.model.SloStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SqliteDecisionMemoryRepository {

    private final String jdbcUrl;

    public SqliteDecisionMemoryRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public void initializeDatabase() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS decision_trace_memory (
                    trace_id TEXT PRIMARY KEY,
                    timestamp_ms INTEGER,
                    execution_id TEXT,
                    slo_status TEXT,
                    latency_delta_ms REAL,
                    rollback_recommended INTEGER,
                    multiplier_adjustment REAL,
                    raw_trace_json TEXT
                );
                """;
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite database", e);
        }
    }

    public void saveTrace(EvaluationOutcome outcome, String rawTraceJson) {
        String sql = """
                INSERT INTO decision_trace_memory 
                (trace_id, timestamp_ms, execution_id, slo_status, latency_delta_ms, rollback_recommended, multiplier_adjustment, raw_trace_json) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, outcome.evaluationId());
            pstmt.setLong(2, outcome.evaluatedAtEpochMs());
            pstmt.setString(3, outcome.executionId());
            pstmt.setString(4, outcome.sloStatus().name());
            pstmt.setDouble(5, outcome.latencyDeltaMs());
            pstmt.setInt(6, outcome.rollbackRecommended() ? 1 : 0);
            pstmt.setDouble(7, outcome.multiplierAdjustmentDelta());
            pstmt.setString(8, rawTraceJson);
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save evaluation trace", e);
        }
    }

    public List<EvaluationOutcome> getRecentBreaches(int limit) {
        List<EvaluationOutcome> breaches = new ArrayList<>();
        String sql = "SELECT * FROM decision_trace_memory WHERE slo_status = ? ORDER BY timestamp_ms DESC LIMIT ?";
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, SloStatus.BREACHED.name());
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                breaches.add(new EvaluationOutcome(
                        rs.getString("trace_id"),
                        rs.getString("execution_id"),
                        rs.getLong("timestamp_ms"),
                        SloStatus.valueOf(rs.getString("slo_status")),
                        rs.getDouble("latency_delta_ms"),
                        rs.getInt("rollback_recommended") == 1,
                        rs.getDouble("multiplier_adjustment"),
                        rs.getString("raw_trace_json")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch recent breaches", e);
        }
        return breaches;
    }
}
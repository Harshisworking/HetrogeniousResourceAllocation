package com.harsh.cloudsim.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CloudSimScalingBenchmarkTest {
    @Test
    void allStrategiesCompleteSameNumberOfMeasuredTasks() {
        for (var result : CloudSimScalingBenchmark.runAll()) {
            assertEquals(CloudSimScalingBenchmark.TASK_COUNT, result.submitted());
            assertEquals(CloudSimScalingBenchmark.TASK_COUNT, result.finished(),
                    () -> "Unfinished tasks in " + result.strategy());
            assertEquals(0, result.failed());
            assertEquals(result.finished(), new HashSet<>(result.tasks().stream()
                    .map(CloudSimScalingBenchmark.TaskObservation::cloudletId).toList()).size());
        }
    }

    @Test
    void measuredCloudletTimesAreFiniteAndConsistent() {
        for (var result : CloudSimScalingBenchmark.runAll()) {
            assertTrue(Double.isFinite(result.meanWaitingSeconds()));
            assertTrue(Double.isFinite(result.meanResponseSeconds()));
            assertTrue(Double.isFinite(result.p95ResponseSeconds()));
            assertTrue(result.makespanSeconds() >= 0);
            for (var task : result.tasks()) {
                assertTrue(task.actualStartSeconds() >= task.scheduledArrivalSeconds() - 0.0001);
                assertTrue(task.finishSeconds() >= task.actualStartSeconds());
                assertEquals(task.finishSeconds() - task.scheduledArrivalSeconds(),
                        task.responseSeconds(), 0.0001);
                assertTrue(task.waitingSeconds() >= 0);
            }
        }
    }

    @Test
    void schedulingIsExplicitAndReproducible() {
        var first = CloudSimScalingBenchmark.runAll();
        var second = CloudSimScalingBenchmark.runAll();
        assertEquals(List.of(CloudSimScalingBenchmark.Strategy.STATIC,
                CloudSimScalingBenchmark.Strategy.REACTIVE,
                CloudSimScalingBenchmark.Strategy.PROACTIVE),
                first.stream().map(CloudSimScalingBenchmark.Result::strategy).toList());
        assertEquals(-1, first.get(0).extraVmRequestTimeSeconds());
        assertEquals(25, first.get(1).extraVmRequestTimeSeconds());
        assertEquals(15, first.get(2).extraVmRequestTimeSeconds());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).meanResponseSeconds(), second.get(i).meanResponseSeconds(), 1e-6);
            assertEquals(first.get(i).p95ResponseSeconds(), second.get(i).p95ResponseSeconds(), 1e-6);
        }
    }

    @Test
    void boundCloudletsUseCommonArrivalWithoutSecondFullDelay() {
        var results = CloudSimScalingBenchmark.runAll();
        var reactive = results.get(1);
        var proactive = results.get(2);
        // Extra VM exists at t=15 in proactive, but jobs arrive at t=20.
        // It exists only at t=25 in reactive; jobs must not be held until t=45.
        for (var task : proactive.tasks()) {
            if (task.vmId() == 1) {
                assertTrue(task.actualStartSeconds() >= 20.0,
                        "Proactive extra VM must not execute pre-arrival work");
                assertTrue(task.actualStartSeconds() < 25.0,
                        "Proactive cloudlets received an unintended second delay");
            }
        }
        for (var task : reactive.tasks()) {
            if (task.vmId() == 1) {
                assertTrue(task.actualStartSeconds() >= 25.0,
                        "Reactive extra VM must not execute before it exists");
                assertTrue(task.actualStartSeconds() < 30.0,
                        "Reactive cloudlets received an unintended second delay");
            }
        }
    }

    @Test
    void exportsRawMeasurementsAndSummary(@TempDir Path dir) throws Exception {
        var results = CloudSimScalingBenchmark.runAll();
        var summary = dir.resolve("results.csv");
        var raw = dir.resolve("tasks.csv");
        CloudSimScalingBenchmark.writeCsv(results, summary);
        CloudSimScalingBenchmark.writeTaskCsv(results, raw);
        assertEquals(4, Files.readAllLines(summary).size());
        assertEquals(1 + 3 * CloudSimScalingBenchmark.TASK_COUNT,
                Files.readAllLines(raw).size());
        assertTrue(Files.readString(raw).contains("PROACTIVE"));
    }
}

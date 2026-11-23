package com.harsh.cloudsim.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationDispatchQuotaTest {
    @Test void extraCallbacksCannotReleaseExtraTasks() {
        SimulationDispatchQuota q = new SimulationDispatchQuota(20, 1, 4);
        assertEquals(0, q.available(15, 24));
        assertEquals(4, q.available(20.1, 24));
        q.recordDispatch(4);
        assertEquals(0, q.available(20.2, 20));
        assertEquals(0, q.available(20.9, 20));
        assertEquals(4, q.available(21.1, 20));
        q.recordDispatch(4);
        assertEquals(0, q.available(21.3, 16));
        assertEquals(16, q.available(26.1, 16));
    }

    @Test void releasesNoMoreThanRemainingBacklog() {
        SimulationDispatchQuota q = new SimulationDispatchQuota(20, 1, 4);
        assertEquals(2, q.available(20.1, 2));
        q.recordDispatch(2);
        assertEquals(2, q.available(20.2, 10));
    }

    @Test void rejectsInvalidSchedulesAndRetrogradeTime() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationDispatchQuota(20, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new SimulationDispatchQuota(20, 1, 0));
        SimulationDispatchQuota q = new SimulationDispatchQuota(20, 1, 4);
        q.available(22, 2);
        assertThrows(IllegalArgumentException.class, () -> q.available(21, 2));
        assertThrows(IllegalArgumentException.class, () -> q.recordDispatch(-1));
    }
}

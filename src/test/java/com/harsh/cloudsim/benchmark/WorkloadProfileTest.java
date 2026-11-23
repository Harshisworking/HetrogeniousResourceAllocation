package com.harsh.cloudsim.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkloadProfileTest {
    @Test void fixedProfilesAreDeterministicAndMonotonic() {
        for (var profile : WorkloadProfile.values()) {
            double previous = 0;
            for (int i = 0; i < profile.taskCount(); i++) {
                double current = profile.arrivalSeconds(i);
                assertTrue(current >= previous);
                previous = current;
            }
            assertEquals(previous, profile.lastArrivalSeconds());
        }
    }

    @Test void distinctTrafficShapesArePresent() {
        assertEquals(8, WorkloadProfile.QUIET.taskCount());
        assertEquals(24, WorkloadProfile.BURST.taskCount());
        assertEquals(48, WorkloadProfile.SUSTAINED.taskCount());
        assertEquals(20, WorkloadProfile.REPEATED_SPIKES.arrivalSeconds(11));
        assertEquals(60, WorkloadProfile.REPEATED_SPIKES.arrivalSeconds(12));
    }

    @Test void invalidIndexIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> WorkloadProfile.BURST.arrivalSeconds(-1));
        assertThrows(IllegalArgumentException.class, () -> WorkloadProfile.BURST.arrivalSeconds(24));
    }
}

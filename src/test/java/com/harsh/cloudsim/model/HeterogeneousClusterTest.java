package com.harsh.cloudsim.model;

import org.cloudbus.cloudsim.hosts.Host;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HeterogeneousClusterTest {

    private List<Host> hosts;

    @BeforeEach
    void createFreshCluster() {
        hosts = HeterogeneousCluster.createClusterHosts();
    }

    @Test
    void shouldCreateExactlyTwoHosts() {
        assertEquals(2, hosts.size());
    }

    @Test
    void shouldCreateExpectedLaptopHost() {
        Host laptopHost = hosts.get(0);

        assertAll(
                () -> assertEquals(0, laptopHost.getId()),
                () -> assertEquals(4, laptopHost.getPeList().size()),
                () -> assertEquals(
                        16_384,
                        laptopHost.getRam().getCapacity()
                )
        );
    }

    @Test
    void shouldCreateExpectedPhoneHost() {
        Host phoneHost = hosts.get(1);

        assertAll(
                () -> assertEquals(1, phoneHost.getId()),
                () -> assertEquals(2, phoneHost.getPeList().size()),
                () -> assertEquals(
                        4_096,
                        phoneHost.getRam().getCapacity()
                )
        );
    }
}
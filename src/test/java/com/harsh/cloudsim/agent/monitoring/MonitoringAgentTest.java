package com.harsh.cloudsim.agent.monitoring;

import com.harsh.cloudsim.agent.monitoring.model.HostMetrics;
import com.harsh.cloudsim.agent.monitoring.model.MetricsSnapshot;
import com.harsh.cloudsim.agent.monitoring.model.VmMetrics;
import com.harsh.cloudsim.agent.monitoring.model.WorkloadMetrics;
import com.harsh.cloudsim.model.HeterogeneousCluster;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Agent 3 — Monitoring Agent Tests")
class MonitoringAgentTest {

    @Test
    @DisplayName("Empty cluster produces safe, zero-normalized snapshot without NaN or exceptions")
    void testEmptyClusterMonitoring() {
        MonitoringAgent agent = new MonitoringAgent();
        MetricsSnapshot snapshot = agent.captureSnapshot(null, Collections.emptyList(), Collections.emptyList(), null);

        assertNotNull(snapshot);
        assertEquals(0, snapshot.totalHosts());
        assertEquals(0, snapshot.activeHosts());
        assertEquals(0, snapshot.totalVms());
        assertEquals(0, snapshot.runningVms());
        assertEquals(0.0, snapshot.averageCpuUtilization());
        assertEquals(0.0, snapshot.averageRamUtilization());
        assertEquals(0.0, snapshot.estimatedRequestsPerSecond());
        assertEquals(0.0, snapshot.estimatedQueueDelaySeconds());
        assertEquals(0.0, snapshot.errorRate());
        assertTrue(snapshot.hostMetrics().isEmpty());
        assertTrue(snapshot.vmMetrics().isEmpty());
    }

    @Test
    @DisplayName("Captures multiple hosts accurately from HeterogeneousCluster")
    void testMultipleHostsMonitoring() {
        List<Host> hosts = HeterogeneousCluster.createClusterHosts();
        MonitoringAgent agent = new MonitoringAgent();
        MetricsSnapshot snapshot = agent.captureSnapshot(null, hosts, Collections.emptyList(), null);

        assertEquals(2, snapshot.totalHosts());
        assertEquals(2, snapshot.hostMetrics().size());

        HostMetrics host0 = snapshot.hostMetrics().getFirst();
        assertEquals(0, host0.hostId());
        assertEquals("x86", host0.architecture());
        assertEquals(4, host0.totalPes());
        assertEquals(16384, host0.totalRamMb());

        HostMetrics host1 = snapshot.hostMetrics().get(1);
        assertEquals(1, host1.hostId());
        assertEquals("ARM", host1.architecture());
        assertEquals(2, host1.totalPes());
        assertEquals(4096, host1.totalRamMb());
    }

    @Test
    @DisplayName("Captures VM metrics correctly")
    void testVmMetricsCollection() {
        List<Vm> vms = new ArrayList<>();
        vms.add(new VmSimple(0, 5000, 2).setRam(4096));
        vms.add(new VmSimple(1, 1000, 1).setRam(1024));

        MonitoringAgent agent = new MonitoringAgent();
        MetricsSnapshot snapshot = agent.captureSnapshot(null, Collections.emptyList(), vms, null);

        assertEquals(2, snapshot.totalVms());
        assertEquals(2, snapshot.vmMetrics().size());

        VmMetrics vm0 = snapshot.vmMetrics().getFirst();
        assertEquals(0, vm0.vmId());
        assertEquals(2, vm0.pesNumber());
        assertEquals(4096, vm0.ramMb());
    }

    @Test
    @DisplayName("Monitors cloudlet execution across full simulation lifecycle")
    void testSimulationLifecycleMonitoring() {
        CloudSim simulation = new CloudSim();
        List<Host> hostList = HeterogeneousCluster.createClusterHosts();
        Datacenter datacenter = new DatacenterSimple(simulation, hostList);
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        Vm vm = new VmSimple(0, 5000, 2).setRam(4096).setBw(1000).setSize(10000);
        Cloudlet cloudlet = new CloudletSimple(10000, 2, new UtilizationModelFull());

        broker.submitVm(vm);
        broker.submitCloudlet(cloudlet);

        MonitoringAgent agent = new MonitoringAgent();

        // 1. Initial snapshot before start
        MetricsSnapshot initial = agent.captureSnapshot(simulation, hostList, List.of(vm), broker);
        assertEquals(0, initial.finishedCloudlets());

        // 2. Run simulation
        simulation.start();

        // 3. Post-execution snapshot
        MetricsSnapshot finishedSnapshot = agent.captureSnapshot(simulation, hostList, List.of(vm), broker);

        assertEquals(1, finishedSnapshot.finishedCloudlets());
        assertEquals(0, finishedSnapshot.failedCloudlets());
        assertEquals(0.0, finishedSnapshot.errorRate());
        assertTrue(finishedSnapshot.simulationTime() > 0.0);
    }

    @Test
    @DisplayName("Immutability test: Host and VM collections cannot be mutated externally")
    void testImmutabilityOfSnapshot() {
        MonitoringAgent agent = new MonitoringAgent();
        List<Host> hosts = HeterogeneousCluster.createClusterHosts();
        MetricsSnapshot snapshot = agent.captureSnapshot(null, hosts, Collections.emptyList(), null);

        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.hostMetrics().add(new HostMetrics(99, "x86", 1, 1, 1000, 1000, 1024, 1024, 0.0, 0.0, true))
        );
    }

    @Test
    @DisplayName("Model validation rejects invalid metrics and illegal bounds")
    void testValidationRejections() {
        // Negative ID
        assertThrows(IllegalArgumentException.class, () ->
                new HostMetrics(-1, "x86", 4, 4, 1000, 1000, 2048, 2048, 0.5, 0.5, true)
        );

        // Utilization > 1.0
        assertThrows(IllegalArgumentException.class, () ->
                new HostMetrics(0, "x86", 4, 4, 1000, 1000, 2048, 2048, 1.5, 0.5, true)
        );

        // NaN CPU utilization
        assertThrows(IllegalArgumentException.class, () ->
                new VmMetrics(0, 0, 2, 2000, 2048, Double.NaN, 0, true)
        );

        // Negative queue length
        assertThrows(IllegalArgumentException.class, () ->
                new WorkloadMetrics(10, 0, 0, 10, 0, -1, 1.0, 0.0, 0.0)
        );
    }

    @Test
    @DisplayName("Non-mutation test: Monitoring agent does not alter CloudSim state")
    void testNonMutationOfCloudSimState() {
        List<Host> hosts = HeterogeneousCluster.createClusterHosts();
        int initialHostCount = hosts.size();

        MonitoringAgent agent = new MonitoringAgent();
        agent.captureSnapshot(null, hosts, Collections.emptyList(), null);

        assertEquals(initialHostCount, hosts.size(), "Monitoring must not modify host list size.");
    }

    @Test
    @DisplayName("Determinism test: Repeated calls on static state produce equivalent metrics")
    void testDeterminism() {
        List<Host> hosts = HeterogeneousCluster.createClusterHosts();
        MonitoringAgent agent = new MonitoringAgent();

        MetricsSnapshot s1 = agent.captureSnapshot(null, hosts, Collections.emptyList(), null);
        MetricsSnapshot s2 = agent.captureSnapshot(null, hosts, Collections.emptyList(), null);

        assertEquals(s1.totalHosts(), s2.totalHosts());
        assertEquals(s1.averageCpuUtilization(), s2.averageCpuUtilization());
        assertEquals(s1.averageRamUtilization(), s2.averageRamUtilization());
    }
}
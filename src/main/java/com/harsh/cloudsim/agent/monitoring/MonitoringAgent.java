package com.harsh.cloudsim.agent.monitoring;

import com.harsh.cloudsim.agent.monitoring.model.HostMetrics;
import com.harsh.cloudsim.agent.monitoring.model.MetricsSnapshot;
import com.harsh.cloudsim.agent.monitoring.model.VmMetrics;
import com.harsh.cloudsim.agent.monitoring.model.WorkloadMetrics;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 3 — Monitoring Agent.
 *
 * Observes CloudSim simulation state, hosts, VMs, and cloudlets,
 * and produces a clean, deterministic, immutable MetricsSnapshot.
 *
 * This agent performs purely observational measurements and does not alter
 * any CloudSim simulation state or invoke external services.
 */
public class MonitoringAgent {

    private final AtomicLong sequenceGenerator = new AtomicLong(0);

    /**
     * Captures a point-in-time metrics snapshot from CloudSim.
     *
     * @param simulation CloudSim instance providing the simulation clock (nullable)
     * @param hosts      List of datacenter hosts to monitor (nullable)
     * @param vms        List of VMs to monitor (nullable)
     * @param broker     DatacenterBroker managing cloudlets (nullable)
     * @return clean, validated, immutable MetricsSnapshot
     */
    public MetricsSnapshot captureSnapshot(
            CloudSim simulation,
            List<Host> hosts,
            List<Vm> vms,
            DatacenterBroker broker
    ) {
        long sequence = sequenceGenerator.getAndIncrement();
        double clock = (simulation != null) ? Math.max(0.0, simulation.clock()) : 0.0;

        List<Host> safeHosts = (hosts != null) ? hosts : Collections.emptyList();
        List<Vm> safeVms = (vms != null) ? vms : Collections.emptyList();

        // 1. Host Metrics
        List<HostMetrics> hostMetricsList = new ArrayList<>();
        double hostCpuSum = 0.0;
        double hostRamSum = 0.0;
        int activeHostCount = 0;

        for (Host host : safeHosts) {
            HostMetrics hm = extractHostMetrics(host);
            hostMetricsList.add(hm);
            hostCpuSum += hm.cpuUtilization();
            hostRamSum += hm.ramUtilization();
            if (hm.active()) {
                activeHostCount++;
            }
        }

        double avgCpu = safeHosts.isEmpty() ? 0.0 : (hostCpuSum / safeHosts.size());
        double avgRam = safeHosts.isEmpty() ? 0.0 : (hostRamSum / safeHosts.size());

        // 2. VM Metrics
        List<VmMetrics> vmMetricsList = new ArrayList<>();
        int runningVmCount = 0;
        for (Vm vm : safeVms) {
            VmMetrics vmMetric = extractVmMetrics(vm);
            vmMetricsList.add(vmMetric);
            if (vmMetric.inExecution()) {
                runningVmCount++;
            }
        }

        // 3. Workload Metrics
        WorkloadMetrics workload = extractWorkloadMetrics(broker, clock);

        return new MetricsSnapshot(
                sequence,
                clock,
                safeHosts.size(),
                activeHostCount,
                safeVms.size(),
                runningVmCount,
                workload.waitingCloudlets(),
                workload.runningCloudlets(),
                workload.finishedCloudlets(),
                workload.failedCloudlets(),
                clamp(avgCpu, 0.0, 1.0),
                clamp(avgRam, 0.0, 1.0),
                workload.estimatedRequestsPerSec(),
                workload.estimatedQueueDelaySec(),
                workload.errorRate(),
                hostMetricsList,
                vmMetricsList,
                workload
        );
    }

    private HostMetrics extractHostMetrics(Host host) {
        Objects.requireNonNull(host, "Host element must not be null.");

        long hostId = host.getId();
        // Resolve architecture label; fallback to x86 or ARM if standard cluster IDs
        String arch = "x86";
        if (hostId == 1) {
            arch = "ARM";
        }

        long totalPes = host.getNumberOfPes();
        long freePes = host.getFreePesNumber();
        long availablePes = Math.max(0, Math.min(totalPes, freePes));

        double totalMips = host.getTotalMipsCapacity();
        double cpuMipsUsage = host.getCpuMipsUtilization();
        double availableMips = Math.max(0.0, totalMips - cpuMipsUsage);

        long totalRam = host.getRam().getCapacity();
        long availableRam = host.getRam().getAvailableResource();

        double cpuUtil = host.getCpuPercentUtilization();
        if (!Double.isFinite(cpuUtil) || cpuUtil < 0.0) {
            cpuUtil = (totalMips > 0.0) ? clamp(cpuMipsUsage / totalMips, 0.0, 1.0) : 0.0;
        } else {
            cpuUtil = clamp(cpuUtil, 0.0, 1.0);
        }

        double ramUtil = host.getRam().getPercentUtilization();
        if (!Double.isFinite(ramUtil) || ramUtil < 0.0) {
            ramUtil = (totalRam > 0) ? clamp((double) (totalRam - availableRam) / totalRam, 0.0, 1.0) : 0.0;
        } else {
            ramUtil = clamp(ramUtil, 0.0, 1.0);
        }

        boolean isActive = host.isActive();

        return new HostMetrics(
                hostId,
                arch,
                totalPes,
                availablePes,
                totalMips,
                availableMips,
                totalRam,
                availableRam,
                cpuUtil,
                ramUtil,
                isActive
        );
    }

    private VmMetrics extractVmMetrics(Vm vm) {
        Objects.requireNonNull(vm, "VM element must not be null.");

        long vmId = vm.getId();
        long hostId = (vm.getHost() != null) ? vm.getHost().getId() : -1;
        long pes = vm.getNumberOfPes();
        double totalMips = vm.getTotalMipsCapacity();
        long ram = vm.getRam().getCapacity();

        double cpuUtil = vm.getCpuPercentUtilization();
        if (!Double.isFinite(cpuUtil) || cpuUtil < 0.0) {
            cpuUtil = 0.0;
        } else {
            cpuUtil = clamp(cpuUtil, 0.0, 1.0);
        }

        // Cloudlets currently running inside this specific VM
        int runningCloudlets = 0;
        if (vm.getCloudletScheduler() != null) {
            runningCloudlets = vm.getCloudletScheduler().getCloudletExecList().size();
        }

        boolean inExec = vm.isInMigration() || (vm.getHost() != null && vm.getHost().isActive());

        return new VmMetrics(
                vmId,
                hostId,
                pes,
                totalMips,
                ram,
                cpuUtil,
                runningCloudlets,
                inExec
        );
    }

    private WorkloadMetrics extractWorkloadMetrics(DatacenterBroker broker, double clock) {
        if (broker == null) {
            return new WorkloadMetrics(0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0);
        }

        List<Cloudlet> submitted = broker.getCloudletSubmittedList();
        List<Cloudlet> waiting = broker.getCloudletWaitingList();
        List<Cloudlet> finished = broker.getCloudletFinishedList();

        int submittedCount = submitted.size();
        int waitingCount = waiting.size();
        int finishedCount = finished.size();
        int runningCount = 0;
        int failedCount = 0;

        double totalWait = 0.0;
        int waitCount = 0;

        for (Cloudlet c : submitted) {
            if (c.getStatus() == Cloudlet.Status.INEXEC) {
                runningCount++;
            } else if (c.getStatus() == Cloudlet.Status.FAILED) {
                failedCount++;
            }

            if (c.getExecStartTime() >= 0) {
                totalWait += Math.max(0.0, c.getExecStartTime() - c.getSubmissionDelay());
                waitCount++;
            }
        }

        int queueLength = waitingCount;

        // Arrival rate proxy: submitted cloudlets per unit simulation time
        double arrivalRateProxy = 0.0;
        if (clock > 0.0) {
            arrivalRateProxy = (double) submittedCount / clock;
        } else if (submittedCount > 0) {
            arrivalRateProxy = (double) submittedCount;
        }

        // Deterministic queue delay proxy: mean wait time for started or finished tasks
        double avgQueueDelaySec = (waitCount > 0) ? (totalWait / waitCount) : 0.0;

        // Error rate: failed cloudlets / total processed cloudlets
        int totalProcessed = finishedCount + failedCount + runningCount;
        double errorRate = (totalProcessed > 0) ? clamp((double) failedCount / totalProcessed, 0.0, 1.0) : 0.0;

        return new WorkloadMetrics(
                submittedCount,
                waitingCount,
                runningCount,
                finishedCount,
                failedCount,
                queueLength,
                arrivalRateProxy,
                avgQueueDelaySec,
                errorRate
        );
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
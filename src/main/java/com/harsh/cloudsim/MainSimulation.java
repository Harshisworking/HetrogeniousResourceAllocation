package com.harsh.cloudsim;

import com.harsh.cloudsim.model.HeterogeneousCluster;
import com.harsh.cloudsim.policy.FlexibleVmAllocationPolicy;
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

import java.util.ArrayList;
import java.util.List;

public class MainSimulation {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   CLOUDSIM HETEROGENEOUS CLUSTER SIMULATION     ");
        System.out.println("==================================================");

        // Instantiates core simulation engine using CloudSim
        CloudSim simulation = new CloudSim();
        List<Host> hostList = HeterogeneousCluster.createClusterHosts();
        FlexibleVmAllocationPolicy policy = new FlexibleVmAllocationPolicy();

        Datacenter datacenter = new DatacenterSimple(simulation, hostList, policy);
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        List<Vm> vmList = new ArrayList<>();
        Vm heavyVm = new VmSimple(0, 5000, 2).setRam(4096).setBw(1000).setSize(10000);
        Vm lightVm = new VmSimple(1, 1000, 1).setRam(1024).setBw(500).setSize(5000);
        vmList.add(heavyVm);
        vmList.add(lightVm);

        List<Cloudlet> cloudletList = new ArrayList<>();
        Cloudlet task0 = new CloudletSimple(20000, 2, new UtilizationModelFull());
        Cloudlet task1 = new CloudletSimple(5000, 1, new UtilizationModelFull());
        cloudletList.add(task0);
        cloudletList.add(task1);

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        simulation.start();

        System.out.println("\n==================================================");
        System.out.println("              SIMULATION RESULTS                  ");
        System.out.println("==================================================");
        for (Cloudlet c : broker.getCloudletFinishedList()) {
            System.out.printf("Task #%d | Execution Time: %.2f sec | Executed on VM #%d (Host #%d)%n",
                    c.getId(), c.getActualCpuTime(), c.getVm().getId(), c.getVm().getHost().getId());
        }
    }
}

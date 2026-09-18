package com.harsh.cloudsim.model;

import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;

import java.util.ArrayList;
import java.util.List;

public class HeterogeneousCluster {

    public static List<Host> createClusterHosts() {
        List<Host> hostList = new ArrayList<>();

        // Host 0: High-Performance x86 Laptop/Desktop
        List<Pe> laptopPes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            laptopPes.add(new PeSimple(10000));
        }
        Host laptopHost = new HostSimple(16384, 100000, 100000, laptopPes);
        laptopHost.setId(0);

        // Host 1: Resource-Constrained ARM Mobile Phone
        List<Pe> phonePes = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            phonePes.add(new PeSimple(2000));
        }
        Host phoneHost = new HostSimple(4096, 50000, 50000, phonePes);
        phoneHost.setId(1);

        hostList.add(laptopHost);
        hostList.add(phoneHost);

        return hostList;
    }
}

package com.harsh.cloudsim.policy;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.Optional;
import java.util.function.Function;

public class FlexibleVmAllocationPolicy extends VmAllocationPolicySimple {

    private Function<Vm, Integer> externalDecisionEngine = null;

    public void setExternalDecisionEngine(Function<Vm, Integer> decisionEngine) {
        this.externalDecisionEngine = decisionEngine;
    }

    @Override
    public Optional<Host> defaultFindHostForVm(Vm vm) {
        if (externalDecisionEngine != null) {
            try {
                int targetHostId = externalDecisionEngine.apply(vm);
                System.out.printf("%n[POLICY HOOK] Agent requested Host #%d for VM #%d%n", targetHostId, vm.getId());
                
                for (Host host : getHostList()) {
                    if (host.getId() == targetHostId && host.isSuitableForVm(vm)) {
                        return Optional.of(host);
                    }
                }
            } catch (Exception e) {
                System.err.println("Agent execution error, falling back to algorithmic rules: " + e.getMessage());
            }
        }

        // Standalone Rule: Heavy VMs (>2000 MIPS) on Laptop (Host 0), Light VMs on Phone (Host 1)
        if (vm.getMips() > 2000 && getHostList().get(0).isSuitableForVm(vm)) {
            System.out.printf("[STANDALONE RULE] Assigned Heavy VM #%d to x86 Laptop (Host 0)%n", vm.getId());
            return Optional.of(getHostList().get(0));
        } else if (getHostList().size() > 1 && getHostList().get(1).isSuitableForVm(vm)) {
            System.out.printf("[STANDALONE RULE] Assigned Light VM #%d to ARM Phone (Host 1)%n", vm.getId());
            return Optional.of(getHostList().get(1));
        }

        return super.defaultFindHostForVm(vm);
    }
}

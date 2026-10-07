package com.harsh.cloudsim.agent.model;

public class HostCapacity {
    private final long hostId;
    private final int architectureType; // 0 = x86, 1 = ARM
    private long availableRamMb;
    private long availablePes;

    public HostCapacity(long hostId, int architectureType, long maxRamMb, long maxPes, long usedRamMb, long usedPes) {
        this.hostId = hostId;
        this.architectureType = architectureType;
        this.availableRamMb = maxRamMb - usedRamMb;
        this.availablePes = maxPes - usedPes;
    }

    public long getHostId() {
        return hostId;
    }

    public boolean canAllocate(long requestedRamMb, long requestedPes) {
        if (this.availableRamMb >= requestedRamMb && this.availablePes >= requestedPes) {
            return true;
        }
        return false;
    }

    public void allocate(long requestedRamMb, long requestedPes) {
        this.availableRamMb -= requestedRamMb;
        this.availablePes -= requestedPes;
    }

    public void release(long requestedRamMb, long requestedPes) {
        this.availableRamMb += requestedRamMb;
        this.availablePes += requestedPes;
    }
}
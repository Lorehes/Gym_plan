package com.gymplan.analytics.application.dto;

public class FrequencyDayData {

    private final long sessionCount;
    private final double totalVolume;

    public FrequencyDayData(long sessionCount, double totalVolume) {
        this.sessionCount = sessionCount;
        this.totalVolume = totalVolume;
    }

    public long getSessionCount() { return sessionCount; }
    public double getTotalVolume() { return totalVolume; }
}

package com.gymplan.analytics.application.dto;

public class SummaryResponse {

    private final String period;
    private final long totalSessions;
    private final double totalVolume;
    private final long totalDurationSec;
    private final long avgDurationSec;
    private final String mostTrainedMuscle;

    public SummaryResponse(String period, long totalSessions, double totalVolume,
                           long totalDurationSec, long avgDurationSec, String mostTrainedMuscle) {
        this.period = period;
        this.totalSessions = totalSessions;
        this.totalVolume = totalVolume;
        this.totalDurationSec = totalDurationSec;
        this.avgDurationSec = avgDurationSec;
        this.mostTrainedMuscle = mostTrainedMuscle;
    }

    public String getPeriod() { return period; }
    public long getTotalSessions() { return totalSessions; }
    public double getTotalVolume() { return totalVolume; }
    public long getTotalDurationSec() { return totalDurationSec; }
    public long getAvgDurationSec() { return avgDurationSec; }
    public String getMostTrainedMuscle() { return mostTrainedMuscle; }
}

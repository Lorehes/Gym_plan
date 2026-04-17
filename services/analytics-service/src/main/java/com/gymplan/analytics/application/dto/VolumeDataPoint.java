package com.gymplan.analytics.application.dto;

public class VolumeDataPoint {

    private final String date;
    private final String muscle;
    private final double volume;

    public VolumeDataPoint(String date, String muscle, double volume) {
        this.date = date;
        this.muscle = muscle;
        this.volume = volume;
    }

    public String getDate() { return date; }
    public String getMuscle() { return muscle; }
    public double getVolume() { return volume; }
}

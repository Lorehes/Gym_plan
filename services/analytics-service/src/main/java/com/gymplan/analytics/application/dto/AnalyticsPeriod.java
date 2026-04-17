package com.gymplan.analytics.application.dto;

public enum AnalyticsPeriod {
    WEEK,
    MONTH;

    public String toEsDateMath() {
        return switch (this) {
            case WEEK -> "now-7d/d";
            case MONTH -> "now-30d/d";
        };
    }
}

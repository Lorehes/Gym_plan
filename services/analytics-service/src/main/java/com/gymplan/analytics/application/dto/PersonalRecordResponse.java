package com.gymplan.analytics.application.dto;

import java.time.Instant;

public class PersonalRecordResponse {

    private final String exerciseId;
    private final String exerciseName;
    private final double maxWeightKg;
    private final int maxReps;
    private final double estimated1RM;
    private final boolean isReliable;
    private final Instant achievedAt;

    public PersonalRecordResponse(String exerciseId, String exerciseName,
                                  double maxWeightKg, int maxReps,
                                  double estimated1RM, boolean isReliable,
                                  Instant achievedAt) {
        this.exerciseId = exerciseId;
        this.exerciseName = exerciseName;
        this.maxWeightKg = maxWeightKg;
        this.maxReps = maxReps;
        this.estimated1RM = estimated1RM;
        this.isReliable = isReliable;
        this.achievedAt = achievedAt;
    }

    public String getExerciseId() { return exerciseId; }
    public String getExerciseName() { return exerciseName; }
    public double getMaxWeightKg() { return maxWeightKg; }
    public int getMaxReps() { return maxReps; }
    public double getEstimated1RM() { return estimated1RM; }
    public boolean isReliable() { return isReliable; }
    public Instant getAchievedAt() { return achievedAt; }
}

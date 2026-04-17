package com.gymplan.analytics.application.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * workout.set.logged Kafka 이벤트 페이로드.
 * 명세: docs/architecture/kafka-events.md
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkoutSetLoggedEvent {

    private String eventType;
    private String sessionId;
    private String userId;
    private String exerciseId;
    private String exerciseName;
    private String muscleGroup;
    private Integer setNo;
    private Integer reps;
    private Double weightKg;
    private Double volume;
    private Boolean isSuccess;
    private Instant occurredAt;

    public WorkoutSetLoggedEvent() {}

    public String getEventType() { return eventType; }
    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getExerciseId() { return exerciseId; }
    public String getExerciseName() { return exerciseName; }
    public String getMuscleGroup() { return muscleGroup; }
    public Integer getSetNo() { return setNo; }
    public Integer getReps() { return reps; }
    public Double getWeightKg() { return weightKg; }
    public Double getVolume() { return volume; }
    public Boolean getIsSuccess() { return isSuccess; }
    public Instant getOccurredAt() { return occurredAt; }

    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setExerciseId(String exerciseId) { this.exerciseId = exerciseId; }
    public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }
    public void setMuscleGroup(String muscleGroup) { this.muscleGroup = muscleGroup; }
    public void setSetNo(Integer setNo) { this.setNo = setNo; }
    public void setReps(Integer reps) { this.reps = reps; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
    public void setVolume(Double volume) { this.volume = volume; }
    public void setIsSuccess(Boolean isSuccess) { this.isSuccess = isSuccess; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}

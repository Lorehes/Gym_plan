package com.gymplan.analytics.application.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * workout.session.completed Kafka 이벤트 페이로드.
 * 명세: docs/architecture/kafka-events.md
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkoutSessionCompletedEvent {

    private String eventType;
    private String sessionId;
    private String userId;
    private String planId;
    private String planName;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationSec;
    private Double totalVolume;
    private Integer totalSets;
    private List<String> muscleGroups;
    private Instant occurredAt;

    public WorkoutSessionCompletedEvent() {}

    public String getEventType() { return eventType; }
    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getPlanId() { return planId; }
    public String getPlanName() { return planName; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Long getDurationSec() { return durationSec; }
    public Double getTotalVolume() { return totalVolume; }
    public Integer getTotalSets() { return totalSets; }
    public List<String> getMuscleGroups() { return muscleGroups; }
    public Instant getOccurredAt() { return occurredAt; }

    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public void setPlanName(String planName) { this.planName = planName; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setDurationSec(Long durationSec) { this.durationSec = durationSec; }
    public void setTotalVolume(Double totalVolume) { this.totalVolume = totalVolume; }
    public void setTotalSets(Integer totalSets) { this.totalSets = totalSets; }
    public void setMuscleGroups(List<String> muscleGroups) { this.muscleGroups = muscleGroups; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}

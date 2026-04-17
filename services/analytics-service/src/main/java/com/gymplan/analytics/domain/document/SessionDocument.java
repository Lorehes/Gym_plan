package com.gymplan.analytics.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.List;

/**
 * ES 색인 단위: 운동 세션 완료 이벤트.
 *
 * 인덱스명: gymplan-sessions-{YYYY.MM} (월별 롤오버)
 * 문서 ID: sessionId
 * 멱등성: 동일 sessionId로 save 시 upsert → 중복 없음
 */
@Document(indexName = "gymplan-sessions", createIndex = false)
public class SessionDocument {

    @Id
    private String sessionId;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Keyword)
    private String planId;

    @Field(type = FieldType.Text)
    private String planName;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant startedAt;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant completedAt;

    @Field(type = FieldType.Long)
    private Long durationSec;

    @Field(type = FieldType.Double)
    private Double totalVolume;

    @Field(type = FieldType.Integer)
    private Integer totalSets;

    @Field(type = FieldType.Keyword)
    private List<String> muscleGroups;

    public SessionDocument() {}

    private SessionDocument(Builder builder) {
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.planId = builder.planId;
        this.planName = builder.planName;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.durationSec = builder.durationSec;
        this.totalVolume = builder.totalVolume;
        this.totalSets = builder.totalSets;
        this.muscleGroups = builder.muscleGroups;
    }

    public static Builder builder() {
        return new Builder();
    }

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

    public static final class Builder {
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

        public Builder sessionId(String v) { this.sessionId = v; return this; }
        public Builder userId(String v) { this.userId = v; return this; }
        public Builder planId(String v) { this.planId = v; return this; }
        public Builder planName(String v) { this.planName = v; return this; }
        public Builder startedAt(Instant v) { this.startedAt = v; return this; }
        public Builder completedAt(Instant v) { this.completedAt = v; return this; }
        public Builder durationSec(Long v) { this.durationSec = v; return this; }
        public Builder totalVolume(Double v) { this.totalVolume = v; return this; }
        public Builder totalSets(Integer v) { this.totalSets = v; return this; }
        public Builder muscleGroups(List<String> v) { this.muscleGroups = v; return this; }
        public SessionDocument build() { return new SessionDocument(this); }
    }
}

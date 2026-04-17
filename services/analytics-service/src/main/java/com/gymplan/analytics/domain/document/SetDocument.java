package com.gymplan.analytics.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

/**
 * ES 색인 단위: 세트 기록 이벤트.
 *
 * 인덱스명: gymplan-sets-{YYYY.MM} (월별 롤오버)
 * 문서 ID: {sessionId}-{setNo}
 * 멱등성: 동일 ID로 save 시 upsert → 중복 없음
 */
@Document(indexName = "gymplan-sets", createIndex = false)
public class SetDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String sessionId;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Keyword)
    private String exerciseId;

    @Field(type = FieldType.Keyword)
    private String exerciseName;

    @Field(type = FieldType.Keyword)
    private String muscleGroup;

    @Field(type = FieldType.Integer)
    private Integer setNo;

    @Field(type = FieldType.Integer)
    private Integer reps;

    @Field(type = FieldType.Double)
    private Double weightKg;

    @Field(type = FieldType.Double)
    private Double volume;

    @Field(type = FieldType.Double)
    private Double estimated1RM;

    @Field(type = FieldType.Boolean)
    private Boolean isSuccess;

    @Field(type = FieldType.Boolean)
    private Boolean isReliable;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant occurredAt;

    public SetDocument() {}

    private SetDocument(Builder builder) {
        this.id = builder.id;
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.exerciseId = builder.exerciseId;
        this.exerciseName = builder.exerciseName;
        this.muscleGroup = builder.muscleGroup;
        this.setNo = builder.setNo;
        this.reps = builder.reps;
        this.weightKg = builder.weightKg;
        this.volume = builder.volume;
        this.estimated1RM = builder.estimated1RM;
        this.isSuccess = builder.isSuccess;
        this.isReliable = builder.isReliable;
        this.occurredAt = builder.occurredAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getExerciseId() { return exerciseId; }
    public String getExerciseName() { return exerciseName; }
    public String getMuscleGroup() { return muscleGroup; }
    public Integer getSetNo() { return setNo; }
    public Integer getReps() { return reps; }
    public Double getWeightKg() { return weightKg; }
    public Double getVolume() { return volume; }
    public Double getEstimated1RM() { return estimated1RM; }
    public Boolean getIsSuccess() { return isSuccess; }
    public Boolean getIsReliable() { return isReliable; }
    public Instant getOccurredAt() { return occurredAt; }

    public static final class Builder {
        private String id;
        private String sessionId;
        private String userId;
        private String exerciseId;
        private String exerciseName;
        private String muscleGroup;
        private Integer setNo;
        private Integer reps;
        private Double weightKg;
        private Double volume;
        private Double estimated1RM;
        private Boolean isSuccess;
        private Boolean isReliable;
        private Instant occurredAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder sessionId(String v) { this.sessionId = v; return this; }
        public Builder userId(String v) { this.userId = v; return this; }
        public Builder exerciseId(String v) { this.exerciseId = v; return this; }
        public Builder exerciseName(String v) { this.exerciseName = v; return this; }
        public Builder muscleGroup(String v) { this.muscleGroup = v; return this; }
        public Builder setNo(Integer v) { this.setNo = v; return this; }
        public Builder reps(Integer v) { this.reps = v; return this; }
        public Builder weightKg(Double v) { this.weightKg = v; return this; }
        public Builder volume(Double v) { this.volume = v; return this; }
        public Builder estimated1RM(Double v) { this.estimated1RM = v; return this; }
        public Builder isSuccess(Boolean v) { this.isSuccess = v; return this; }
        public Builder isReliable(Boolean v) { this.isReliable = v; return this; }
        public Builder occurredAt(Instant v) { this.occurredAt = v; return this; }
        public SetDocument build() { return new SetDocument(this); }
    }
}

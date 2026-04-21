package com.gymplan.analytics.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

/**
 * ES 색인 단위: 종목별 개인 기록.
 *
 * 인덱스명: gymplan-personal-records (롤오버 없음, 고정)
 * 문서 ID: {userId}-{exerciseId}  → 유저×종목 조합당 1건
 * 갱신 조건: estimated1RM이 기존보다 클 때만 upsert
 */
@Document(indexName = "gymplan-personal-records", createIndex = true)
public class PersonalRecordDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Keyword)
    private String exerciseId;

    @Field(type = FieldType.Keyword)
    private String exerciseName;

    @Field(type = FieldType.Double)
    private Double maxWeightKg;

    @Field(type = FieldType.Integer)
    private Integer maxReps;

    @Field(type = FieldType.Double)
    private Double estimated1RM;

    @Field(type = FieldType.Boolean)
    private Boolean isReliable;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant achievedAt;

    public PersonalRecordDocument() {}

    private PersonalRecordDocument(Builder builder) {
        this.id = builder.id;
        this.userId = builder.userId;
        this.exerciseId = builder.exerciseId;
        this.exerciseName = builder.exerciseName;
        this.maxWeightKg = builder.maxWeightKg;
        this.maxReps = builder.maxReps;
        this.estimated1RM = builder.estimated1RM;
        this.isReliable = builder.isReliable;
        this.achievedAt = builder.achievedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getExerciseId() { return exerciseId; }
    public String getExerciseName() { return exerciseName; }
    public Double getMaxWeightKg() { return maxWeightKg; }
    public Integer getMaxReps() { return maxReps; }
    public Double getEstimated1RM() { return estimated1RM; }
    public Boolean getIsReliable() { return isReliable; }
    public Instant getAchievedAt() { return achievedAt; }

    public static final class Builder {
        private String id;
        private String userId;
        private String exerciseId;
        private String exerciseName;
        private Double maxWeightKg;
        private Integer maxReps;
        private Double estimated1RM;
        private Boolean isReliable;
        private Instant achievedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder userId(String v) { this.userId = v; return this; }
        public Builder exerciseId(String v) { this.exerciseId = v; return this; }
        public Builder exerciseName(String v) { this.exerciseName = v; return this; }
        public Builder maxWeightKg(Double v) { this.maxWeightKg = v; return this; }
        public Builder maxReps(Integer v) { this.maxReps = v; return this; }
        public Builder estimated1RM(Double v) { this.estimated1RM = v; return this; }
        public Builder isReliable(Boolean v) { this.isReliable = v; return this; }
        public Builder achievedAt(Instant v) { this.achievedAt = v; return this; }
        public PersonalRecordDocument build() { return new PersonalRecordDocument(this); }
    }
}

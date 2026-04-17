package com.gymplan.analytics.application.service;

import com.gymplan.analytics.domain.document.PersonalRecordDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 종목별 개인 기록(PR) 조회 및 갱신.
 *
 * 갱신 조건 (모두 충족해야 함):
 *   1. isSuccess = true
 *   2. isReliable = true (reps ≤ 30)
 *   3. 신규 estimated1RM > 기존 PR estimated1RM (또는 기존 기록 없음)
 */
@Service
public class PersonalRecordService {

    private static final Logger log = LoggerFactory.getLogger(PersonalRecordService.class);
    private static final IndexCoordinates PR_INDEX = IndexCoordinates.of("gymplan-personal-records");

    private final ElasticsearchOperations esOps;

    public PersonalRecordService(ElasticsearchOperations esOps) {
        this.esOps = esOps;
    }

    public void checkAndUpdate(String userId, String exerciseId, String exerciseName,
                               double weightKg, int reps, double estimated1RM,
                               boolean isSuccess, boolean isReliable, Instant occurredAt) {
        if (!isSuccess || !isReliable) {
            return;
        }

        String docId = userId + "-" + exerciseId;
        PersonalRecordDocument existing = esOps.get(docId, PersonalRecordDocument.class, PR_INDEX);

        if (existing != null && existing.getEstimated1RM() >= estimated1RM) {
            return;
        }

        PersonalRecordDocument updated = PersonalRecordDocument.builder()
                .id(docId)
                .userId(userId)
                .exerciseId(exerciseId)
                .exerciseName(exerciseName)
                .maxWeightKg(weightKg)
                .maxReps(reps)
                .estimated1RM(estimated1RM)
                .isReliable(isReliable)
                .achievedAt(occurredAt)
                .build();

        esOps.index(
                new IndexQueryBuilder().withId(docId).withObject(updated).build(),
                PR_INDEX
        );

        log.info("PR updated: userId={}, exerciseId={}, estimated1RM={}", userId, exerciseId, estimated1RM);
    }
}

package com.gymplan.analytics.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ScriptType;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * 종목별 개인 기록(PR) 원자적 갱신.
 *
 * 갱신 조건 (모두 충족해야 함):
 *   1. isSuccess = true
 *   2. isReliable = true (reps ≤ 30)
 *   3. 신규 estimated1RM > 기존 PR (Painless Script로 ES에서 원자 비교)
 *
 * 동시성 전략: ES UpdateQuery + Painless Script 조건부 upsert
 *   - Read-then-Write race condition 제거
 *   - "현재값보다 클 때만 갱신"을 ES 레벨에서 원자적으로 수행
 *   - ctx.op = 'none'으로 조건 불충족 시 불필요한 쓰기 방지
 *   - retryOnConflict(3)으로 버전 충돌 자동 재시도
 *   - 문서 없으면 upsert doc으로 직접 생성 (scriptedUpsert=false)
 */
@Service
public class PersonalRecordService {

    private static final Logger log = LoggerFactory.getLogger(PersonalRecordService.class);
    private static final IndexCoordinates PR_INDEX = IndexCoordinates.of("gymplan-personal-records");

    private static final String UPDATE_SCRIPT =
            "if (ctx._source.estimated1RM == null || ctx._source.estimated1RM < params.new1RM) {" +
            "  ctx._source.estimated1RM = params.new1RM;" +
            "  ctx._source.maxWeightKg  = params.weightKg;" +
            "  ctx._source.maxReps      = params.reps;" +
            "  ctx._source.isReliable   = params.isReliable;" +
            "  ctx._source.achievedAt   = params.achievedAt;" +
            "} else {" +
            "  ctx.op = 'none';" +
            "}";

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

        Map<String, Object> scriptParams = Map.of(
                "new1RM",     estimated1RM,
                "weightKg",   weightKg,
                "reps",       reps,
                "isReliable", isReliable,
                "achievedAt", occurredAt.toString()
        );

        // 문서 없을 때 사용할 초기 upsert 문서
        Document upsertDoc = Document.from(Map.of(
                "userId",       userId,
                "exerciseId",   exerciseId,
                "exerciseName", exerciseName,
                "maxWeightKg",  weightKg,
                "maxReps",      reps,
                "estimated1RM", estimated1RM,
                "isReliable",   isReliable,
                "achievedAt",   occurredAt.toString()
        ));

        UpdateQuery updateQuery = UpdateQuery.builder(docId)
                .withScript(UPDATE_SCRIPT)
                .withScriptType(ScriptType.INLINE)
                .withLang("painless")
                .withParams(scriptParams)
                .withUpsert(upsertDoc)
                .withRetryOnConflict(3)
                .build();

        esOps.update(updateQuery, PR_INDEX);

        log.info("PR upsert: userId={}, exerciseId={}, estimated1RM={}", userId, exerciseId, estimated1RM);
    }
}

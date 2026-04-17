package com.gymplan.analytics.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymplan.analytics.application.event.WorkoutSetLoggedEvent;
import com.gymplan.analytics.application.service.EpleyCalculator;
import com.gymplan.analytics.application.service.PersonalRecordService;
import com.gymplan.analytics.domain.document.SetDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * workout.set.logged 이벤트 소비자.
 *
 * 처리 흐름:
 *   1. JSON 역직렬화 (실패 → DLQ 즉시)
 *   2. Epley 1RM 계산 (weight × (1 + reps/30.0), 소수점 1자리)
 *   3. SetDocument ES 색인 (upsert)
 *   4. PersonalRecordService.checkAndUpdate — 조건 충족 시 PR 갱신
 *   5. offset 수동 커밋
 */
@Component
public class SetLoggedConsumer {

    private static final Logger log = LoggerFactory.getLogger(SetLoggedConsumer.class);
    private static final DateTimeFormatter INDEX_MONTH_FMT =
            DateTimeFormatter.ofPattern("yyyy.MM").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final ElasticsearchOperations esOps;
    private final PersonalRecordService personalRecordService;

    public SetLoggedConsumer(ObjectMapper objectMapper,
                             ElasticsearchOperations esOps,
                             PersonalRecordService personalRecordService) {
        this.objectMapper = objectMapper;
        this.esOps = esOps;
        this.personalRecordService = personalRecordService;
    }

    @KafkaListener(topics = "workout.set.logged", groupId = "gymplan-analytics-service")
    public void consume(String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        Acknowledgment ack) {
        WorkoutSetLoggedEvent event;
        try {
            event = objectMapper.readValue(payload, WorkoutSetLoggedEvent.class);
        } catch (Exception e) {
            log.error("[{}] 역직렬화 실패 — DLQ로 라우팅: {}", topic, e.getMessage());
            throw new IllegalArgumentException("Deserialization failed for topic=" + topic, e);
        }

        double estimated1RM = EpleyCalculator.calculate(event.getWeightKg(), event.getReps());
        boolean isReliable = EpleyCalculator.isReliable(event.getReps());

        String docId = event.getSessionId() + "-" + event.getSetNo();
        String indexName = "gymplan-sets-" + INDEX_MONTH_FMT.format(event.getOccurredAt());

        SetDocument doc = SetDocument.builder()
                .id(docId)
                .sessionId(event.getSessionId())
                .userId(event.getUserId())
                .exerciseId(event.getExerciseId())
                .exerciseName(event.getExerciseName())
                .muscleGroup(event.getMuscleGroup())
                .setNo(event.getSetNo())
                .reps(event.getReps())
                .weightKg(event.getWeightKg())
                .volume(event.getVolume())
                .estimated1RM(estimated1RM)
                .isSuccess(event.getIsSuccess())
                .isReliable(isReliable)
                .occurredAt(event.getOccurredAt())
                .build();

        esOps.index(
                new IndexQueryBuilder().withId(docId).withObject(doc).build(),
                IndexCoordinates.of(indexName)
        );

        personalRecordService.checkAndUpdate(
                event.getUserId(),
                event.getExerciseId(),
                event.getExerciseName(),
                event.getWeightKg(),
                event.getReps(),
                estimated1RM,
                Boolean.TRUE.equals(event.getIsSuccess()),
                isReliable,
                event.getOccurredAt()
        );

        log.info("[{}] 세트 색인 완료: docId={}, estimated1RM={}, isReliable={}",
                topic, docId, estimated1RM, isReliable);

        ack.acknowledge();
    }
}

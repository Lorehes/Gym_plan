package com.gymplan.analytics.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymplan.analytics.application.event.WorkoutSessionCompletedEvent;
import com.gymplan.analytics.domain.document.SessionDocument;
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
 * workout.session.completed 이벤트 소비자.
 *
 * 처리 흐름:
 *   1. JSON 역직렬화 (실패 → IllegalArgumentException → DLQ 즉시)
 *   2. SessionDocument 생성 및 ES 색인 (monthlyIndex로 upsert)
 *   3. 성공 시 offset 수동 커밋
 *   처리 실패 시 DefaultErrorHandler(지수 백오프 3회 재시도 → DLQ)
 */
@Component
public class SessionCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(SessionCompletedConsumer.class);
    private static final DateTimeFormatter INDEX_MONTH_FMT =
            DateTimeFormatter.ofPattern("yyyy.MM").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final ElasticsearchOperations esOps;

    public SessionCompletedConsumer(ObjectMapper objectMapper, ElasticsearchOperations esOps) {
        this.objectMapper = objectMapper;
        this.esOps = esOps;
    }

    @KafkaListener(topics = "workout.session.completed", groupId = "gymplan-analytics-service")
    public void consume(String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        Acknowledgment ack) {
        WorkoutSessionCompletedEvent event;
        try {
            event = objectMapper.readValue(payload, WorkoutSessionCompletedEvent.class);
        } catch (Exception e) {
            log.error("[{}] 역직렬화 실패 — DLQ로 라우팅: {}", topic, e.getMessage());
            throw new IllegalArgumentException("Deserialization failed for topic=" + topic, e);
        }

        String indexName = "gymplan-sessions-" + INDEX_MONTH_FMT.format(event.getCompletedAt());

        SessionDocument doc = SessionDocument.builder()
                .sessionId(event.getSessionId())
                .userId(event.getUserId())
                .planId(event.getPlanId())
                .planName(event.getPlanName())
                .startedAt(event.getStartedAt())
                .completedAt(event.getCompletedAt())
                .durationSec(event.getDurationSec())
                .totalVolume(event.getTotalVolume())
                .totalSets(event.getTotalSets())
                .muscleGroups(event.getMuscleGroups())
                .build();

        esOps.index(
                new IndexQueryBuilder()
                        .withId(event.getSessionId())
                        .withObject(doc)
                        .build(),
                IndexCoordinates.of(indexName)
        );

        log.info("[{}] 세션 색인 완료: sessionId={}, userId={}, index={}",
                topic, event.getSessionId(), event.getUserId(), indexName);

        ack.acknowledge();
    }
}

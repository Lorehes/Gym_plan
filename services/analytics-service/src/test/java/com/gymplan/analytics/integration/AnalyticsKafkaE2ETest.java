package com.gymplan.analytics.integration;

import com.gymplan.analytics.application.service.EpleyCalculator;
import com.gymplan.analytics.domain.document.PersonalRecordDocument;
import com.gymplan.analytics.domain.document.SessionDocument;
import com.gymplan.analytics.domain.document.SetDocument;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 2 E2E — 시나리오 B
 *
 * Kafka 이벤트 소비 → Elasticsearch 색인 → 통계 API 응답 검증
 *
 * 시나리오:
 *   B-1: workout.set.logged → gymplan-sets-* 색인 + PR 갱신
 *   B-2: workout.session.completed → gymplan-sessions-* 색인 → GET /summary 응답
 *   B-3: 중복 이벤트 upsert → ES 문서 1건 유지 (멱등성)
 *   B-4: PR API — 높은 estimated1RM만 유지 검증
 *
 * 참조: docs/specs/analytics-service.md §TC-005, TC-006, TC-010
 *       docs/architecture/kafka-events.md
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsKafkaE2ETest extends AbstractAnalyticsIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ElasticsearchOperations esOps;

    private KafkaProducer<String, String> producer;
    private final String userId = "1";

    @BeforeEach
    void setUpProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        producer = new KafkaProducer<>(props);
    }

    @AfterEach
    void tearDownProducer() {
        if (producer != null) producer.close();
    }

    // ──────────────────────────────────────────────────────────────────────
    // E2E-B-1: workout.set.logged → ES 색인 + PR 갱신
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("E2E-B-1: workout.set.logged → gymplan-sets-* ES 색인 + PR 갱신 검증 (TC-005, TC-006)")
    void e2eB1_setLogged_esIndexing_and_prUpdate() throws Exception {
        String sessionId = "e2e-" + UUID.randomUUID();
        Instant now = Instant.now();

        // Epley 기댓값: 80 × (1 + 8/30) ≈ 101.3
        double expected1RM = EpleyCalculator.calculate(80.0, 8);

        String payload = setLoggedPayload(sessionId, userId, "10", "벤치프레스", "CHEST",
                1, 8, 80.0, 640.0, true, now.toString());

        producer.send(new ProducerRecord<>("workout.set.logged", sessionId, payload));
        producer.flush();

        // ── ES 세트 문서 색인 대기 ──
        String docId = sessionId + "-1";
        String setIndex = monthlyIndex("gymplan-sets", now);
        assertThat(waitForDocument(docId, SetDocument.class, setIndex, 15_000))
                .as("workout.set.logged 이벤트가 ES에 색인되어야 함").isTrue();

        // ── 세트 문서 검증 ──
        SetDocument setDoc = esOps.get(docId, SetDocument.class, IndexCoordinates.of(setIndex));
        assertThat(setDoc).isNotNull();
        assertThat(setDoc.getEstimated1RM()).isCloseTo(expected1RM, within(0.05));
        assertThat(setDoc.getIsReliable()).isTrue();
        assertThat(setDoc.getMuscleGroup()).isEqualTo("CHEST");

        // ── PR 문서 생성 검증 ──
        String prDocId = userId + "-10";
        assertThat(waitForDocument(prDocId, PersonalRecordDocument.class, "gymplan-personal-records", 5_000))
                .as("개인 기록(PR) 문서가 생성되어야 함").isTrue();

        PersonalRecordDocument prDoc = esOps.get(prDocId, PersonalRecordDocument.class,
                IndexCoordinates.of("gymplan-personal-records"));
        assertThat(prDoc).isNotNull();
        assertThat(prDoc.getEstimated1RM()).isCloseTo(101.3, within(0.1));
        assertThat(prDoc.getIsReliable()).isTrue();
    }

    // ──────────────────────────────────────────────────────────────────────
    // E2E-B-2: workout.session.completed → ES 색인 → 통계 API
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("E2E-B-2: workout.session.completed → ES 색인 → GET /summary?period=WEEK 응답 검증")
    void e2eB2_sessionCompleted_esIndexing_and_summaryApi() throws Exception {
        String sessionId = "e2e-" + UUID.randomUUID();
        Instant now = Instant.now();

        String payload = sessionCompletedPayload(sessionId, userId, "12", "가슴/삼두 루틴",
                now.minusSeconds(4200).toString(), now.toString(), 4200, 3840.0, 16,
                "[\"CHEST\",\"ARMS\"]");

        producer.send(new ProducerRecord<>("workout.session.completed", sessionId, payload));
        producer.flush();

        // ── ES 세션 문서 색인 대기 ──
        String sessionIndex = monthlyIndex("gymplan-sessions", now);
        assertThat(waitForDocument(sessionId, SessionDocument.class, sessionIndex, 15_000))
                .as("workout.session.completed 이벤트가 ES에 색인되어야 함").isTrue();

        // ── 세션 문서 내용 검증 ──
        SessionDocument sessionDoc = esOps.get(sessionId, SessionDocument.class,
                IndexCoordinates.of(sessionIndex));
        assertThat(sessionDoc).isNotNull();
        assertThat(sessionDoc.getTotalVolume()).isEqualTo(3840.0);
        assertThat(sessionDoc.getTotalSets()).isEqualTo(16);
        assertThat(sessionDoc.getMuscleGroups()).containsExactlyInAnyOrder("CHEST", "ARMS");
        assertThat(sessionDoc.getPlanName()).isEqualTo("가슴/삼두 루틴");

        // ── 인덱스 강제 refresh 후 통계 API 호출 ──
        esOps.indexOps(IndexCoordinates.of(sessionIndex)).refresh();

        mockMvc.perform(get("/api/v1/analytics/summary")
                        .header("X-User-Id", userId)
                        .param("period", "WEEK")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalSessions").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.totalVolume").value(greaterThanOrEqualTo(3840.0)));
    }

    // ──────────────────────────────────────────────────────────────────────
    // E2E-B-3: 중복 이벤트 → upsert → 문서 1건 (멱등성)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("E2E-B-3: 동일 sessionId 이벤트 2회 발행 → upsert → 문서 1건 (TC-010)")
    void e2eB3_idempotency_duplicateEventResultsInSingleDocument() throws Exception {
        String sessionId = "e2e-dup-" + UUID.randomUUID();
        Instant now = Instant.now();

        String payload = sessionCompletedPayload(sessionId, userId, null, null,
                now.minusSeconds(1800).toString(), now.toString(), 1800, 1000.0, 5,
                "[\"BACK\"]");

        // 동일 이벤트 2회 발행 (At-Least-Once 재시도 시뮬레이션)
        producer.send(new ProducerRecord<>("workout.session.completed", sessionId, payload));
        producer.send(new ProducerRecord<>("workout.session.completed", sessionId, payload));
        producer.flush();

        String sessionIndex = monthlyIndex("gymplan-sessions", now);
        assertThat(waitForDocument(sessionId, SessionDocument.class, sessionIndex, 15_000)).isTrue();

        Thread.sleep(3_000); // 두 번째 이벤트 처리 대기
        esOps.indexOps(IndexCoordinates.of(sessionIndex)).refresh();

        SearchHits<SessionDocument> hits = esOps.search(
                NativeQuery.builder()
                        .withQuery(q -> q.ids(ids -> ids.values(sessionId)))
                        .build(),
                SessionDocument.class,
                IndexCoordinates.of(sessionIndex)
        );
        assertThat(hits.getTotalHits())
                .as("중복 이벤트는 upsert로 처리되어 문서 1건만 존재해야 함").isEqualTo(1L);
    }

    // ──────────────────────────────────────────────────────────────────────
    // E2E-B-4: 개인 기록 API — 높은 1RM만 갱신
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("E2E-B-4: 낮은 estimated1RM 이벤트는 PR 갱신 안 됨 → GET /personal-records 검증")
    void e2eB4_personalRecordApi_onlyHigherPrIsPreserved() throws Exception {
        String sessionId = "e2e-pr-" + UUID.randomUUID();
        Instant now = Instant.now();

        // 첫 번째 세트: 80kg × 8rep → estimated1RM ≈ 101.3 (높은 기록)
        producer.send(new ProducerRecord<>("workout.set.logged", sessionId,
                setLoggedPayload(sessionId, userId, "10", "벤치프레스", "CHEST",
                        1, 8, 80.0, 640.0, true, now.toString())));

        // 두 번째 세트: 70kg × 5rep → estimated1RM ≈ 81.7 (낮은 기록 — PR 갱신 안 됨)
        producer.send(new ProducerRecord<>("workout.set.logged", sessionId,
                setLoggedPayload(sessionId, userId, "10", "벤치프레스", "CHEST",
                        2, 5, 70.0, 350.0, true, now.plusSeconds(300).toString())));
        producer.flush();

        String prDocId = userId + "-10";
        assertThat(waitForDocument(prDocId, PersonalRecordDocument.class, "gymplan-personal-records", 15_000))
                .isTrue();
        Thread.sleep(3_000);
        esOps.indexOps(IndexCoordinates.of("gymplan-personal-records")).refresh();

        // PR API 응답 검증 — 높은 estimated1RM (101.3) 유지
        mockMvc.perform(get("/api/v1/analytics/personal-records")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].exerciseName").value("벤치프레스"))
                .andExpect(jsonPath("$.data[0].estimated1RM").value(greaterThanOrEqualTo(100.0)));
    }

    // ──────────────────────────────────────────────────────────────────────
    // 헬퍼 — JSON 페이로드 빌더
    // ──────────────────────────────────────────────────────────────────────

    private String setLoggedPayload(
            String sessionId, String userId, String exerciseId, String exerciseName,
            String muscleGroup, int setNo, int reps, double weightKg, double volume,
            boolean isSuccess, String occurredAt) {
        return String.format("""
            {
              "eventType": "WORKOUT_SET_LOGGED",
              "sessionId": "%s",
              "userId": "%s",
              "exerciseId": "%s",
              "exerciseName": "%s",
              "muscleGroup": "%s",
              "setNo": %d,
              "reps": %d,
              "weightKg": %.1f,
              "volume": %.1f,
              "isSuccess": %s,
              "occurredAt": "%s"
            }""",
                sessionId, userId, exerciseId, exerciseName, muscleGroup,
                setNo, reps, weightKg, volume, isSuccess, occurredAt);
    }

    private String sessionCompletedPayload(
            String sessionId, String userId, String planId, String planName,
            String startedAt, String completedAt, long durationSec, double totalVolume,
            int totalSets, String muscleGroupsJson) {
        String planIdField = planId != null ? "\"" + planId + "\"" : "null";
        String planNameField = planName != null ? "\"" + planName + "\"" : "null";
        return String.format("""
            {
              "eventType": "WORKOUT_SESSION_COMPLETED",
              "sessionId": "%s",
              "userId": "%s",
              "planId": %s,
              "planName": %s,
              "startedAt": "%s",
              "completedAt": "%s",
              "durationSec": %d,
              "totalVolume": %.1f,
              "totalSets": %d,
              "muscleGroups": %s,
              "occurredAt": "%s"
            }""",
                sessionId, userId, planIdField, planNameField,
                startedAt, completedAt, durationSec, totalVolume, totalSets,
                muscleGroupsJson, completedAt);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 헬퍼 — ES 문서 폴링
    // ──────────────────────────────────────────────────────────────────────

    private <T> boolean waitForDocument(String id, Class<T> clazz, String indexName, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                esOps.indexOps(IndexCoordinates.of(indexName)).refresh();
                if (esOps.get(id, clazz, IndexCoordinates.of(indexName)) != null) return true;
            } catch (Exception ignored) {
                // 인덱스 미존재 등 과도기 예외 무시
            }
            Thread.sleep(500);
        }
        return false;
    }

    /**
     * ES 월별 롤오버 인덱스명 생성.
     * {@code SessionCompletedConsumer}와 동일한 포맷: "gymplan-sessions-2026.04"
     */
    private String monthlyIndex(String prefix, Instant instant) {
        String ym = instant.toString().substring(0, 7); // "2026-04"
        return prefix + "-" + ym.replace("-", ".");     // "gymplan-sessions-2026.04"
    }
}

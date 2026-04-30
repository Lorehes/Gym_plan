package com.gymplan.e2e

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * 시나리오 W — 운동 실행 + Kafka 비동기 흐름 (모바일 시나리오)
 *
 * 명세: docs/specs/e2e-scenario-workout.md
 *
 * ⚠️ 인프라 미준비로 전체 @Disabled 처리.
 * 활성화 조건 — docker-compose.e2e.yml 에 다음 추가:
 *   - MongoDB, Kafka(+ZK 또는 KRaft)
 *   - workout-service, analytics-service, notification-service 컨테이너
 *   - api-gateway 의 ROUTE_WORKOUT/ANALYTICS/NOTIFICATION 환경변수 실제값으로 교체
 *
 * 검증 항목:
 *   E2E-W-01: POST /sessions → 201 IN_PROGRESS
 *   E2E-W-02: POST /sessions/{id}/sets × 5 → Kafka workout.set.logged 5건
 *   E2E-W-03: POST /sessions/{id}/complete → Kafka workout.session.completed 1건
 *   E2E-W-04: analytics-service eventual consistency — PR 통계 반영 (폴링)
 *   E2E-W-05: notification-service SSE timer-start 이벤트 수신
 */
@Tag("e2e")
@Disabled("인프라 확장 필요 — docs/specs/e2e-scenario-workout.md '활성화 체크리스트' 참조")
@DisplayName("[E2E-W] 운동 실행 + Kafka 비동기 흐름")
class ScenarioWorkoutTest : AbstractE2ETest() {
    @Nested
    @DisplayName("E2E-W-01: 세션 시작 → 201 IN_PROGRESS")
    inner class StartSession {
        @Test
        fun `POST sessions 요청 시 201과 IN_PROGRESS 상태가 반환된다`() {
            // TODO[E2E-W-01]: workout-service 컨테이너 활성화 후 구현.
            //   POST /api/v1/sessions {planId, planName} → 201
            //   응답: sessionId, startedAt, status="IN_PROGRESS"
            //
            // 차단 사유: workout-service 가 docker-compose.e2e.yml 에 없음.
            //   (api-gateway 의 ROUTE_WORKOUT_SERVICE_URI = http://localhost:8084 더미값)
        }
    }

    @Nested
    @DisplayName("E2E-W-02: 세트 5회 기록 → Kafka workout.set.logged 5건")
    inner class LogFiveSets {
        @Test
        fun `5세트 POST 후 Kafka 토픽에 5개 이벤트가 발행된다`() {
            // TODO[E2E-W-02]: Kafka KafkaConsumer 또는 testcontainers KafkaContainer 연동.
            //   1. 세션 시작 (E2E-W-01 헬퍼)
            //   2. POST /api/v1/sessions/{id}/sets × 5
            //   3. e2e-test-consumer 그룹으로 workout.set.logged 토픽 polling
            //   4. assertThat(records).hasSize(5)
            //
            // 차단 사유: Kafka 컨테이너 부재 + workout-service 부재.
        }
    }

    @Nested
    @DisplayName("E2E-W-03: 세션 완료 → workout.session.completed 발행")
    inner class CompleteSession {
        @Test
        fun `complete 후 session_completed 이벤트 1건이 발행된다`() {
            // TODO[E2E-W-03]: Kafka workout.session.completed 토픽 검증.
            //   - 페이로드 필드 확인: sessionId, userId, totalVolume, totalSets, exercises[]
        }
    }

    @Nested
    @DisplayName("E2E-W-04: analytics ES 인덱싱 (eventually consistent)")
    inner class AnalyticsEventualConsistency {
        @Test
        fun `세션 완료 후 일정 시간 내 PR 데이터가 ES에 반영된다`() {
            // TODO[E2E-W-04]: 폴링 기반 검증.
            //   val deadline = now + 15s
            //   while (now < deadline) {
            //     GET /api/v1/analytics/personal-records
            //     if (응답에 기대 PR 포함) return
            //     sleep 500ms
            //   }
            //   fail("PR 미반영")
            //
            // 차단 사유: analytics-service 컨테이너 부재.
        }
    }

    @Nested
    @DisplayName("E2E-W-05: notification SSE timer-start 수신")
    inner class TimerSseReceive {
        @Test
        fun `세트 기록 직후 SSE timer-start 이벤트가 도착한다`() {
            // TODO[E2E-W-05]: SSE 클라이언트 (okhttp-sse 또는 URLConnection 스트리밍) 로
            //   GET /api/v1/notifications/timer/stream?sessionId={id} 구독.
            //   세트 기록 트리거 후 5초 내에 'event: timer-start' 라인 수신 확인.
            //
            // 차단 사유: notification-service 컨테이너 부재.
        }
    }
}

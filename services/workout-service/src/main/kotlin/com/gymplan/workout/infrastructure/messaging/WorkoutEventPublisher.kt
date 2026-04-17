package com.gymplan.workout.infrastructure.messaging

import com.gymplan.workout.application.event.WorkoutSessionCompletedEvent
import com.gymplan.workout.application.event.WorkoutSetLoggedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Kafka 이벤트 발행 컴포넌트.
 *
 * 핵심 원칙 (docs/architecture/kafka-events.md):
 *   - @Async: API 응답 후 비동기 발행 → 응답 시간에 포함하지 않음
 *   - Kafka 장애 시에도 API 응답은 성공을 반환 (try-catch로 예외 흡수)
 *   - 발행 실패는 로그로만 기록 (DLQ는 Kafka broker 설정으로 처리)
 *
 * 토픽:
 *   - workout.set.logged       → analytics-service
 *   - workout.session.completed → analytics-service, notification-service
 */
@Component
class WorkoutEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {
    private val log = LoggerFactory.getLogger(WorkoutEventPublisher::class.java)

    @Async
    fun publishSetLogged(event: WorkoutSetLoggedEvent) {
        kafkaTemplate.send(TOPIC_SET_LOGGED, event.sessionId, event)
            .whenComplete { _, ex ->
                if (ex != null) {
                    log.error("Kafka 발행 실패: WORKOUT_SET_LOGGED sessionId={}", event.sessionId, ex)
                } else {
                    log.info("Kafka 발행 완료: WORKOUT_SET_LOGGED sessionId={}", event.sessionId)
                }
            }
    }

    @Async
    fun publishSessionCompleted(event: WorkoutSessionCompletedEvent) {
        kafkaTemplate.send(TOPIC_SESSION_COMPLETED, event.sessionId, event)
            .whenComplete { _, ex ->
                if (ex != null) {
                    log.error("Kafka 발행 실패: WORKOUT_SESSION_COMPLETED sessionId={}", event.sessionId, ex)
                } else {
                    log.info("Kafka 발행 완료: WORKOUT_SESSION_COMPLETED sessionId={}", event.sessionId)
                }
            }
    }

    companion object {
        const val TOPIC_SET_LOGGED = "workout.set.logged"
        const val TOPIC_SESSION_COMPLETED = "workout.session.completed"
    }
}

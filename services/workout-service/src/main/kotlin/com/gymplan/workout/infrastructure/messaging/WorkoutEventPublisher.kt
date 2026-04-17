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
 *   - retries=3 소진 후 최종 실패 시 {topic}.dlq 토픽으로 이동
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
                    log.error("Kafka 발행 실패, DLQ로 이동: {} sessionId={}", TOPIC_SET_LOGGED, event.sessionId, ex)
                    publishToDlq(TOPIC_SET_LOGGED, event.sessionId, event)
                } else {
                    log.info("Kafka 발행 완료: {} sessionId={}", TOPIC_SET_LOGGED, event.sessionId)
                }
            }
    }

    @Async
    fun publishSessionCompleted(event: WorkoutSessionCompletedEvent) {
        kafkaTemplate.send(TOPIC_SESSION_COMPLETED, event.sessionId, event)
            .whenComplete { _, ex ->
                if (ex != null) {
                    log.error("Kafka 발행 실패, DLQ로 이동: {} sessionId={}", TOPIC_SESSION_COMPLETED, event.sessionId, ex)
                    publishToDlq(TOPIC_SESSION_COMPLETED, event.sessionId, event)
                } else {
                    log.info("Kafka 발행 완료: {} sessionId={}", TOPIC_SESSION_COMPLETED, event.sessionId)
                }
            }
    }

    private fun publishToDlq(
        originalTopic: String,
        key: String,
        event: Any,
    ) {
        try {
            kafkaTemplate.send("$originalTopic.dlq", key, event)
                .whenComplete { _, ex ->
                    if (ex != null) {
                        log.error("DLQ 발행도 실패 (이벤트 유실): {}.dlq key={}", originalTopic, key, ex)
                    } else {
                        log.warn("DLQ 발행 완료: {}.dlq key={}", originalTopic, key)
                    }
                }
        } catch (e: Exception) {
            log.error("DLQ 발행 예외 (이벤트 유실): {}.dlq key={}", originalTopic, key, e)
        }
    }

    companion object {
        const val TOPIC_SET_LOGGED = "workout.set.logged"
        const val TOPIC_SESSION_COMPLETED = "workout.session.completed"
    }
}

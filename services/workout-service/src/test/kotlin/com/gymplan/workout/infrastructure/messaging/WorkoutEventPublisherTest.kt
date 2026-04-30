package com.gymplan.workout.infrastructure.messaging

import com.gymplan.workout.application.event.WorkoutSetLoggedEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * WorkoutEventPublisher 단위 테스트.
 *
 * TC-11 관련: retries 소진 후 DLQ 이동 동작 검증.
 * @Async는 Spring 컨텍스트 없이 일반 메서드로 동작하므로 동기적으로 검증 가능.
 */
class WorkoutEventPublisherTest {
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    private lateinit var publisher: WorkoutEventPublisher

    private val event =
        WorkoutSetLoggedEvent(
            sessionId = "session123",
            userId = "1",
            exerciseId = "10",
            exerciseName = "벤치프레스",
            muscleGroup = "CHEST",
            setNo = 1,
            reps = 10,
            weightKg = 70.0,
            volume = 700.0,
            isSuccess = true,
            occurredAt = Instant.now(),
        )

    @BeforeEach
    fun setUp() {
        kafkaTemplate = mock()
        publisher = WorkoutEventPublisher(kafkaTemplate)
    }

    @Test
    @DisplayName("TC-11: Kafka 발행 성공 → DLQ 발행 없음")
    fun `발행 성공 시 DLQ 호출 안 함`() {
        val successFuture = CompletableFuture.completedFuture(mock<SendResult<String, Any>>())
        whenever(kafkaTemplate.send(WorkoutEventPublisher.TOPIC_SET_LOGGED, event.sessionId, event))
            .thenReturn(successFuture)

        publisher.publishSetLogged(event)

        verify(kafkaTemplate, never()).send(eq("${WorkoutEventPublisher.TOPIC_SET_LOGGED}.dlq"), any(), any())
    }

    @Test
    @DisplayName("TC-11: Kafka 발행 실패(retries 소진) → DLQ 토픽으로 이동")
    fun `발행 실패 시 DLQ 토픽에 발행`() {
        val failedFuture =
            CompletableFuture.failedFuture<SendResult<String, Any>>(
                RuntimeException("Kafka broker unavailable"),
            )
        val dlqFuture = CompletableFuture.completedFuture(mock<SendResult<String, Any>>())

        whenever(kafkaTemplate.send(WorkoutEventPublisher.TOPIC_SET_LOGGED, event.sessionId, event))
            .thenReturn(failedFuture)
        whenever(kafkaTemplate.send("${WorkoutEventPublisher.TOPIC_SET_LOGGED}.dlq", event.sessionId, event))
            .thenReturn(dlqFuture)

        publisher.publishSetLogged(event)

        verify(kafkaTemplate).send(
            "${WorkoutEventPublisher.TOPIC_SET_LOGGED}.dlq",
            event.sessionId,
            event,
        )
    }

    @Test
    @DisplayName("TC-11: DLQ 발행도 실패 → 예외 흡수 (API 응답에 영향 없음)")
    fun `DLQ 발행 실패도 예외 흡수`() {
        val failedFuture =
            CompletableFuture.failedFuture<SendResult<String, Any>>(
                RuntimeException("Kafka broker unavailable"),
            )
        val dlqFailedFuture =
            CompletableFuture.failedFuture<SendResult<String, Any>>(
                RuntimeException("DLQ also unavailable"),
            )

        whenever(kafkaTemplate.send(WorkoutEventPublisher.TOPIC_SET_LOGGED, event.sessionId, event))
            .thenReturn(failedFuture)
        whenever(kafkaTemplate.send("${WorkoutEventPublisher.TOPIC_SET_LOGGED}.dlq", event.sessionId, event))
            .thenReturn(dlqFailedFuture)

        // 예외 전파 없이 정상 종료되어야 함
        publisher.publishSetLogged(event)
    }
}

package com.gymplan.notification.infrastructure.kafka

import com.gymplan.notification.domain.model.NotificationSettings
import com.gymplan.notification.infrastructure.fcm.FcmService
import com.gymplan.notification.infrastructure.idempotency.IdempotencyService
import com.gymplan.notification.infrastructure.redis.NotificationSettingsRedisRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.support.Acknowledgment

@ExtendWith(MockitoExtension::class)
class WorkoutEventConsumerTest {
    @Mock private lateinit var fcmService: FcmService
    @Mock private lateinit var settingsRepository: NotificationSettingsRedisRepository
    @Mock private lateinit var idempotencyService: IdempotencyService
    @Mock private lateinit var ack: Acknowledgment

    @InjectMocks
    private lateinit var consumer: WorkoutEventConsumer

    private val event = WorkoutSessionCompletedEvent(
        sessionId = "665f1a2b3c4d5e6f7a8b9c0d",
        userId = "1",
        durationSec = 4200,
        totalVolume = 3840.0,
        occurredAt = "2026-04-08T10:10:05Z",
    )

    // TC-04: 알림 설정 활성화 → FCM 발송
    @Test
    fun `TC-04 알림 활성화 시 FCM 발송`() {
        whenever(idempotencyService.isAlreadyProcessed(event.eventType, event.userId, event.occurredAt))
            .thenReturn(false)
        whenever(settingsRepository.findByUserId(1L)).thenReturn(
            NotificationSettings(userId = 1L, pushEnabled = true, workoutCompleteAlert = true),
        )

        consumer.consume(event, ack)

        verify(fcmService).sendWorkoutComplete(1L, event.sessionId, 3840.0, 70L)
        verify(ack).acknowledge()
    }

    // TC-05: workoutCompleteAlert=false → FCM 미발송
    @Test
    fun `TC-05 workoutCompleteAlert false 시 FCM 스킵`() {
        whenever(idempotencyService.isAlreadyProcessed(event.eventType, event.userId, event.occurredAt))
            .thenReturn(false)
        whenever(settingsRepository.findByUserId(1L)).thenReturn(
            NotificationSettings(userId = 1L, pushEnabled = true, workoutCompleteAlert = false),
        )

        consumer.consume(event, ack)

        verify(fcmService, never()).sendWorkoutComplete(any(), any(), any(), any())
        verify(ack).acknowledge()
    }

    // TC-08: 중복 이벤트 멱등 처리
    @Test
    fun `TC-08 중복 이벤트 스킵`() {
        whenever(idempotencyService.isAlreadyProcessed(event.eventType, event.userId, event.occurredAt))
            .thenReturn(true)

        consumer.consume(event, ack)

        verify(fcmService, never()).sendWorkoutComplete(any(), any(), any(), any())
        verify(ack).acknowledge()
    }
}

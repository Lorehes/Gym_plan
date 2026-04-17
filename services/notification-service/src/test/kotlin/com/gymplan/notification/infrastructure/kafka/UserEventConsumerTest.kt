package com.gymplan.notification.infrastructure.kafka

import com.gymplan.notification.infrastructure.fcm.FcmService
import com.gymplan.notification.infrastructure.idempotency.IdempotencyService
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
class UserEventConsumerTest {
    @Mock private lateinit var fcmService: FcmService
    @Mock private lateinit var idempotencyService: IdempotencyService
    @Mock private lateinit var ack: Acknowledgment

    @InjectMocks
    private lateinit var consumer: UserEventConsumer

    private val event = UserRegisteredEvent(
        userId = "10",
        email = "user@example.com",
        nickname = "철수",
        occurredAt = "2026-04-08T08:00:00Z",
    )

    // TC-06: 회원가입 환영 FCM 발송
    @Test
    fun `TC-06 신규 가입 시 FCM 환영 알림 발송`() {
        whenever(idempotencyService.isAlreadyProcessed(event.eventType, event.userId, event.occurredAt))
            .thenReturn(false)

        consumer.consume(event, ack)

        verify(fcmService).sendWelcome(10L)
        verify(ack).acknowledge()
    }

    @Test
    fun `중복 user registered 이벤트 스킵`() {
        whenever(idempotencyService.isAlreadyProcessed(event.eventType, event.userId, event.occurredAt))
            .thenReturn(true)

        consumer.consume(event, ack)

        verify(fcmService, never()).sendWelcome(any())
        verify(ack).acknowledge()
    }
}

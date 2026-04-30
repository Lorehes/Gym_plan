package com.gymplan.notification.infrastructure.kafka

import com.gymplan.notification.infrastructure.fcm.FcmService
import com.gymplan.notification.infrastructure.idempotency.IdempotencyService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * user.registered 이벤트 소비 → FCM 환영 알림.
 *
 * 신규 가입이므로 알림 설정 기본값(pushEnabled=true) 적용.
 * DLQ: KafkaConsumerConfig의 DefaultErrorHandler → user.registered.dlq
 */
@Component
class UserEventConsumer(
    private val fcmService: FcmService,
    private val idempotencyService: IdempotencyService,
) {
    private val log = LoggerFactory.getLogger(UserEventConsumer::class.java)

    @KafkaListener(
        topics = ["user.registered"],
        groupId = "gymplan-notification-service",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consume(
        event: UserRegisteredEvent,
        ack: Acknowledgment,
    ) {
        log.info("Kafka 수신: USER_REGISTERED userId={}", event.userId)

        if (idempotencyService.isAlreadyProcessed(event.eventType, event.userId, event.occurredAt)) {
            log.info("중복 이벤트 스킵: userId={}", event.userId)
            ack.acknowledge()
            return
        }

        val userId =
            event.userId.toLongOrNull() ?: run {
                log.warn("유효하지 않은 userId: {}", event.userId)
                ack.acknowledge()
                return
            }

        fcmService.sendWelcome(userId)
        ack.acknowledge()
    }
}

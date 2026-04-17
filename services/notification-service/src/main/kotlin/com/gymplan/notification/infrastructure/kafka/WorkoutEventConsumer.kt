package com.gymplan.notification.infrastructure.kafka

import com.gymplan.notification.infrastructure.fcm.FcmService
import com.gymplan.notification.infrastructure.idempotency.IdempotencyService
import com.gymplan.notification.infrastructure.redis.NotificationSettingsRedisRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * workout.session.completed 이벤트 소비 → FCM 운동 완료 알림.
 *
 * 멱등 처리: eventType + userId + occurredAt 조합으로 중복 발송 방지.
 * DLQ: KafkaConsumerConfig의 DefaultErrorHandler → workout.session.completed.dlq
 */
@Component
class WorkoutEventConsumer(
    private val fcmService: FcmService,
    private val settingsRepository: NotificationSettingsRedisRepository,
    private val idempotencyService: IdempotencyService,
) {
    private val log = LoggerFactory.getLogger(WorkoutEventConsumer::class.java)

    @KafkaListener(
        topics = ["workout.session.completed"],
        groupId = "gymplan-notification-service",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consume(
        event: WorkoutSessionCompletedEvent,
        ack: Acknowledgment,
    ) {
        log.info("Kafka 수신: WORKOUT_SESSION_COMPLETED sessionId={}, userId={}", event.sessionId, event.userId)

        if (idempotencyService.isAlreadyProcessed(event.eventType, event.userId, event.occurredAt)) {
            log.info("중복 이벤트 스킵: sessionId={}", event.sessionId)
            ack.acknowledge()
            return
        }

        val userId = event.userId.toLongOrNull() ?: run {
            log.warn("유효하지 않은 userId: {}", event.userId)
            ack.acknowledge()
            return
        }

        val settings = settingsRepository.findByUserId(userId)
        if (!settings.pushEnabled || !settings.workoutCompleteAlert) {
            log.info("알림 비활성화로 FCM 스킵: userId={}", userId)
            ack.acknowledge()
            return
        }

        val durationMin = event.durationSec / 60
        fcmService.sendWorkoutComplete(userId, event.sessionId, event.totalVolume, durationMin)

        ack.acknowledge()
    }
}

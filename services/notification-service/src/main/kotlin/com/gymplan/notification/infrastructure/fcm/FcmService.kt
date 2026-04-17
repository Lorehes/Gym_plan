package com.gymplan.notification.infrastructure.fcm

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.gymplan.notification.infrastructure.redis.FcmTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FcmService(
    private val fcmTokenRepository: FcmTokenRepository,
) {
    private val log = LoggerFactory.getLogger(FcmService::class.java)

    fun sendWorkoutComplete(
        userId: Long,
        sessionId: String,
        totalVolume: Double,
        durationMin: Long,
    ) {
        val token = fcmTokenRepository.findByUserId(userId) ?: run {
            log.info("FCM 토큰 없음 (등록 미완료): userId={}", userId)
            return
        }

        val body = "오늘 운동 완료! 총 볼륨 ${totalVolume}kg, ${durationMin}분 수고했어요 💪"
        val message =
            Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle("운동 완료 🎉")
                        .setBody(body)
                        .build(),
                )
                .putData("type", "WORKOUT_COMPLETED")
                .putData("sessionId", sessionId)
                .build()

        FirebaseMessaging.getInstance().send(message)
        log.info("FCM 운동 완료 알림 발송: userId={}, sessionId={}", userId, sessionId)
    }

    fun sendWelcome(userId: Long) {
        val token = fcmTokenRepository.findByUserId(userId) ?: run {
            log.info("FCM 토큰 없음 (등록 미완료): userId={}", userId)
            return
        }

        val message =
            Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle("GymPlan에 오신 것을 환영해요! 👋")
                        .setBody("첫 루틴을 만들어보세요.")
                        .build(),
                )
                .putData("type", "WELCOME")
                .putData("userId", userId.toString())
                .build()

        FirebaseMessaging.getInstance().send(message)
        log.info("FCM 환영 알림 발송: userId={}", userId)
    }
}

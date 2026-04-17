package com.gymplan.notification.infrastructure.redis

import com.gymplan.notification.domain.model.NotificationSettings
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

/**
 * 알림 설정을 Redis Hash에 저장.
 * Key: notification:settings:{userId}
 * Fields: restTimerEnabled, workoutCompleteAlert, pushEnabled
 */
@Repository
class NotificationSettingsRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun findByUserId(userId: Long): NotificationSettings {
        val key = settingsKey(userId)
        val ops = redisTemplate.opsForHash<String, String>()
        val hash = ops.entries(key)

        return NotificationSettings(
            userId = userId,
            restTimerEnabled = hash["restTimerEnabled"]?.toBoolean() ?: true,
            workoutCompleteAlert = hash["workoutCompleteAlert"]?.toBoolean() ?: true,
            pushEnabled = hash["pushEnabled"]?.toBoolean() ?: true,
        )
    }

    fun save(settings: NotificationSettings) {
        val key = settingsKey(settings.userId)
        val ops = redisTemplate.opsForHash<String, String>()
        ops.putAll(
            key,
            mapOf(
                "restTimerEnabled" to settings.restTimerEnabled.toString(),
                "workoutCompleteAlert" to settings.workoutCompleteAlert.toString(),
                "pushEnabled" to settings.pushEnabled.toString(),
            ),
        )
    }

    private fun settingsKey(userId: Long) = "notification:settings:$userId"
}

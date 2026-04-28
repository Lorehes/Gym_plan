package com.gymplan.notification.application.service

import com.gymplan.notification.application.dto.NotificationSettingsResponse
import com.gymplan.notification.application.dto.UpdateNotificationSettingsRequest
import com.gymplan.notification.domain.model.NotificationSettings
import com.gymplan.notification.infrastructure.redis.NotificationSettingsRedisRepository
import org.springframework.stereotype.Service

@Service
class NotificationSettingsService(
    private val settingsRepository: NotificationSettingsRedisRepository,
) {
    fun getSettings(userId: Long): NotificationSettingsResponse {
        val settings = settingsRepository.findByUserId(userId)
        return settings.toResponse()
    }

    fun updateSettings(
        userId: Long,
        request: UpdateNotificationSettingsRequest,
    ): NotificationSettingsResponse {
        val current = settingsRepository.findByUserId(userId)
        val updated =
            NotificationSettings(
                userId = userId,
                restTimerEnabled = request.restTimerEnabled,
                workoutCompleteAlert = request.workoutCompleteAlert,
                pushEnabled = request.pushEnabled,
            )
        settingsRepository.save(updated)
        return updated.toResponse()
    }

    private fun NotificationSettings.toResponse() =
        NotificationSettingsResponse(
            restTimerEnabled = restTimerEnabled,
            workoutCompleteAlert = workoutCompleteAlert,
            pushEnabled = pushEnabled,
        )
}

package com.gymplan.notification.application.dto

import jakarta.validation.constraints.NotNull

data class NotificationSettingsResponse(
    val restTimerEnabled: Boolean,
    val workoutCompleteAlert: Boolean,
    val pushEnabled: Boolean,
)

data class UpdateNotificationSettingsRequest(
    @field:NotNull val restTimerEnabled: Boolean?,
    @field:NotNull val workoutCompleteAlert: Boolean?,
    @field:NotNull val pushEnabled: Boolean?,
)

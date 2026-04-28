package com.gymplan.notification.application.dto

data class NotificationSettingsResponse(
    val restTimerEnabled: Boolean,
    val workoutCompleteAlert: Boolean,
    val pushEnabled: Boolean,
)

data class UpdateNotificationSettingsRequest(
    val restTimerEnabled: Boolean,
    val workoutCompleteAlert: Boolean,
    val pushEnabled: Boolean,
)

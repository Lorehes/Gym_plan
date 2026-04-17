package com.gymplan.notification.domain.model

data class NotificationSettings(
    val userId: Long,
    val restTimerEnabled: Boolean = true,
    val workoutCompleteAlert: Boolean = true,
    val pushEnabled: Boolean = true,
)

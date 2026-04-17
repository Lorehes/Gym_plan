package com.gymplan.notification.infrastructure.kafka

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkoutSessionCompletedEvent(
    val eventType: String = "WORKOUT_SESSION_COMPLETED",
    val sessionId: String,
    val userId: String,
    val planId: String? = null,
    val planName: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val durationSec: Long = 0,
    val totalVolume: Double = 0.0,
    val totalSets: Int = 0,
    val muscleGroups: List<String> = emptyList(),
    val occurredAt: String,
)

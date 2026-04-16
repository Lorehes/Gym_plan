package com.gymplan.workout.application.event

import java.time.Instant

/**
 * workout.set.logged Kafka 이벤트 페이로드.
 *
 * 명세: docs/architecture/kafka-events.md §workout.set.logged
 * 소비자: analytics-service (실시간 볼륨 누적, 개인 기록 갱신)
 */
data class WorkoutSetLoggedEvent(
    val eventType: String = "WORKOUT_SET_LOGGED",
    val sessionId: String,
    val userId: String,
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val setNo: Int,
    val reps: Int,
    val weightKg: Double,
    val volume: Double,
    val isSuccess: Boolean,
    val occurredAt: Instant = Instant.now(),
)

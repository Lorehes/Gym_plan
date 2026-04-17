package com.gymplan.workout.application.event

import java.time.Instant

/**
 * workout.session.completed Kafka 이벤트 페이로드.
 *
 * 명세: docs/architecture/kafka-events.md §workout.session.completed
 * 소비자:
 *   - analytics-service  → Elasticsearch 색인, 통계 업데이트
 *   - notification-service → 운동 완료 FCM 푸시 알림
 */
data class WorkoutSessionCompletedEvent(
    val eventType: String = "WORKOUT_SESSION_COMPLETED",
    val sessionId: String,
    val userId: String,
    val planId: String?,
    val planName: String?,
    val startedAt: Instant,
    val completedAt: Instant,
    val durationSec: Long,
    val totalVolume: Double,
    val totalSets: Int,
    val muscleGroups: List<String>,
    val occurredAt: Instant = Instant.now(),
)

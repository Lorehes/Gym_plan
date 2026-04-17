package com.gymplan.workout.domain.entity

import java.time.Instant

/**
 * 단일 세트 기록 (운동 세션 문서 내 중첩 객체).
 *
 * WorkoutSession.exercises[*].sets[] 배열의 원소.
 * @Document 없음 — WorkoutSession 단일 문서에 포함되어 저장된다.
 */
data class SetRecord(
    val setNo: Int,
    val reps: Int,
    val weightKg: Double,
    val isSuccess: Boolean,
    val completedAt: Instant = Instant.now(),
)

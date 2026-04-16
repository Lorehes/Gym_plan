package com.gymplan.workout.domain.entity

/**
 * 세션 내 운동 종목 기록 (운동 세션 문서 내 중첩 객체).
 *
 * WorkoutSession.exercises[] 배열의 원소.
 * exerciseName 은 비정규화 스냅샷 — exercise-catalog 변경/삭제에 독립적.
 */
data class SessionExercise(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val sets: List<SetRecord> = emptyList(),
)

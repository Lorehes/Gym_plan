package com.gymplan.workout.application.dto

import com.gymplan.workout.domain.entity.WorkoutSession
import java.time.Instant

data class StartSessionResponse(
    val sessionId: String,
    val startedAt: Instant,
    val status: String = "IN_PROGRESS",
)

data class CompleteSessionResponse(
    val sessionId: String,
    val durationSec: Long,
    val totalVolume: Double,
    val totalSets: Int,
)

data class SetRecordResponse(
    val sessionId: String,
    val exerciseId: String,
    val setNo: Int,
)

data class SessionSummaryResponse(
    val sessionId: String,
    val planId: String?,
    val planName: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
    val status: String,
    val totalVolume: Double,
    val totalSets: Int,
    val durationSec: Long,
)

data class SessionDetailResponse(
    val sessionId: String,
    val planId: String?,
    val planName: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
    val status: String,
    val totalVolume: Double,
    val totalSets: Int,
    val durationSec: Long,
    val notes: String?,
    val exercises: List<ExerciseResponse>,
)

data class ExerciseResponse(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val sets: List<SetResponse>,
)

data class SetResponse(
    val setNo: Int,
    val reps: Int,
    val weightKg: Double,
    val isSuccess: Boolean,
    val completedAt: Instant,
)

// ─── 변환 확장 함수 ───

fun WorkoutSession.toDetailResponse() =
    SessionDetailResponse(
        sessionId = id!!,
        planId = planId,
        planName = planName,
        startedAt = startedAt,
        completedAt = completedAt,
        status = if (completedAt == null) "IN_PROGRESS" else "COMPLETED",
        totalVolume = totalVolume,
        totalSets = totalSets,
        durationSec = durationSec,
        notes = notes,
        exercises =
            exercises.map { ex ->
                ExerciseResponse(
                    exerciseId = ex.exerciseId,
                    exerciseName = ex.exerciseName,
                    muscleGroup = ex.muscleGroup,
                    sets =
                        ex.sets.map { s ->
                            SetResponse(s.setNo, s.reps, s.weightKg, s.isSuccess, s.completedAt)
                        },
                )
            },
    )

fun WorkoutSession.toSummaryResponse() =
    SessionSummaryResponse(
        sessionId = id!!,
        planId = planId,
        planName = planName,
        startedAt = startedAt,
        completedAt = completedAt,
        status = if (completedAt == null) "IN_PROGRESS" else "COMPLETED",
        totalVolume = totalVolume,
        totalSets = totalSets,
        durationSec = durationSec,
    )

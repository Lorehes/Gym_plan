package com.gymplan.plan.application.dto

import com.gymplan.plan.domain.entity.PlanExercise
import com.gymplan.plan.domain.entity.WorkoutPlan
import java.math.BigDecimal

/** GET /api/v1/plans — 목록 항목 */
data class PlanSummaryResponse(
    val planId: Long,
    val name: String,
    val dayOfWeek: Int?,
    val exerciseCount: Int,
    val isTemplate: Boolean,
)

/** POST/PUT /api/v1/plans — 루틴 생성/수정 응답 */
data class PlanDetailResponse(
    val planId: Long,
    val name: String,
    val description: String?,
    val dayOfWeek: Int?,
    val isTemplate: Boolean,
    val exercises: List<ExerciseItemResponse>,
)

/** GET /api/v1/plans/today ⭐ 핵심 API 응답 */
data class TodayPlanResponse(
    val planId: Long,
    val name: String,
    val dayOfWeek: Int?,
    val exercises: List<ExerciseItemResponse>,
)

/** 운동 항목 */
data class ExerciseItemResponse(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val orderIndex: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: BigDecimal?,
    val restSeconds: Int,
    val notes: String?,
)

// ───── 확장 함수 ─────

fun WorkoutPlan.toSummaryResponse() =
    PlanSummaryResponse(
        planId = id!!,
        name = name,
        dayOfWeek = dayOfWeek,
        exerciseCount = exercises.size,
        isTemplate = isTemplate,
    )

fun WorkoutPlan.toDetailResponse() =
    PlanDetailResponse(
        planId = id!!,
        name = name,
        description = description,
        dayOfWeek = dayOfWeek,
        isTemplate = isTemplate,
        exercises = exercises.map { it.toResponse() },
    )

fun WorkoutPlan.toTodayPlanResponse() =
    TodayPlanResponse(
        planId = id!!,
        name = name,
        dayOfWeek = dayOfWeek,
        exercises = exercises.map { it.toResponse() },
    )

fun PlanExercise.toResponse() =
    ExerciseItemResponse(
        id = id!!,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        muscleGroup = muscleGroup,
        orderIndex = orderIndex,
        targetSets = targetSets,
        targetReps = targetReps,
        targetWeightKg = targetWeight,
        restSeconds = restSeconds,
        notes = notes,
    )

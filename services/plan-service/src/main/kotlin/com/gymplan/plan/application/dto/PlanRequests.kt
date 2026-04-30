package com.gymplan.plan.application.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreatePlanRequest(
    @field:NotBlank(message = "루틴 이름은 필수입니다")
    @field:Size(min = 1, max = 100, message = "루틴 이름은 1~100자 이내여야 합니다")
    val name: String,
    @field:Size(max = 500, message = "설명은 500자 이내여야 합니다")
    val description: String? = null,
    /** 0=월, 1=화, 2=수, 3=목, 4=금, 5=토, 6=일, null=무요일 */
    @field:Min(value = 0, message = "dayOfWeek은 0~6 사이여야 합니다")
    @field:Max(value = 6, message = "dayOfWeek은 0~6 사이여야 합니다")
    val dayOfWeek: Int? = null,
)

data class UpdatePlanRequest(
    @field:Size(min = 1, max = 100, message = "루틴 이름은 1~100자 이내여야 합니다")
    val name: String? = null,
    @field:Size(max = 500, message = "설명은 500자 이내여야 합니다")
    val description: String? = null,
    @field:Min(value = 0, message = "dayOfWeek은 0~6 사이여야 합니다")
    @field:Max(value = 6, message = "dayOfWeek은 0~6 사이여야 합니다")
    val dayOfWeek: Int? = null,
)

data class AddExerciseRequest(
    @field:Min(value = 1, message = "exerciseId는 1 이상이어야 합니다")
    val exerciseId: Long,
    @field:NotBlank(message = "exerciseName은 필수입니다")
    @field:Size(max = 100, message = "exerciseName은 100자 이내여야 합니다")
    val exerciseName: String,
    /**
     * 비정규화 저장 값. exercise-catalog와 동일한 허용값.
     * plan-service는 exercise-catalog를 호출하지 않으므로 클라이언트가 올바른 값을 전송해야 한다.
     */
    @field:NotBlank(message = "muscleGroup은 필수입니다")
    @field:Pattern(
        regexp = "^(CHEST|BACK|SHOULDERS|ARMS|LEGS|CORE|CARDIO)$",
        message = "muscleGroup은 CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE, CARDIO 중 하나여야 합니다",
    )
    val muscleGroup: String,
    /** null이면 현재 마지막 순서 다음으로 자동 배정 */
    val orderIndex: Int? = null,
    @field:Min(value = 1, message = "targetSets은 1 이상이어야 합니다")
    val targetSets: Int = 3,
    @field:Min(value = 1, message = "targetReps은 1 이상이어야 합니다")
    val targetReps: Int = 10,
    val targetWeightKg: BigDecimal? = null,
    @field:Min(value = 0, message = "restSeconds은 0 이상이어야 합니다")
    val restSeconds: Int = 90,
    @field:Size(max = 255, message = "notes는 255자 이내여야 합니다")
    val notes: String? = null,
)

data class UpdateExerciseRequest(
    @field:Size(max = 100, message = "exerciseName은 100자 이내여야 합니다")
    val exerciseName: String? = null,
    @field:Pattern(
        regexp = "^(CHEST|BACK|SHOULDERS|ARMS|LEGS|CORE|CARDIO)$",
        message = "muscleGroup은 CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE, CARDIO 중 하나여야 합니다",
    )
    val muscleGroup: String? = null,
    @field:Min(value = 1, message = "targetSets은 1 이상이어야 합니다")
    val targetSets: Int? = null,
    @field:Min(value = 1, message = "targetReps은 1 이상이어야 합니다")
    val targetReps: Int? = null,
    val targetWeightKg: BigDecimal? = null,
    @field:Min(value = 0, message = "restSeconds은 0 이상이어야 합니다")
    val restSeconds: Int? = null,
    @field:Size(max = 255, message = "notes는 255자 이내여야 합니다")
    val notes: String? = null,
)

data class ReorderExercisesRequest(
    val orderedIds: List<Long>,
)

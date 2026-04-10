package com.gymplan.exercise.application.dto

import com.gymplan.exercise.domain.vo.Difficulty
import com.gymplan.exercise.domain.vo.Equipment
import com.gymplan.exercise.domain.vo.MuscleGroup
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 커스텀 종목 생성 요청.
 *
 * 참조: docs/specs/exercise-catalog.md — POST /api/v1/exercises
 */
data class CreateExerciseRequest(
    @field:NotBlank(message = "종목 이름은 필수입니다")
    @field:Size(max = 100, message = "종목 이름은 100자 이내여야 합니다")
    val name: String? = null,
    @field:Size(max = 100, message = "영문 이름은 100자 이내여야 합니다")
    val nameEn: String? = null,
    @field:NotNull(message = "근육 부위는 필수입니다")
    val muscleGroup: MuscleGroup? = null,
    @field:NotNull(message = "장비 종류는 필수입니다")
    val equipment: Equipment? = null,
    @field:NotNull(message = "난이도는 필수입니다")
    val difficulty: Difficulty? = null,
    @field:Size(max = 2000, message = "설명은 2000자 이내여야 합니다")
    val description: String? = null,
    @field:Size(max = 500, message = "동영상 URL은 500자 이내여야 합니다")
    val videoUrl: String? = null,
)

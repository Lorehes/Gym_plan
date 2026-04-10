package com.gymplan.exercise.application.dto

import com.gymplan.exercise.domain.entity.Exercise
import com.gymplan.exercise.domain.vo.Difficulty
import com.gymplan.exercise.domain.vo.Equipment
import com.gymplan.exercise.domain.vo.MuscleGroup

/**
 * 종목 목록 응답 (검색 결과).
 * description, videoUrl 은 목록에서 제외.
 */
data class ExerciseSummaryResponse(
    val exerciseId: Long,
    val name: String,
    val nameEn: String?,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val difficulty: Difficulty,
) {
    companion object {
        fun from(entity: Exercise): ExerciseSummaryResponse =
            ExerciseSummaryResponse(
                exerciseId = entity.id!!,
                name = entity.name,
                nameEn = entity.nameEn,
                muscleGroup = entity.muscleGroup,
                equipment = entity.equipment,
                difficulty = entity.difficulty,
            )
    }
}

/**
 * 종목 상세 응답 (description, videoUrl 포함).
 */
data class ExerciseDetailResponse(
    val exerciseId: Long,
    val name: String,
    val nameEn: String?,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val difficulty: Difficulty,
    val description: String?,
    val videoUrl: String?,
    val isCustom: Boolean,
    val createdBy: Long?,
) {
    companion object {
        fun from(entity: Exercise): ExerciseDetailResponse =
            ExerciseDetailResponse(
                exerciseId = entity.id!!,
                name = entity.name,
                nameEn = entity.nameEn,
                muscleGroup = entity.muscleGroup,
                equipment = entity.equipment,
                difficulty = entity.difficulty,
                description = entity.description,
                videoUrl = entity.videoUrl,
                isCustom = entity.isCustom,
                createdBy = entity.createdBy,
            )
    }
}

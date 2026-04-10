package com.gymplan.exercise.application.service

import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.NotFoundException
import com.gymplan.exercise.application.dto.CreateExerciseRequest
import com.gymplan.exercise.application.dto.ExerciseDetailResponse
import com.gymplan.exercise.domain.entity.Exercise
import com.gymplan.exercise.domain.repository.ExerciseRepository
import com.gymplan.exercise.infrastructure.search.ExerciseIndexer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운동 종목 CRUD 서비스.
 *
 * - 상세 조회: MySQL 직접 조회
 * - 생성: MySQL 저장 후 Elasticsearch 비동기 색인
 *
 * 참조: docs/specs/exercise-catalog.md, docs/architecture/services.md
 */
@Service
@Transactional(readOnly = true)
class ExerciseService(
    private val exerciseRepository: ExerciseRepository,
    private val exerciseIndexer: ExerciseIndexer,
) {
    fun getById(exerciseId: Long): ExerciseDetailResponse {
        val exercise =
            exerciseRepository.findById(exerciseId)
                .orElseThrow { NotFoundException(ErrorCode.EXERCISE_NOT_FOUND) }
        return ExerciseDetailResponse.from(exercise)
    }

    @Transactional
    fun create(
        request: CreateExerciseRequest,
        userId: Long,
    ): ExerciseDetailResponse {
        val exercise =
            Exercise(
                name = request.name!!,
                nameEn = request.nameEn,
                muscleGroup = request.muscleGroup!!,
                equipment = request.equipment!!,
                difficulty = request.difficulty!!,
                description = request.description,
                videoUrl = request.videoUrl,
                isCustom = true,
                createdBy = userId,
            )
        val saved = exerciseRepository.save(exercise)
        exerciseIndexer.index(saved)
        return ExerciseDetailResponse.from(saved)
    }
}

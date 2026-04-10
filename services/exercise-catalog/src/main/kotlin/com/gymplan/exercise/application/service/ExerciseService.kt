package com.gymplan.exercise.application.service

import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.NotFoundException
import com.gymplan.exercise.application.dto.CreateExerciseRequest
import com.gymplan.exercise.application.dto.ExerciseDetailResponse
import com.gymplan.exercise.domain.entity.Exercise
import com.gymplan.exercise.domain.event.ExerciseCreatedEvent
import com.gymplan.exercise.domain.repository.ExerciseRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운동 종목 CRUD 서비스.
 *
 * - 상세 조회: MySQL 직접 조회
 * - 생성: MySQL 저장 후 이벤트 발행 → 트랜잭션 커밋 후 Elasticsearch 비동기 색인
 *
 * 참조: docs/specs/exercise-catalog.md, docs/architecture/services.md
 */
@Service
@Transactional(readOnly = true)
class ExerciseService(
    private val exerciseRepository: ExerciseRepository,
    private val eventPublisher: ApplicationEventPublisher,
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
                name = requireNotNull(request.name) { "종목 이름은 필수입니다" },
                nameEn = request.nameEn,
                muscleGroup = requireNotNull(request.muscleGroup) { "근육 부위는 필수입니다" },
                equipment = requireNotNull(request.equipment) { "장비 종류는 필수입니다" },
                difficulty = requireNotNull(request.difficulty) { "난이도는 필수입니다" },
                description = request.description,
                videoUrl = request.videoUrl,
                isCustom = true,
                createdBy = userId,
            )
        val saved = exerciseRepository.save(exercise)

        eventPublisher.publishEvent(
            ExerciseCreatedEvent(
                exerciseId = saved.id!!,
                name = saved.name,
                nameEn = saved.nameEn,
                muscleGroup = saved.muscleGroup.name,
                equipment = saved.equipment.name,
                difficulty = saved.difficulty.name,
            ),
        )

        return ExerciseDetailResponse.from(saved)
    }
}

package com.gymplan.plan.application.service

import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.ForbiddenException
import com.gymplan.common.exception.GymPlanException
import com.gymplan.common.exception.NotFoundException
import com.gymplan.plan.application.dto.AddExerciseRequest
import com.gymplan.plan.application.dto.CreatePlanRequest
import com.gymplan.plan.application.dto.ExerciseItemResponse
import com.gymplan.plan.application.dto.PlanDetailResponse
import com.gymplan.plan.application.dto.PlanSummaryResponse
import com.gymplan.plan.application.dto.ReorderExercisesRequest
import com.gymplan.plan.application.dto.UpdateExerciseRequest
import com.gymplan.plan.application.dto.UpdatePlanRequest
import com.gymplan.plan.application.dto.toDetailResponse
import com.gymplan.plan.application.dto.toResponse
import com.gymplan.plan.application.dto.toSummaryResponse
import com.gymplan.plan.domain.entity.PlanExercise
import com.gymplan.plan.domain.entity.WorkoutPlan
import com.gymplan.plan.domain.repository.PlanExerciseRepository
import com.gymplan.plan.domain.repository.WorkoutPlanRepository
import com.gymplan.plan.infrastructure.cache.PlanCacheManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 루틴 CRUD 유스케이스.
 *
 * 명세: docs/specs/plan-service.md §3.1, §3.3
 *
 * 캐시 무효화 원칙:
 *   - 루틴 수정/삭제:      plan:today:{userId}, plan:cache:{planId} 즉시 DEL
 *   - 운동 추가/수정/삭제: 동일 (캐시에 exercises가 포함되어 있으므로)
 */
@Service
class PlanService(
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val planExerciseRepository: PlanExerciseRepository,
    private val planCacheManager: PlanCacheManager,
) {
    private val log = LoggerFactory.getLogger(PlanService::class.java)

    // ─────────────────── 루틴 관리 ───────────────────

    @Transactional(readOnly = true)
    fun getMyPlans(userId: Long): List<PlanSummaryResponse> =
        workoutPlanRepository
            .findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)
            .map { it.toSummaryResponse() }

    @Transactional(readOnly = true)
    fun getPlan(userId: Long, planId: Long): PlanDetailResponse {
        // 캐시 키가 userId를 포함하므로 히트 시 소유권이 이미 보장됨 — DB 조회 불필요
        planCacheManager.getPlanDetail(userId, planId)?.let { return it }

        val plan = workoutPlanRepository.findWithExercisesByIdAndIsActiveTrue(planId)
            ?: throw NotFoundException(ErrorCode.PLAN_NOT_FOUND)
        if (plan.userId != userId) throw ForbiddenException(ErrorCode.PLAN_ACCESS_DENIED)

        val response = plan.toDetailResponse()
        planCacheManager.setPlanDetail(userId, planId, response)
        return response
    }

    @Transactional
    fun createPlan(userId: Long, request: CreatePlanRequest): PlanDetailResponse {
        val plan = WorkoutPlan(
            userId = userId,
            name = request.name,
            description = request.description,
            dayOfWeek = request.dayOfWeek,
        )
        val saved = workoutPlanRepository.save(plan)
        log.info("루틴 생성: userId={}, planId={}, name={}", userId, saved.id, saved.name)
        return saved.toDetailResponse()
    }

    @Transactional
    fun updatePlan(userId: Long, planId: Long, request: UpdatePlanRequest): PlanDetailResponse {
        // exercises 포함 로딩 → 이후 toDetailResponse()에서 추가 쿼리 불필요
        val plan = workoutPlanRepository.findWithExercisesByIdAndIsActiveTrue(planId)
            ?: throw NotFoundException(ErrorCode.PLAN_NOT_FOUND)
        if (plan.userId != userId) throw ForbiddenException(ErrorCode.PLAN_ACCESS_DENIED)

        plan.update(request.name, request.description, request.dayOfWeek)
        workoutPlanRepository.save(plan)

        planCacheManager.evictPlanCaches(userId, planId)
        log.info("루틴 수정: userId={}, planId={}", userId, planId)

        return plan.toDetailResponse()
    }

    @Transactional
    fun deletePlan(userId: Long, planId: Long) {
        val plan = findPlanForUser(userId, planId)
        plan.softDelete()
        workoutPlanRepository.save(plan)

        planCacheManager.evictPlanCaches(userId, planId)
        log.info("루틴 삭제(soft): userId={}, planId={}", userId, planId)
    }

    // ─────────────────── 운동 관리 ───────────────────

    @Transactional
    fun addExercise(userId: Long, planId: Long, request: AddExerciseRequest): ExerciseItemResponse {
        val plan = findPlanForUser(userId, planId)

        val orderIndex = request.orderIndex
            ?: ((planExerciseRepository.findTopByPlanIdOrderByOrderIndexDesc(planId)?.orderIndex ?: -1) + 1)

        val exercise = PlanExercise(
            plan = plan,
            exerciseId = request.exerciseId,
            exerciseName = request.exerciseName,
            muscleGroup = request.muscleGroup,
            orderIndex = orderIndex,
            targetSets = request.targetSets,
            targetReps = request.targetReps,
            targetWeight = request.targetWeightKg,
            restSeconds = request.restSeconds,
            notes = request.notes,
        )
        val saved = planExerciseRepository.save(exercise)

        planCacheManager.evictPlanCaches(userId, planId)
        log.info("운동 추가: userId={}, planId={}, exerciseId={}", userId, planId, request.exerciseId)

        return saved.toResponse()
    }

    @Transactional
    fun updateExercise(
        userId: Long,
        planId: Long,
        exerciseItemId: Long,
        request: UpdateExerciseRequest,
    ): ExerciseItemResponse {
        findPlanForUser(userId, planId)
        val exercise = findExerciseForPlan(planId, exerciseItemId)

        exercise.update(
            exerciseName = request.exerciseName,
            muscleGroup = request.muscleGroup,
            targetSets = request.targetSets,
            targetReps = request.targetReps,
            targetWeight = request.targetWeightKg,
            restSeconds = request.restSeconds,
            notes = request.notes,
        )
        planExerciseRepository.save(exercise)

        planCacheManager.evictPlanCaches(userId, planId)
        return exercise.toResponse()
    }

    @Transactional
    fun deleteExercise(userId: Long, planId: Long, exerciseItemId: Long) {
        findPlanForUser(userId, planId)
        val exercise = findExerciseForPlan(planId, exerciseItemId)
        planExerciseRepository.delete(exercise)

        planCacheManager.evictPlanCaches(userId, planId)
        log.info("운동 삭제: userId={}, planId={}, exerciseItemId={}", userId, planId, exerciseItemId)
    }

    @Transactional
    fun reorderExercises(userId: Long, planId: Long, request: ReorderExercisesRequest) {
        findPlanForUser(userId, planId)

        val exercises = planExerciseRepository.findByPlanIdOrderByOrderIndexAsc(planId)

        if (request.orderedIds.size != exercises.size) {
            throw GymPlanException(
                ErrorCode.VALIDATION_FAILED,
                "orderedIds 개수(${request.orderedIds.size})가 루틴의 운동 수(${exercises.size})와 일치하지 않습니다",
            )
        }

        val exerciseMap = exercises.associateBy { it.id!! }
        request.orderedIds.forEachIndexed { index, id ->
            val exercise = exerciseMap[id]
                ?: throw GymPlanException(ErrorCode.VALIDATION_FAILED, "존재하지 않는 exerciseItemId: $id")
            exercise.updateOrderIndex(index)
        }
        planExerciseRepository.saveAll(exerciseMap.values)

        planCacheManager.evictPlanCaches(userId, planId)
        log.info("운동 순서 변경: userId={}, planId={}", userId, planId)
    }

    // ─────────────────── 내부 헬퍼 ───────────────────

    private fun findPlanForUser(userId: Long, planId: Long): WorkoutPlan {
        val plan = workoutPlanRepository.findById(planId)
            .filter { it.isActive }
            .orElseThrow { NotFoundException(ErrorCode.PLAN_NOT_FOUND) }

        if (plan.userId != userId) {
            throw ForbiddenException(ErrorCode.PLAN_ACCESS_DENIED)
        }
        return plan
    }

    private fun findExerciseForPlan(planId: Long, exerciseItemId: Long): PlanExercise {
        val exercise = planExerciseRepository.findById(exerciseItemId)
            .orElseThrow { NotFoundException(ErrorCode.EXERCISE_NOT_FOUND) }

        if (exercise.plan.id != planId) {
            throw ForbiddenException(ErrorCode.PLAN_ACCESS_DENIED)
        }
        return exercise
    }
}

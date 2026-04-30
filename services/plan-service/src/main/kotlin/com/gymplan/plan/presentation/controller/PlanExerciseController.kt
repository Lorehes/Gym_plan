package com.gymplan.plan.presentation.controller

import com.gymplan.common.dto.ApiResponse
import com.gymplan.common.security.CurrentUserId
import com.gymplan.plan.application.dto.AddExerciseRequest
import com.gymplan.plan.application.dto.ExerciseItemResponse
import com.gymplan.plan.application.dto.ReorderExercisesRequest
import com.gymplan.plan.application.dto.UpdateExerciseRequest
import com.gymplan.plan.application.service.PlanService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 루틴 내 운동 항목 관리 엔드포인트.
 *
 * 참조: docs/api/plan-service.md — POST/PUT/DELETE /plans/{planId}/exercises/...
 *
 * 설계 원칙: plan-service는 exercise-catalog를 HTTP 호출하지 않는다.
 *   exerciseName / muscleGroup은 클라이언트가 exercise-catalog에서 선택 후 전달.
 *   plan-service는 수신한 값을 그대로 plan_exercises에 저장한다 (비정규화).
 */
@RestController
@RequestMapping("/api/v1/plans/{planId}/exercises")
class PlanExerciseController(
    private val planService: PlanService,
) {
    /** 운동 추가 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addExercise(
        @CurrentUserId userId: Long,
        @PathVariable planId: Long,
        @Valid @RequestBody request: AddExerciseRequest,
    ): ApiResponse<ExerciseItemResponse> = ApiResponse.success(planService.addExercise(userId, planId, request))

    // 운동 순서 변경. '/reorder' 리터럴이 '/{exerciseItemId}' 경로 변수보다 우선 매핑됨.
    @PutMapping("/reorder")
    fun reorderExercises(
        @CurrentUserId userId: Long,
        @PathVariable planId: Long,
        @Valid @RequestBody request: ReorderExercisesRequest,
    ): ApiResponse<Map<String, Boolean>> {
        planService.reorderExercises(userId, planId, request)
        return ApiResponse.success(mapOf("reordered" to true))
    }

    /** 운동 설정 수정 */
    @PutMapping("/{exerciseItemId}")
    fun updateExercise(
        @CurrentUserId userId: Long,
        @PathVariable planId: Long,
        @PathVariable exerciseItemId: Long,
        @Valid @RequestBody request: UpdateExerciseRequest,
    ): ApiResponse<ExerciseItemResponse> = ApiResponse.success(planService.updateExercise(userId, planId, exerciseItemId, request))

    /** 운동 제거 */
    @DeleteMapping("/{exerciseItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteExercise(
        @CurrentUserId userId: Long,
        @PathVariable planId: Long,
        @PathVariable exerciseItemId: Long,
    ) = planService.deleteExercise(userId, planId, exerciseItemId)
}

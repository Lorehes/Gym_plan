package com.gymplan.plan.presentation.controller

import com.gymplan.common.dto.ApiResponse
import com.gymplan.common.security.CurrentUserId
import com.gymplan.plan.application.dto.CreatePlanRequest
import com.gymplan.plan.application.dto.PlanDetailResponse
import com.gymplan.plan.application.dto.PlanSummaryResponse
import com.gymplan.plan.application.dto.TodayPlanResponse
import com.gymplan.plan.application.dto.UpdatePlanRequest
import com.gymplan.plan.application.service.PlanService
import com.gymplan.plan.application.service.TodayPlanService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 루틴 CRUD 엔드포인트.
 *
 * 참조: docs/api/plan-service.md, docs/specs/plan-service.md
 *
 * 인증: Gateway가 주입한 X-User-Id 헤더를 @CurrentUserId로 주입.
 *       plan-service는 JWT를 직접 검증하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/plans")
class PlanController(
    private val planService: PlanService,
    private val todayPlanService: TodayPlanService,
) {
    /**
     * 오늘의 루틴 조회 ⭐ 핵심 API
     * Redis Cache-Aside: 캐시 히트 시 P95 < 200ms 목표.
     * 오늘 요일에 배정된 루틴이 없으면 data: null 반환 (404 아님).
     */
    @GetMapping("/today")
    fun getTodayPlan(
        @CurrentUserId userId: Long,
    ): ApiResponse<TodayPlanResponse?> = ApiResponse.success(todayPlanService.getTodayPlan(userId))

    /** 내 루틴 목록 조회 */
    @GetMapping
    fun getMyPlans(
        @CurrentUserId userId: Long,
    ): ApiResponse<List<PlanSummaryResponse>> = ApiResponse.success(planService.getMyPlans(userId))

    /** 루틴 생성 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPlan(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: CreatePlanRequest,
    ): ApiResponse<PlanDetailResponse> = ApiResponse.success(planService.createPlan(userId, request))

    /** 루틴 상세 조회 */
    @GetMapping("/{planId}")
    fun getPlan(
        @CurrentUserId userId: Long,
        @PathVariable planId: Long,
    ): ApiResponse<PlanDetailResponse> = ApiResponse.success(planService.getPlan(userId, planId))

    /** 루틴 수정 + 캐시 무효화 */
    @PutMapping("/{planId}")
    fun updatePlan(
        @CurrentUserId userId: Long,
        @PathVariable planId: Long,
        @Valid @RequestBody request: UpdatePlanRequest,
    ): ApiResponse<PlanDetailResponse> = ApiResponse.success(planService.updatePlan(userId, planId, request))

    /** 루틴 삭제 (soft delete) + 캐시 무효화 */
    @DeleteMapping("/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePlan(
        @CurrentUserId userId: Long,
        @PathVariable planId: Long,
    ) = planService.deletePlan(userId, planId)
}

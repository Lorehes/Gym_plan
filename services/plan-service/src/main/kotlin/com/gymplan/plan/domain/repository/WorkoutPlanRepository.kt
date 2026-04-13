package com.gymplan.plan.domain.repository

import com.gymplan.plan.domain.entity.WorkoutPlan
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface WorkoutPlanRepository : JpaRepository<WorkoutPlan, Long> {

    /** 내 루틴 목록 (soft delete 제외) */
    fun findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId: Long): List<WorkoutPlan>

    /**
     * 오늘의 루틴 조회 (가장 최근 생성, exercises JOIN).
     * @EntityGraph 로 exercises 컬렉션을 단일 LEFT JOIN 쿼리로 로딩 (N+1 방지).
     */
    @EntityGraph(attributePaths = ["exercises"])
    fun findTopByUserIdAndDayOfWeekAndIsActiveTrueOrderByCreatedAtDesc(
        userId: Long,
        dayOfWeek: Int,
    ): WorkoutPlan?

    /**
     * 루틴 상세 조회 (exercises JOIN).
     * plan:cache:{planId} 캐시 미스 시 사용.
     */
    @EntityGraph(attributePaths = ["exercises"])
    fun findWithExercisesByIdAndIsActiveTrue(id: Long): WorkoutPlan?
}

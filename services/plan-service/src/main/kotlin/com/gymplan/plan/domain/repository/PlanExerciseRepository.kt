package com.gymplan.plan.domain.repository

import com.gymplan.plan.domain.entity.PlanExercise
import org.springframework.data.jpa.repository.JpaRepository

interface PlanExerciseRepository : JpaRepository<PlanExercise, Long> {

    fun findByPlanIdOrderByOrderIndexAsc(planId: Long): List<PlanExercise>

    fun countByPlanId(planId: Long): Long

    /** orderIndex 자동 배정용: 현재 max orderIndex */
    fun findTopByPlanIdOrderByOrderIndexDesc(planId: Long): PlanExercise?
}

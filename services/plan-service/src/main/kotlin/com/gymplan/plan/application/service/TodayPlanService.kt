package com.gymplan.plan.application.service

import com.gymplan.plan.application.dto.TodayPlanResponse
import com.gymplan.plan.application.dto.toTodayPlanResponse
import com.gymplan.plan.domain.repository.WorkoutPlanRepository
import com.gymplan.plan.infrastructure.cache.PlanCacheManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

/**
 * 오늘의 루틴 조회 유스케이스.
 *
 * 성능 목표: P95 < 200ms (Redis 캐시 히트 기준)
 * 명세: docs/specs/plan-service.md §3.2, §7
 *
 * Cache-Aside 흐름:
 *   1. Redis GET plan:today:{userId}
 *   2. HIT  → 즉시 반환 (DB 쿼리 없음)
 *   3. MISS → DB 조회 (LEFT JOIN FETCH exercises) → Redis SET EX 600 → 반환
 *
 * dayOfWeek 변환: KST 기준, DayOfWeek.MONDAY.value(=1) - 1 = 0(월요일)
 */
@Service
class TodayPlanService(
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val planCacheManager: PlanCacheManager,
) {
    private val log = LoggerFactory.getLogger(TodayPlanService::class.java)

    @Transactional(readOnly = true)
    fun getTodayPlan(userId: Long): TodayPlanResponse? {
        // 1. 캐시 조회
        planCacheManager.getTodayPlan(userId)?.let {
            log.debug("오늘의 루틴 캐시 HIT: userId={}", userId)
            return it
        }

        // 2. 캐시 미스 → DB 조회
        val todayDow = todayDayOfWeek()
        val plan = workoutPlanRepository
            .findTopByUserIdAndDayOfWeekAndIsActiveTrueOrderByCreatedAtDesc(userId, todayDow)

        if (plan == null) {
            log.debug("오늘({})에 배정된 루틴 없음: userId={}", todayDow, userId)
            return null
        }

        val response = plan.toTodayPlanResponse()

        // 3. 캐시 저장 (TTL 10분)
        planCacheManager.setTodayPlan(userId, response)
        log.debug("오늘의 루틴 캐시 MISS → DB 조회 후 캐시 저장: userId={}", userId)

        return response
    }

    /** KST 기준 오늘 요일 (0=월, 1=화, ..., 6=일) */
    private fun todayDayOfWeek(): Int =
        LocalDate.now(ZoneId.of("Asia/Seoul")).dayOfWeek.value - 1
}

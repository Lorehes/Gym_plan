package com.gymplan.plan.infrastructure.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.gymplan.plan.application.dto.PlanDetailResponse
import com.gymplan.plan.application.dto.TodayPlanResponse
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * plan-service Redis 캐시 관리.
 *
 * 키 설계 (docs/database/redis-keys.md):
 *   plan:today:{userId}  — 오늘의 루틴 전체 (TodayPlanResponse JSON), TTL 10분
 *   plan:cache:{planId}  — 루틴 상세 (PlanDetailResponse JSON),     TTL 10분
 *
 * 패턴:
 *   읽기: Read-Aside (캐시 먼저 → 미스 시 DB → 캐시 저장)
 *   쓰기: Write-Invalidation (DB 저장 후 관련 키 즉시 DEL)
 *
 * plan-service는 exercise-catalog를 HTTP 호출하지 않으므로,
 * 캐시된 JSON에 exerciseName/muscleGroup이 이미 포함되어 있다.
 */
@Component
class PlanCacheManager(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(PlanCacheManager::class.java)

    // ───── 오늘의 루틴 캐시 ─────

    fun getTodayPlan(userId: Long): TodayPlanResponse? {
        val key = todayKey(userId)
        val json = redis.opsForValue().get(key) ?: return null
        return runCatching {
            objectMapper.readValue(json, TodayPlanResponse::class.java)
        }.onFailure {
            log.warn("plan:today:{} 역직렬화 실패 — 캐시 삭제: {}", userId, it.message)
            redis.delete(key)
        }.getOrNull()
    }

    fun setTodayPlan(userId: Long, response: TodayPlanResponse) {
        val key = todayKey(userId)
        val json = objectMapper.writeValueAsString(response)
        redis.opsForValue().set(key, json, TTL)
        log.debug("plan:today:{} 캐시 저장", userId)
    }

    fun evictTodayPlan(userId: Long) {
        redis.delete(todayKey(userId))
        log.debug("plan:today:{} 캐시 삭제", userId)
    }

    // ───── 루틴 상세 캐시 ─────

    fun getPlanDetail(planId: Long): PlanDetailResponse? {
        val key = cacheKey(planId)
        val json = redis.opsForValue().get(key) ?: return null
        return runCatching {
            objectMapper.readValue(json, PlanDetailResponse::class.java)
        }.onFailure {
            log.warn("plan:cache:{} 역직렬화 실패 — 캐시 삭제: {}", planId, it.message)
            redis.delete(key)
        }.getOrNull()
    }

    fun setPlanDetail(planId: Long, response: PlanDetailResponse) {
        val key = cacheKey(planId)
        val json = objectMapper.writeValueAsString(response)
        redis.opsForValue().set(key, json, TTL)
        log.debug("plan:cache:{} 캐시 저장", planId)
    }

    /**
     * 루틴 수정/삭제/운동 변경 시 연관 캐시 전체 무효화.
     * TTL 만료를 기다리지 않는다 — 체육관에서 최신 루틴 보장 필수.
     */
    fun evictPlanCaches(userId: Long, planId: Long) {
        redis.delete(listOf(todayKey(userId), cacheKey(planId)))
        log.debug("캐시 무효화: plan:today:{}, plan:cache:{}", userId, planId)
    }

    // ───── 키 빌더 ─────

    private fun todayKey(userId: Long) = "plan:today:$userId"

    private fun cacheKey(planId: Long) = "plan:cache:$planId"

    companion object {
        val TTL: Duration = Duration.ofMinutes(10)
    }
}

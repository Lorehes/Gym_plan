package com.gymplan.plan.infrastructure.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * plan-service 캐시 메트릭.
 *
 * Grafana SLA/비즈니스 대시보드가 아래 메트릭명을 참조합니다:
 *   cache_gets_total{result="hit"}  — 오늘의 루틴 Redis 캐시 히트
 *   cache_gets_total{result="miss"} — 오늘의 루틴 Redis 캐시 미스 (DB 조회 발생)
 */
@Component
class PlanMetrics(registry: MeterRegistry) {
    val todayPlanCacheHit: Counter =
        Counter.builder("cache_gets_total")
            .description("오늘의 루틴 캐시 조회 결과")
            .tag("cache", "today-plan")
            .tag("result", "hit")
            .register(registry)

    val todayPlanCacheMiss: Counter =
        Counter.builder("cache_gets_total")
            .description("오늘의 루틴 캐시 조회 결과")
            .tag("cache", "today-plan")
            .tag("result", "miss")
            .register(registry)
}

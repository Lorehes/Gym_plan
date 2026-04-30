package com.gymplan.user.infrastructure.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * user-service 비즈니스 메트릭.
 *
 * Grafana 비즈니스 대시보드가 아래 메트릭명을 그대로 참조합니다:
 *   gymplan_user_registrations_total — 신규 가입 카운터
 *   gymplan_active_users_daily       — 로그인 이벤트 카운터 (DAU 근사값)
 */
@Component
class UserMetrics(registry: MeterRegistry) {
    val registrations: Counter =
        Counter.builder("gymplan_user_registrations_total")
            .description("신규 회원가입 수")
            .register(registry)

    val activeUsers: Counter =
        Counter.builder("gymplan_active_users_daily")
            .description("로그인 이벤트 수 (DAU 근사값 — increase([24h])로 집계)")
            .register(registry)
}

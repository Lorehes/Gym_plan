package com.gymplan.workout.infrastructure.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * workout-service 비즈니스 메트릭.
 *
 * Grafana 비즈니스 대시보드가 아래 메트릭명을 그대로 참조합니다:
 *   gymplan_workout_sessions_total     — 세션 시작 카운터
 *   gymplan_workout_sessions_completed — 세션 완료 카운터
 *   gymplan_sets_logged_total          — 세트 기록 카운터
 */
@Component
class WorkoutMetrics(registry: MeterRegistry) {
    val sessionsStarted: Counter =
        Counter.builder("gymplan_workout_sessions_total")
            .description("운동 세션 시작 수")
            .register(registry)

    val sessionsCompleted: Counter =
        Counter.builder("gymplan_workout_sessions_completed")
            .description("운동 세션 완료 수")
            .register(registry)

    val setsLogged: Counter =
        Counter.builder("gymplan_sets_logged_total")
            .description("세트 기록 수")
            .register(registry)
}

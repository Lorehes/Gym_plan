package com.gymplan.workout

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * workout-service 엔트리 포인트.
 *
 * 포트: 8084
 * DB:   MongoDB (gymplan_workout)
 * 이벤트: Kafka 발행 (workout.session.completed, workout.set.logged)
 *
 * 컴포넌트 스캔 범위:
 *   - com.gymplan.workout.*        : 본 서비스
 *   - com.gymplan.common.exception : GlobalExceptionHandler 등록용
 *   - com.gymplan.common.security  : CurrentUserIdArgumentResolver 등록용
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.gymplan.workout",
        "com.gymplan.common.exception",
        "com.gymplan.common.security",
    ],
)
class WorkoutServiceApplication

fun main(args: Array<String>) {
    runApplication<WorkoutServiceApplication>(*args)
}

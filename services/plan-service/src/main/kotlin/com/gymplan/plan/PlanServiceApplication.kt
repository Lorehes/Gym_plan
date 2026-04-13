package com.gymplan.plan

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * plan-service 엔트리 포인트.
 *
 * 포트: 8082
 * DB:   MySQL (gymplan_plan), Redis
 *
 * 컴포넌트 스캔 범위:
 *   - com.gymplan.plan.*           : 본 서비스
 *   - com.gymplan.common.exception : GlobalExceptionHandler 등록용
 *   - com.gymplan.common.security  : CurrentUserIdArgumentResolver 등록용
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.gymplan.plan",
        "com.gymplan.common.exception",
        "com.gymplan.common.security",
    ],
)
class PlanServiceApplication

fun main(args: Array<String>) {
    runApplication<PlanServiceApplication>(*args)
}

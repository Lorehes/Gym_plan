package com.gymplan.user

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * user-service 엔트리 포인트.
 *
 * 포트: 8081
 * DB:   MySQL (gymplan_user), Redis
 *
 * 컴포넌트 스캔 범위:
 *   - com.gymplan.user.*         : 본 서비스
 *   - com.gymplan.common.exception : GlobalExceptionHandler 등록용
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.gymplan.user",
        "com.gymplan.common.exception",
        "com.gymplan.common.security",
    ],
)
class UserServiceApplication

fun main(args: Array<String>) {
    runApplication<UserServiceApplication>(*args)
}

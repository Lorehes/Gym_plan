package com.gymplan.exercise

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * exercise-catalog 엔트리 포인트.
 *
 * 포트: 8083
 * DB:   MySQL (gymplan_exercise), Elasticsearch
 *
 * 컴포넌트 스캔 범위:
 *   - com.gymplan.exercise.*        : 본 서비스
 *   - com.gymplan.common.exception  : GlobalExceptionHandler 등록용
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.gymplan.exercise",
        "com.gymplan.common.exception",
        "com.gymplan.common.security",
    ],
)
class ExerciseCatalogApplication

fun main(args: Array<String>) {
    runApplication<ExerciseCatalogApplication>(*args)
}

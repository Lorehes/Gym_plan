package com.gymplan.notification

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * notification-service 엔트리 포인트.
 *
 * 포트: 8086
 * DB:   Redis (pub-sub, 설정 저장)
 * 입력: Kafka (workout.session.completed, user.registered)
 * 출력: SSE 타이머 스트림, FCM 푸시 알림
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.gymplan.notification",
        "com.gymplan.common.exception",
        "com.gymplan.common.security",
    ],
)
class NotificationServiceApplication

fun main(args: Array<String>) {
    runApplication<NotificationServiceApplication>(*args)
}

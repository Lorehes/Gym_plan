package com.gymplan.notification.application.dto

import java.time.Instant

data class TimerStartEvent(
    val sessionId: String,
    val restSeconds: Long,
    val exerciseName: String,
)

data class TimerEndEvent(
    val sessionId: String,
    val message: String = "휴식 완료! 다음 세트를 시작하세요.",
)

data class HeartbeatEvent(
    val timestamp: String = Instant.now().toString(),
)

/** workout-service가 Redis에 발행하는 메시지 형식. */
data class TimerPublishMessage(
    val restSeconds: Long,
    val exerciseName: String = "",
)

package com.gymplan.notification.infrastructure.idempotency

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Kafka At-Least-Once 중복 이벤트 방지.
 * Key: notification:idem:{eventType}:{userId}:{occurredAt}
 * TTL: 24시간 (중복 윈도우)
 */
@Service
class IdempotencyService(
    private val redisTemplate: StringRedisTemplate,
) {
    fun isAlreadyProcessed(
        eventType: String,
        userId: String,
        occurredAt: String,
    ): Boolean {
        val key = "notification:idem:$eventType:$userId:$occurredAt"
        val set = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL)
        return set == false
    }

    companion object {
        private val TTL: Duration = Duration.ofHours(24)
    }
}

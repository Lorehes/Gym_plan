package com.gymplan.notification.infrastructure.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

/**
 * user-service가 저장한 FCM 디바이스 토큰을 조회.
 * Key: user:fcm:{userId}
 *
 * 토큰 등록/갱신은 user-service 담당.
 */
@Repository
class FcmTokenRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun findByUserId(userId: Long): String? = redisTemplate.opsForValue().get("user:fcm:$userId")
}

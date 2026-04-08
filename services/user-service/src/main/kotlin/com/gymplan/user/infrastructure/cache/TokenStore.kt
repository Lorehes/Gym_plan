package com.gymplan.user.infrastructure.cache

import com.gymplan.common.security.JwtProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

/**
 * user-service 의 Redis 상태 저장소.
 *
 * 책임:
 *  - Refresh Token 저장/조회/삭제 (Rotation)
 *  - 사용자별 Refresh Token 인덱스 (전체 무효화용)
 *  - 로그인 실패 카운터 및 잠금 플래그 (브루트포스 방어)
 *
 * 키 네이밍 (docs/database/redis-keys.md + docs/specs/user-service.md §6.2):
 *
 *  user:refresh:{tokenHash}        → userId  TTL 7d
 *  user:refresh:index:{userId}     → SET of tokenHashes (TTL 7d)
 *  user:login:fail:{emailLower}    → Counter TTL 60s
 *  user:locked:{emailLower}        → "1"     TTL 5m
 *
 * 보안:
 *  - Refresh Token 의 원본은 절대 Redis 에 저장하지 않는다 (SHA-256 해시만 저장).
 */
@Component
class TokenStore(
    private val redis: StringRedisTemplate,
    private val jwtProperties: JwtProperties,
) {
    // ───── Refresh Token ─────

    /**
     * 새 Refresh Token 을 저장한다.
     * 같은 사용자가 여러 디바이스에서 로그인한 경우 중복 호출되어 인덱스에 추가된다.
     */
    fun saveRefreshToken(
        refreshToken: String,
        userId: Long,
    ) {
        val hash = hash(refreshToken)
        val ttl = jwtProperties.refreshTokenTtl
        redis.opsForValue().set(refreshKey(hash), userId.toString(), ttl)
        redis.opsForSet().add(indexKey(userId), hash)
        // 인덱스도 같은 TTL 부여 (넉넉히) — 가장 마지막에 추가된 토큰의 만료에 맞춤
        redis.expire(indexKey(userId), ttl)
    }

    /**
     * Refresh Token 이 유효하면 userId 반환, 없으면 null.
     * Redis 의 TTL 만료로 키가 사라진 경우도 null 로 처리됨.
     */
    fun findUserIdByRefreshToken(refreshToken: String): Long? {
        val hash = hash(refreshToken)
        val value = redis.opsForValue().get(refreshKey(hash)) ?: return null
        return value.toLongOrNull()
    }

    /**
     * 특정 Refresh Token 하나만 제거한다 (Rotation 시 사용).
     * 이미 삭제되어 없으면 false 반환 — 재사용 탐지에 활용.
     */
    fun deleteRefreshToken(
        refreshToken: String,
        userId: Long,
    ): Boolean {
        val hash = hash(refreshToken)
        val deleted = redis.delete(refreshKey(hash))
        redis.opsForSet().remove(indexKey(userId), hash)
        return deleted
    }

    /**
     * 사용자의 모든 Refresh Token 을 무효화한다.
     *  - 로그아웃 (해당 사용자의 전체 디바이스)
     *  - Refresh Token 재사용 탐지 대응 (TC-011)
     */
    fun revokeAllRefreshTokens(userId: Long) {
        val indexKey = indexKey(userId)
        val hashes = redis.opsForSet().members(indexKey).orEmpty()
        if (hashes.isNotEmpty()) {
            redis.delete(hashes.map { refreshKey(it) })
        }
        redis.delete(indexKey)
    }

    // ───── 브루트포스 방어 ─────

    /**
     * 해당 이메일이 잠금 상태인지 확인.
     */
    fun isLocked(email: String): Boolean = redis.hasKey(lockedKey(email))

    /**
     * 로그인 실패 1회를 기록. 임계치 도달 시 자동으로 잠금 상태 설정.
     *
     * @return 현재까지의 실패 횟수
     */
    fun recordLoginFailure(
        email: String,
        threshold: Int = 5,
    ): Long {
        val key = failKey(email)
        val count = redis.opsForValue().increment(key) ?: 1L
        if (count == 1L) {
            redis.expire(key, FAIL_WINDOW)
        }
        if (count >= threshold) {
            redis.opsForValue().set(lockedKey(email), "1", LOCK_TTL)
            redis.delete(key)
        }
        return count
    }

    /** 로그인 성공 시 실패 카운터 초기화. */
    fun clearLoginFailures(email: String) {
        redis.delete(failKey(email))
    }

    // ───── 키 빌더 / 해시 ─────

    private fun refreshKey(hash: String) = "user:refresh:$hash"

    private fun indexKey(userId: Long) = "user:refresh:index:$userId"

    private fun failKey(email: String) = "user:login:fail:${email.lowercase()}"

    private fun lockedKey(email: String) = "user:locked:${email.lowercase()}"

    companion object {
        private val FAIL_WINDOW: Duration = Duration.ofMinutes(1)
        private val LOCK_TTL: Duration = Duration.ofMinutes(5)

        /**
         * 토큰의 SHA-256 해시 (16진수 소문자).
         * 원본 토큰을 Redis 에 저장하지 않기 위한 키 파생 함수.
         */
        fun hash(token: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            return HexFormat.of().formatHex(digest)
        }
    }
}

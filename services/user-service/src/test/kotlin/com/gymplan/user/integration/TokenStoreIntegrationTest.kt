package com.gymplan.user.integration

import com.gymplan.user.infrastructure.cache.TokenStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * TokenStore 의 Redis 연산 통합 테스트.
 *
 * 실제 Redis (Testcontainers) 로 다음을 검증한다:
 *  - Refresh Token 저장/조회/단일 삭제
 *  - 사용자별 인덱스 무결성 (다중 디바이스 + 전체 무효화)
 *  - 브루트포스 카운터의 임계치 전이 (잠금 발동 시 fail 키가 정리되는지 포함)
 *
 * 명세: docs/specs/user-service.md §4.4 MUST — "Testcontainers로 실제 MySQL/Redis 사용 (모킹 금지)"
 */
@SpringBootTest
class TokenStoreIntegrationTest
    @Autowired
    constructor(
        private val tokenStore: TokenStore,
        private val redis: StringRedisTemplate,
    ) : AbstractIntegrationTest() {
        @BeforeEach
        fun flushRedis() {
            redis.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
        }

        // ─────────────── Refresh Token 기본 연산 ───────────────

        @Test
        @DisplayName("saveRefreshToken 후 findUserIdByRefreshToken 이 같은 userId 를 반환")
        fun save_then_find() {
            tokenStore.saveRefreshToken("RT-1", userId = 42L)

            val found = tokenStore.findUserIdByRefreshToken("RT-1")

            assertThat(found).isEqualTo(42L)
        }

        @Test
        @DisplayName("존재하지 않는 토큰 조회는 null")
        fun find_missing() {
            val found = tokenStore.findUserIdByRefreshToken("never-saved")
            assertThat(found).isNull()
        }

        @Test
        @DisplayName("deleteRefreshToken 은 키와 인덱스 엔트리를 모두 제거한다")
        fun delete_removesKeyAndIndex() {
            tokenStore.saveRefreshToken("RT-2", userId = 1L)
            val indexBefore = redis.opsForSet().members("user:refresh:index:1")
            assertThat(indexBefore).hasSize(1)

            val deleted = tokenStore.deleteRefreshToken("RT-2", userId = 1L)

            assertThat(deleted).isTrue
            assertThat(tokenStore.findUserIdByRefreshToken("RT-2")).isNull()
            val indexAfter = redis.opsForSet().members("user:refresh:index:1")
            assertThat(indexAfter).isEmpty()
        }

        @Test
        @DisplayName("이미 삭제된 토큰을 다시 삭제하면 false (재사용 탐지에 활용)")
        fun delete_returnsFalseForMissing() {
            val result = tokenStore.deleteRefreshToken("never-existed", userId = 1L)
            assertThat(result).isFalse
        }

        // ─────────────── 다중 디바이스 + 전체 무효화 ───────────────

        @Test
        @DisplayName("같은 사용자의 여러 Refresh Token 을 한 번에 폐기할 수 있다")
        fun revokeAll_clearsAllTokensForUser() {
            tokenStore.saveRefreshToken("RT-A", userId = 7L)
            tokenStore.saveRefreshToken("RT-B", userId = 7L)
            tokenStore.saveRefreshToken("RT-C", userId = 7L)

            // 다른 사용자의 토큰은 영향받지 않아야 한다
            tokenStore.saveRefreshToken("RT-OTHER", userId = 99L)

            tokenStore.revokeAllRefreshTokens(userId = 7L)

            assertThat(tokenStore.findUserIdByRefreshToken("RT-A")).isNull()
            assertThat(tokenStore.findUserIdByRefreshToken("RT-B")).isNull()
            assertThat(tokenStore.findUserIdByRefreshToken("RT-C")).isNull()
            assertThat(redis.opsForSet().members("user:refresh:index:7")).isNullOrEmpty()

            // 다른 사용자는 그대로
            assertThat(tokenStore.findUserIdByRefreshToken("RT-OTHER")).isEqualTo(99L)
        }

        @Test
        @DisplayName("revokeAllRefreshTokens 는 토큰이 없는 사용자에게 호출해도 안전")
        fun revokeAll_noopForUserWithNoTokens() {
            tokenStore.revokeAllRefreshTokens(userId = 12345L)
            // 예외 없이 통과하면 OK
        }

        // ─────────────── 브루트포스 방어 ───────────────

        @Test
        @DisplayName("TC-007: 임계치 직전까지는 잠금되지 않고, 임계치 도달 시 잠금 + fail 키 정리")
        fun loginFailure_thresholdTransition() {
            val email = "bruteforce@example.com"

            // 4회 실패 — 아직 잠금 아님
            repeat(4) { tokenStore.recordLoginFailure(email) }
            assertThat(tokenStore.isLocked(email)).isFalse
            assertThat(redis.hasKey("user:login:fail:$email")).isTrue

            // 5회째 — 임계치 도달, 잠금 상태 전이
            val fifth = tokenStore.recordLoginFailure(email)
            assertThat(fifth).isEqualTo(5L)
            assertThat(tokenStore.isLocked(email)).isTrue

            // 잠금 발동 시 fail 카운터는 삭제된다 (TokenStore 구현 계약)
            assertThat(redis.hasKey("user:login:fail:$email")).isFalse
        }

        @Test
        @DisplayName("clearLoginFailures 는 카운터를 초기화한다")
        fun clearLoginFailures_resets() {
            val email = "reset@example.com"
            tokenStore.recordLoginFailure(email)
            tokenStore.recordLoginFailure(email)
            assertThat(redis.hasKey("user:login:fail:$email")).isTrue

            tokenStore.clearLoginFailures(email)

            assertThat(redis.hasKey("user:login:fail:$email")).isFalse
            assertThat(tokenStore.isLocked(email)).isFalse
        }

        @Test
        @DisplayName("이메일 대소문자는 키 수준에서 정규화된다 (lowercase)")
        fun emailKey_isCaseInsensitive() {
            tokenStore.recordLoginFailure("MiXeD@Case.com")
            assertThat(redis.hasKey("user:login:fail:mixed@case.com")).isTrue
        }
    }

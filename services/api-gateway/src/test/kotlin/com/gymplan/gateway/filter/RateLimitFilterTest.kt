package com.gymplan.gateway.filter

import com.gymplan.gateway.AbstractGatewayTest
import com.gymplan.gateway.TestRouteConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.test.StepVerifier

/**
 * RateLimitFilter 통합 테스트.
 *
 * 테스트 설정 (AbstractGatewayTest):
 *   - IP 제한: 20 req/min
 *   - User 제한: 5 req/min
 *
 * 실제 Redis Testcontainer 를 사용해 INCR/EXPIRE 동작을 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestRouteConfig::class)
class RateLimitFilterTest : AbstractGatewayTest() {
    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var redis: ReactiveStringRedisTemplate

    @Test
    @DisplayName("IP rate limit 초과 시 429 RATE_LIMIT_EXCEEDED (whitelist 경로, 인증 불필요)")
    fun ipRateLimitExceeded() {
        clearRateKeys()

        val limit = 20

        // whitelist 경로이자 gateway 라우트인 /api/v1/auth/login 사용 (401 회피).
        // 인증 없는 요청이므로 User rate limit 은 적용되지 않고 IP rate limit 만 동작한다.
        repeat(limit) {
            val status =
                client.post().uri("/api/v1/auth/login")
                    .exchange()
                    .returnResult(String::class.java)
                    .status.value()
            assertThat(status).isNotEqualTo(429)
        }

        // limit+1 번째에서 429
        client.post().uri("/api/v1/auth/login")
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error.code").isEqualTo("RATE_LIMIT_EXCEEDED")
    }

    @Test
    @DisplayName("User rate limit 초과 시 429 RATE_LIMIT_EXCEEDED")
    fun userRateLimitExceeded() {
        clearRateKeys()

        val token = testJwtProvider.createAccessToken(999L, "user-rl@gymplan.io")
        val userLimit = 5

        // userLimit 번까지는 통과 (IP 제한 20 > User 제한 5 이므로 User 제한이 먼저 도달)
        repeat(userLimit) {
            client.get().uri("/api/v1/plans")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .exchange()
                .expectStatus().value { status -> assert(status != 429) { "제한 전인데 429" } }
        }

        // userLimit+1 번째에서 429
        client.get().uri("/api/v1/plans")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error.code").isEqualTo("RATE_LIMIT_EXCEEDED")
    }

    private fun clearRateKeys() {
        // rate:* 키를 모두 삭제해 테스트 격리
        val keys = redis.keys("rate:*").collectList().block() ?: emptyList()
        if (keys.isNotEmpty()) {
            StepVerifier.create(redis.delete(*keys.toTypedArray()))
                .expectNextCount(1)
                .verifyComplete()
        }
    }
}

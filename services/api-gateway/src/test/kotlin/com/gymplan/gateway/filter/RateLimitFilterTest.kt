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
 *   - IP 제한: 5 req/min
 *   - User 제한: 10 req/min
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
    @DisplayName("IP rate limit 초과 시 429 RATE_LIMIT_EXCEEDED")
    fun ipRateLimitExceeded() {
        // Redis 에서 rate:ip:* 키 초기화
        clearRateKeys()

        val token = testJwtProvider.createAccessToken(1L, "rate-test@gymplan.io")
        val limit = 5

        // limit 번까지는 통과 (401 아닌 것만 확인)
        repeat(limit) {
            client.get().uri("/api/v1/plans")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .exchange()
                .expectStatus().value { status -> assert(status != 429) { "제한 전인데 429" } }
        }

        // limit+1 번째에서 429
        client.get().uri("/api/v1/plans")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error.code").isEqualTo("RATE_LIMIT_EXCEEDED")
    }

    @Test
    @DisplayName("인증 없는 요청도 IP rate limit 적용 (whitelist 경로)")
    fun unauthenticatedIpRateLimit() {
        clearRateKeys()

        val limit = 5

        // whitelist 경로이자 gateway 라우트인 /api/v1/auth/login 사용 (401 회피).
        // 다운스트림 서비스가 없으므로 502/503 이 반환되지만, rate limit 전에 필터가 동작한다.
        repeat(limit) {
            val status =
                client.post().uri("/api/v1/auth/login")
                    .exchange()
                    .returnResult(String::class.java)
                    .status.value()
            assertThat(status).isNotEqualTo(429)
        }

        client.post().uri("/api/v1/auth/login")
            .exchange()
            .expectStatus().isEqualTo(429)
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

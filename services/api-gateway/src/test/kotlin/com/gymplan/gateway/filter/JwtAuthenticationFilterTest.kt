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
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * JwtAuthenticationFilter 통합 테스트.
 *
 * docs/context/security-guide.md "Gateway 필터" 단계를 검증:
 *   1. X-User-* 헤더 스푸핑 차단
 *   2. whitelist 경로 통과
 *   3. 토큰 없음/잘못된 토큰 → 401
 *   4. 유효한 토큰 → 하위 서비스로 전달 (401이 아닌 것으로 확인)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestRouteConfig::class)
class JwtAuthenticationFilterTest : AbstractGatewayTest() {
    @Autowired
    lateinit var client: WebTestClient

    // ─── whitelist 경로 ───

    @Test
    @DisplayName("whitelist 경로(/actuator/health)는 토큰 없이도 통과")
    fun whitelistPathAllowed() {
        client.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    @DisplayName("whitelist 경로(/api/v1/auth/login)는 토큰 없이도 통과")
    fun whitelistAuthPathAllowed() {
        // 백엔드 서비스가 없으므로 502/503 등이 기대됨. 401이 아니면 필터 통과.
        val status =
            client.post().uri("/api/v1/auth/login")
                .exchange()
                .returnResult(String::class.java)
                .status.value()

        assertThat(status).isNotEqualTo(401)
    }

    // ─── 인증 실패 ───

    @Test
    @DisplayName("Authorization 헤더 없으면 401")
    fun missingAuthorizationHeader() {
        client.get().uri("/api/v1/plans")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error.code").isEqualTo("AUTH_INVALID_TOKEN")
    }

    @Test
    @DisplayName("잘못된 JWT 토큰이면 401")
    fun invalidToken() {
        client.get().uri("/api/v1/plans")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error.code").isEqualTo("AUTH_INVALID_TOKEN")
    }

    @Test
    @DisplayName("Bearer prefix 없으면 401")
    fun missingBearerPrefix() {
        client.get().uri("/api/v1/plans")
            .header(HttpHeaders.AUTHORIZATION, "some-token")
            .exchange()
            .expectStatus().isUnauthorized
    }

    // ─── 헤더 스푸핑 차단 ───

    @Test
    @DisplayName("외부에서 X-User-Id 헤더 직접 주입 시 401")
    fun blockExternalUserIdInjection() {
        client.get().uri("/api/v1/plans")
            .header("X-User-Id", "999")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("AUTH_INVALID_TOKEN")
    }

    @Test
    @DisplayName("외부에서 X-User-Email 헤더 직접 주입 시 401")
    fun blockExternalUserEmailInjection() {
        client.get().uri("/api/v1/plans")
            .header("X-User-Email", "hacker@evil.com")
            .exchange()
            .expectStatus().isUnauthorized
    }

    // ─── 인증 성공 ───

    @Test
    @DisplayName("유효한 Access Token → 필터 통과 (401이 아님)")
    fun validAccessTokenPassesFilter() {
        val token = testJwtProvider.createAccessToken(42L, "test@gymplan.io")

        val status =
            client.get().uri("/api/v1/plans")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .exchange()
                .returnResult(String::class.java)
                .status.value()

        assertThat(status)
            .describedAs("유효한 토큰이면 필터를 통과해야 함 (401이 아닌 것으로 확인)")
            .isNotEqualTo(401)
    }

    @Test
    @DisplayName("Refresh Token 은 Access Token 이 아니므로 401")
    fun refreshTokenRejected() {
        val refreshToken = testJwtProvider.createRefreshToken(42L)

        client.get().uri("/api/v1/plans")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $refreshToken")
            .exchange()
            .expectStatus().isUnauthorized
    }
}

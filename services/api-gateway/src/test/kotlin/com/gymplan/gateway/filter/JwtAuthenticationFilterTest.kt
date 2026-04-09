package com.gymplan.gateway.filter

import com.gymplan.gateway.AbstractGatewayTest
import com.gymplan.gateway.TestRouteConfig
import io.jsonwebtoken.Jwts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

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

    @Test
    @DisplayName("만료된 Access Token 이면 401 AUTH_EXPIRED_TOKEN")
    fun expiredAccessTokenRejected() {
        val expiredToken = createExpiredAccessToken(42L, "expired@gymplan.io")

        client.get().uri("/api/v1/plans")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $expiredToken")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.error.code").isEqualTo("AUTH_EXPIRED_TOKEN")
    }

    /**
     * 테스트용 만료된 Access Token 생성.
     * JwtProvider 는 만료 토큰 생성 메서드를 제공하지 않으므로 jjwt 로 직접 빌드한다.
     */
    private fun createExpiredAccessToken(
        userId: Long,
        email: String,
    ): String {
        val pem = testKeys.privateKeyPem
        val der =
            Base64.getDecoder().decode(
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\s".toRegex(), ""),
            )
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
        val past = Instant.now().minusSeconds(120)
        return Jwts.builder()
            .issuer("gymplan-test")
            .subject(userId.toString())
            .claim("email", email)
            .claim("type", "access")
            .issuedAt(Date.from(past.minusSeconds(60)))
            .expiration(Date.from(past))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()
    }
}

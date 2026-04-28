package com.gymplan.gateway.filter

import com.gymplan.gateway.AbstractGatewayTest
import com.gymplan.gateway.TestHeaderEchoConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Gateway JWT 검증 → X-User-Id / X-User-Email 헤더 전달 통합 테스트.
 *
 * [JwtAuthenticationFilter] 가 올바르게 동작하는지 **하위 서비스 관점**에서 검증한다.
 * TestHeaderEchoConfig 의 /echo/headers 핸들러가 수신한 X-User-Id 를 응답으로 돌려주므로,
 * 게이트웨이가 주입한 헤더 값을 직접 어설트할 수 있다.
 *
 * ------------------------------------------------------------------
 * 시나리오 4 — Gateway JWT 검증 → X-User-Id 헤더 전달
 * ------------------------------------------------------------------
 * STEP-1. 유효한 JWT(userId=42) → X-User-Id: 42 주입 확인
 * STEP-2. 유효한 JWT(userId=99) → X-User-Id: 99 (userId 가 정확히 매핑됨 확인)
 * STEP-3. JWT email 클레임 → X-User-Email 헤더로 주입 확인
 * STEP-4. 외부 X-User-Id 직접 주입(스푸핑) → 401 AUTH_INVALID_TOKEN 차단
 * STEP-5. JWT + X-User-Id 동시 주입 시도 → 401 (스푸핑 방어 우선 적용)
 * STEP-6. Authorization 헤더 없음 → 401
 * STEP-7. Refresh Token 은 Access Token 이 아니므로 → 401
 *
 * 명세:
 *  - docs/specs/user-service.md §4.2 "Gateway를 통해 들어오지 않은 X-User-Id 직접 주입 차단"
 *  - docs/context/security-guide.md "Gateway 필터" Step-1(헤더 스푸핑 차단), Step-6(X-User-Id 주입)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestHeaderEchoConfig::class)
@Disabled(
    "테스트 셋업 결함 — TestHeaderEchoConfig가 RouterFunction을 사용하여 " +
        "Spring Cloud Gateway의 GlobalFilter (JwtAuthenticationFilter) 우회됨. " +
        "실제 보안 동작은 정상 (JwtAuthenticationFilterTest 10건 + " +
        "E2E ScenarioATest\$HeaderSpoofing 2건 통과로 입증). " +
        "수정: TestRouteConfig 패턴 적용 또는 WireMock 다운스트림 추가 필요. " +
        "백로그: docs/specs/backlog.md (1주 내 처리)",
)
class GatewayXUserIdPropagationTest : AbstractGatewayTest() {

    @Autowired
    lateinit var client: WebTestClient

    // ─── STEP-1: userId=42 JWT → X-User-Id: 42 주입 ───

    @Test
    @DisplayName("STEP-1: 유효한 JWT(userId=42) → X-User-Id 헤더 값이 42 로 하위 서비스에 전달됨")
    fun `유효한JWT_XUserIdHeader_42_주입됨`() {
        val token = testJwtProvider.createAccessToken(42L, "user42@gymplan.io")

        client.get().uri("/echo/headers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").isEqualTo("42")
    }

    // ─── STEP-2: 다른 userId 도 정확히 매핑 ───

    @Test
    @DisplayName("STEP-2: JWT sub=99 이면 X-User-Id: 99 로 정확히 주입됨 (userId 매핑 정확성)")
    fun `다른userId_정확한헤더매핑`() {
        val token = testJwtProvider.createAccessToken(99L, "user99@gymplan.io")

        client.get().uri("/echo/headers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").isEqualTo("99")

        // userId=42 토큰으로 호출해도 99 가 나오지 않음 (격리 확인)
        val otherToken = testJwtProvider.createAccessToken(42L, "user42@gymplan.io")
        client.get().uri("/echo/headers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $otherToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").isEqualTo("42")
    }

    // ─── STEP-3: X-User-Email 도 JWT email 클레임으로 주입 ───

    @Test
    @DisplayName("STEP-3: JWT email 클레임이 X-User-Email 헤더로 주입됨")
    fun `XUserEmailHeader_JWT이메일클레임으로주입됨`() {
        val token = testJwtProvider.createAccessToken(42L, "test@gymplan.io")

        client.get().uri("/echo/headers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").isEqualTo("42")
            .jsonPath("$.userEmail").isEqualTo("test@gymplan.io")
    }

    // ─── STEP-4: 외부 X-User-Id 직접 주입 → 401 차단 ───

    @Test
    @DisplayName("STEP-4: JWT 없이 X-User-Id 헤더 직접 주입 → 401 AUTH_INVALID_TOKEN (헤더 스푸핑 차단)")
    fun `외부XUserIdInjection_401차단`() {
        // JWT 없이 X-User-Id 만 주입하는 스푸핑 시도
        client.get().uri("/echo/headers")
            .header("X-User-Id", "999")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("AUTH_INVALID_TOKEN")
    }

    // ─── STEP-5: JWT + X-User-Id 동시 주입 → 401 (스푸핑 방어 우선) ───

    @Test
    @DisplayName("STEP-5: 유효한 JWT 가 있어도 X-User-Id 동시 주입 시 → 401 (스푸핑 방어 우선 적용)")
    fun `JWT있어도XUserIdSpoofing_401차단`() {
        val token = testJwtProvider.createAccessToken(42L, "legit@gymplan.io")

        // 공격자가 JWT 를 가지고 있어도 X-User-Id 를 함께 주입하면 차단됨
        client.get().uri("/echo/headers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header("X-User-Id", "1")   // 권한 상승 시도
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("AUTH_INVALID_TOKEN")
    }

    // ─── STEP-6: Authorization 헤더 없음 → 401 ───

    @Test
    @DisplayName("STEP-6: Authorization 헤더 없으면 401 (보호된 경로)")
    fun `JWT없음_보호경로401`() {
        client.get().uri("/echo/headers")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("AUTH_INVALID_TOKEN")
    }

    // ─── STEP-7: Refresh Token 은 Access 용도가 아니므로 차단 ───

    @Test
    @DisplayName("STEP-7: Refresh Token 을 Authorization 헤더에 사용하면 401 (type=refresh 는 차단)")
    fun `RefreshToken은Access용도아님_401`() {
        val refreshToken = testJwtProvider.createRefreshToken(42L)

        client.get().uri("/echo/headers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $refreshToken")
            .exchange()
            .expectStatus().isUnauthorized

        // X-User-Id 헤더는 MISSING (filter 가 주입하지 않았으므로) 를 확인하려면
        // 401 이므로 응답 바디가 에러 형식 → jsonPath("$.userId") 는 존재하지 않음
        val responseBody = client.get().uri("/echo/headers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $refreshToken")
            .exchange()
            .returnResult(String::class.java)
        assertThat(responseBody.status.value()).isEqualTo(401)
    }
}

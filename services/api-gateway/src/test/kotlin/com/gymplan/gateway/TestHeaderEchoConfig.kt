package com.gymplan.gateway

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

/**
 * JWT → X-User-Id 헤더 전달 검증용 테스트 핸들러.
 *
 * /echo/headers 요청을 받아 [JwtAuthenticationFilter] 가 주입한 X-User-Id, X-User-Email 값을
 * 응답 바디에 그대로 돌려준다.
 *
 * 응답 형식:
 * ```json
 * {
 *   "userId":    "42",
 *   "userEmail": "user42@gymplan.io"
 * }
 * ```
 *
 * - 필터가 헤더를 주입하지 않으면 "MISSING" 을 반환한다.
 * - [GatewayXUserIdPropagationTest] 에서 `@Import(TestHeaderEchoConfig::class)` 로 등록된다.
 */
@TestConfiguration
class TestHeaderEchoConfig {
    @Bean
    fun headerEchoRoutes(): RouterFunction<ServerResponse> =
        router {
            GET("/echo/headers") { request ->
                val userId = request.headers().firstHeader("X-User-Id") ?: "MISSING"
                val userEmail = request.headers().firstHeader("X-User-Email") ?: "MISSING"
                ServerResponse.status(HttpStatus.OK)
                    .bodyValue("""{"userId":"$userId","userEmail":"$userEmail"}""")
            }
        }
}

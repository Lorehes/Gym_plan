package com.gymplan.gateway

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

/**
 * 테스트 전용 핸들러.
 *
 * 실제 다운스트림 서비스가 없으므로 필터 검증용 /echo 엔드포인트를 등록한다.
 * 이 핸들러에 도달하면 필터를 통과한 것이다.
 */
@TestConfiguration
class TestRouteConfig {
    @Bean
    fun echoRoutes(): RouterFunction<ServerResponse> =
        router {
            GET("/echo") {
                ServerResponse.status(HttpStatus.OK).bodyValue("""{"success":true,"data":"echo"}""")
            }
        }
}

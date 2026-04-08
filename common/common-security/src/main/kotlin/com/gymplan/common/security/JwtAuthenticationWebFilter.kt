package com.gymplan.common.security

import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Spring Cloud Gateway 용 JWT 검증 필터.
 *
 * docs/context/security-guide.md "Gateway 필터" 단계와 1:1 매칭:
 *   1. Authorization 헤더에서 Bearer 추출
 *   2. RS256 공개키로 검증 (JwtProvider)
 *   3. 통과 시 X-User-Id / X-User-Email 헤더를 다운스트림 요청에 주입
 *   4. 외부에서 위 두 헤더를 직접 주입 시도 시 차단
 *
 * 인증 제외 경로 (whitelist) 는 생성자에서 주입.
 *
 * 주의: 하위 서비스는 이 필터를 사용하지 않는다.
 *      하위 서비스는 X-User-Id 헤더만 신뢰 (보안 가이드 참조).
 */
class JwtAuthenticationWebFilter(
    private val jwtProvider: JwtProvider,
    private val whitelistPaths: List<String> = listOf(
        "/api/v1/auth/login",
        "/api/v1/auth/signup",
        "/api/v1/auth/refresh",
        "/actuator/health",
    ),
) : WebFilter {
    private val log = LoggerFactory.getLogger(JwtAuthenticationWebFilter::class.java)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        val path = request.path.value()

        // 1. 외부에서 X-User-* 헤더 주입 시도 차단 (헤더 스푸핑 방지)
        if (request.headers.containsKey(HEADER_USER_ID) || request.headers.containsKey(HEADER_USER_EMAIL)) {
            log.warn("외부 X-User-* 헤더 주입 시도 차단: path={}", path)
            return Mono.error(UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN))
        }

        // 2. whitelist 경로는 검증 없이 통과
        if (whitelistPaths.any { path.startsWith(it) }) {
            return chain.filter(exchange)
        }

        // 3. Authorization 헤더 확인
        val authHeader = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?: return Mono.error(UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN))

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            return Mono.error(UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN))
        }
        val token = authHeader.substring(BEARER_PREFIX.length).trim()

        // 4. 검증 (실패 시 jwtProvider 가 UnauthorizedException 발생)
        val payload = jwtProvider.parse(token)
        if (!payload.isAccess()) {
            return Mono.error(UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN))
        }

        // 5. 다운스트림 요청 헤더에 사용자 정보 주입
        val mutated: ServerHttpRequest =
            request.mutate()
                .header(HEADER_USER_ID, payload.userId.toString())
                .apply { payload.email?.let { header(HEADER_USER_EMAIL, it) } }
                .build()

        return chain.filter(exchange.mutate().request(mutated).build())
    }

    companion object {
        const val HEADER_USER_ID = "X-User-Id"
        const val HEADER_USER_EMAIL = "X-User-Email"
        private const val BEARER_PREFIX = "Bearer "
    }
}

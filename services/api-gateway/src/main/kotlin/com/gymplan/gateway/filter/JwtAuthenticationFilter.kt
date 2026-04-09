package com.gymplan.gateway.filter

import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.UnauthorizedException
import com.gymplan.common.security.JwtPayload
import com.gymplan.common.security.JwtProvider
import com.gymplan.gateway.config.GatewaySecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Gateway JWT 인증 필터.
 *
 * docs/context/security-guide.md "Gateway 필터" 단계와 1:1 매칭:
 *   1. 외부 X-User-* 헤더 주입 시도 차단 (헤더 스푸핑 방지)
 *   2. whitelist 경로는 검증 없이 통과
 *   3. Authorization 헤더에서 Bearer 토큰 추출
 *   4. RS256 공개키로 서명 검증 (JwtProvider)
 *   5. (옵션) Redis 에서 user:session:{userId} 존재 여부 확인 — sessionCheckEnabled=true 시
 *   6. X-User-Id / X-User-Email 헤더를 다운스트림 요청에 주입
 *
 * 설계 메모:
 *  - common-security 의 JwtAuthenticationWebFilter 는 Mono.error 만 던지므로
 *    GlobalFilter 가 아닌 일반 WebFilter 다. Gateway 는 라우트 단위 정렬이 필요해
 *    GlobalFilter + Ordered 로 별도 구현한다.
 *  - 실패 시 UnauthorizedException 을 던지고, GatewayErrorHandler 가 단일 경로로 변환한다.
 *  - RateLimitFilter 보다 먼저 동작해야 user 단위 Rate Limit 키가 채워진다 → @Order 작은 값.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val redis: ReactiveStringRedisTemplate,
    private val properties: GatewaySecurityProperties,
) : GlobalFilter, Ordered {
    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val request = exchange.request
        val path = request.path.value()

        // 1. 헤더 스푸핑 차단 — 외부에서 X-User-* 직접 주입 시도
        if (request.headers.containsKey(HEADER_USER_ID) || request.headers.containsKey(HEADER_USER_EMAIL)) {
            log.warn("외부 X-User-* 헤더 주입 시도 차단: path={}", path)
            return Mono.error(UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN))
        }

        // 2. whitelist 경로는 검증 없이 통과
        if (isWhitelisted(path)) {
            return chain.filter(exchange)
        }

        // 3. Authorization 헤더 → Bearer 토큰
        val authHeader = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (authHeader.isNullOrBlank() || !authHeader.startsWith(BEARER_PREFIX)) {
            return Mono.error(UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN))
        }
        val token = authHeader.substring(BEARER_PREFIX.length).trim()
        if (token.isEmpty()) {
            return Mono.error(UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN))
        }

        // 4. RS256 서명/만료 검증 — JwtProvider 가 실패 시 UnauthorizedException 발생
        val payload: JwtPayload =
            try {
                jwtProvider.parse(token)
            } catch (e: UnauthorizedException) {
                return Mono.error(e)
            }
        if (!payload.isAccess()) {
            return Mono.error(UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN))
        }

        // 5. (옵션) Redis 세션 유효성 확인
        return verifySession(payload.userId)
            .then(Mono.defer { chain.filter(exchange.mutate().request(injectUserHeaders(request, payload)).build()) })
    }

    /**
     * docs/database/redis-keys.md `user:session:{userId}` 의 존재 여부로 활성 세션을 확인한다.
     *
     * sessionCheckEnabled=false 면 즉시 통과 (현재 user-service 가 세션 키를 채우지 않으므로 기본값).
     * 활성화 시 키가 없으면 만료/로그아웃된 세션으로 간주하고 401 반환.
     */
    private fun verifySession(userId: Long): Mono<Void> {
        if (!properties.security.sessionCheckEnabled) {
            return Mono.empty()
        }
        val key = "user:session:$userId"
        return redis.hasKey(key)
            .flatMap { exists ->
                if (exists == true) {
                    Mono.empty()
                } else {
                    log.info("세션 만료 또는 로그아웃됨: userId={}", userId)
                    Mono.error(UnauthorizedException(ErrorCode.AUTH_EXPIRED_TOKEN))
                }
            }
    }

    private fun injectUserHeaders(
        request: ServerHttpRequest,
        payload: JwtPayload,
    ): ServerHttpRequest {
        val builder =
            request.mutate()
                .header(HEADER_USER_ID, payload.userId.toString())
        payload.email?.let { builder.header(HEADER_USER_EMAIL, it) }
        return builder.build()
    }

    /**
     * whitelist 경로 매칭.
     *
     * 정확히 일치하거나, 경로 뒤에 '/' 가 이어지는 경우만 허용한다.
     * startsWith 만 사용하면 `/api/v1/auth/login-admin` 같은 의도치 않은
     * 경로가 화이트리스트될 수 있다.
     */
    private fun isWhitelisted(path: String): Boolean =
        properties.security.whitelistPaths.any { pattern ->
            path == pattern || path.startsWith("$pattern/")
        }

    /** RateLimitFilter 보다 먼저 (작을수록 먼저). */
    override fun getOrder(): Int = ORDER

    companion object {
        const val HEADER_USER_ID = "X-User-Id"
        const val HEADER_USER_EMAIL = "X-User-Email"
        private const val BEARER_PREFIX = "Bearer "

        /** RateLimitFilter 보다 먼저 동작해야 함. RateLimit ORDER 보다 작은 값. */
        const val ORDER = -100
    }
}

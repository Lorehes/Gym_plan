package com.gymplan.gateway.filter

import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.GymPlanException
import com.gymplan.gateway.config.GatewaySecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Redis 기반 Rate Limit 필터.
 *
 * 정책 (docs/context/security-guide.md §API 보안 / Rate Limiting):
 *   - IP 단위:    100 req/min  (인증 여부와 무관, 모든 요청)
 *   - 사용자 단위: 300 req/min  (X-User-Id 가 주입된 인증 요청만)
 *   - 초과 시:     HTTP 429 + RATE_LIMIT_EXCEEDED
 *
 * 키 (docs/database/redis-keys.md):
 *   rate:ip:{ip}:{minute}        Counter (TTL = window)
 *   rate:{userId}:{minute}        Counter (TTL = window)
 *
 * 분 단위 윈도우는 epoch-minute 으로 키를 분리해 시간 경계 race 를 회피한다.
 * INCR 후 첫 1 응답일 때만 EXPIRE 를 설정해 불필요한 EXPIRE 호출을 줄인다.
 *
 * Order: JwtAuthenticationFilter 다음 (X-User-Id 가 채워진 뒤) 동작.
 */
@Component
class RateLimitFilter(
    private val redis: ReactiveStringRedisTemplate,
    private val properties: GatewaySecurityProperties,
) : GlobalFilter, Ordered {
    private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        if (!properties.rateLimit.enabled) {
            return chain.filter(exchange)
        }

        val window = properties.rateLimit.window
        val minuteBucket = bucketKey(window)
        val ip = clientIp(exchange)

        val ipKey = "rate:ip:$ip:$minuteBucket"
        val ipCheck: Mono<Void> =
            increment(ipKey, window).flatMap<Void> { count ->
                if (count > properties.rateLimit.ipLimitPerMin) {
                    log.info("IP rate limit 초과: ip={}, count={}", ip, count)
                    Mono.error(GymPlanException(ErrorCode.RATE_LIMIT_EXCEEDED))
                } else {
                    Mono.empty()
                }
            }

        val userId = exchange.request.headers.getFirst(JwtAuthenticationFilter.HEADER_USER_ID)
        val userCheck: Mono<Void> =
            if (userId.isNullOrBlank()) {
                Mono.empty()
            } else {
                val userKey = "rate:$userId:$minuteBucket"
                increment(userKey, window).flatMap<Void> { count ->
                    if (count > properties.rateLimit.userLimitPerMin) {
                        log.info("User rate limit 초과: userId={}, count={}", userId, count)
                        Mono.error(GymPlanException(ErrorCode.RATE_LIMIT_EXCEEDED))
                    } else {
                        Mono.empty()
                    }
                }
            }

        return ipCheck.then(userCheck).then(chain.filter(exchange))
    }

    /**
     * INCR 후 카운터가 1이면 (= 새로 만들어진 키) EXPIRE 를 설정한다.
     * 같은 분 윈도우 내 후속 요청은 EXPIRE 를 건너뛴다.
     */
    private fun increment(
        key: String,
        ttl: Duration,
    ): Mono<Long> =
        redis.opsForValue().increment(key)
            .flatMap { count ->
                if (count == 1L) {
                    redis.expire(key, ttl).thenReturn(count)
                } else {
                    Mono.just(count)
                }
            }

    /**
     * 분 단위 시간 버킷. 윈도우가 다르더라도 정수 분으로 키를 분리하면
     * 시간 경계의 race 가 자연스럽게 해소된다 (이전 분 키는 만료되어 사라짐).
     */
    private fun bucketKey(window: Duration): String {
        val now = Instant.now().atOffset(ZoneOffset.UTC)
        // 윈도우가 60s 의 정수배가 아니면 단순 epoch 단위로 fallback
        val seconds = window.seconds
        return if (seconds % 60L == 0L) {
            now.toEpochSecond().div(60L).toString()
        } else {
            now.toEpochSecond().div(seconds).toString()
        }
    }

    /**
     * 클라이언트 실제 IP 를 얻는다.
     * X-Forwarded-For 가 있으면 첫 번째 항목을 사용 (LB/Ingress 통과).
     * 신뢰할 수 있는 프록시 뒤에 있다는 가정하에서만 사용해야 한다.
     */
    private fun clientIp(exchange: ServerWebExchange): String {
        val xff = exchange.request.headers.getFirst("X-Forwarded-For")
        if (!xff.isNullOrBlank()) {
            return xff.substringBefore(',').trim()
        }
        return exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"
    }

    /** JwtAuthenticationFilter 다음에 실행. */
    override fun getOrder(): Int = ORDER

    companion object {
        const val ORDER = -50
    }
}

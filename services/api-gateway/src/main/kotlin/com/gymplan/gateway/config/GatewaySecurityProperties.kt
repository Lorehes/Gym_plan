package com.gymplan.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Gateway 의 인증/Rate Limit 정책 설정.
 *
 * application.yml 매핑:
 *
 *   gymplan:
 *     gateway:
 *       security:
 *         whitelist-paths:
 *           - /api/v1/auth/login
 *           - /api/v1/auth/register
 *           - /api/v1/auth/refresh
 *           - /actuator/health
 *         session-check-enabled: false
 *       rate-limit:
 *         enabled: true
 *         ip-limit-per-min: 100
 *         user-limit-per-min: 300
 *
 * 키 (RS256 PEM) 는 별도 `gymplan.jwt.public-key` (Vault 주입) 로 받는다.
 *
 * sessionCheckEnabled 는 docs/context/security-guide.md "Gateway 필터 §3 Redis 세션 유효성 확인"
 * 단계의 토글이다. user-service 가 user:session:{userId} 를 채우기 시작하면 true 로 설정한다.
 * 현재 user-service (commit 514ed9d 기준) 는 Refresh Token 만 Redis 에 저장하고 Access Token
 * 세션 키는 채우지 않으므로 기본값 false. 활성화 시 모든 인증 요청에 대해 GET 1회가 추가된다.
 */
@ConfigurationProperties(prefix = "gymplan.gateway")
data class GatewaySecurityProperties(
    val security: Security = Security(),
    val rateLimit: RateLimit = RateLimit(),
) {
    data class Security(
        val whitelistPaths: List<String> =
            listOf(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/refresh",
                "/actuator/health",
                "/actuator/info",
            ),
        val sessionCheckEnabled: Boolean = false,
    )

    data class RateLimit(
        val enabled: Boolean = true,
        val ipLimitPerMin: Int = 100,
        val userLimitPerMin: Int = 300,
        val window: Duration = Duration.ofMinutes(1),
    )
}

package com.gymplan.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Gateway 가 JWT 검증에 사용하는 키/Issuer 설정.
 *
 * application.yml:
 *
 *   gymplan:
 *     jwt:
 *       issuer: gymplan
 *       public-key: ${JWT_PUBLIC_KEY}   # PEM, Vault 주입
 *
 * Gateway 는 발급자가 아니므로 privateKey 는 절대 주입받지 않는다.
 * (privateKey 는 user-service 전용 — docs/context/security-guide.md)
 */
@ConfigurationProperties(prefix = "gymplan.jwt")
data class GatewayJwtProperties(
    val issuer: String = "gymplan",
    val publicKey: String? = null,
)

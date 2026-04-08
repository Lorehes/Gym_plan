package com.gymplan.common.security

import java.time.Duration

/**
 * JWT 설정 값. 외부 yml/Vault 에서 주입.
 *
 * application.yml 예시:
 *   gymplan:
 *     jwt:
 *       issuer: gymplan
 *       access-token-ttl: 30m
 *       refresh-token-ttl: 7d
 *       public-key: ${JWT_PUBLIC_KEY}    # PEM (Vault 주입)
 *       private-key: ${JWT_PRIVATE_KEY}  # PEM (user-service 만 주입)
 *
 * 보안 가이드 (docs/context/security-guide.md):
 * - 알고리즘 RS256 고정
 * - 키는 절대 코드/yml 하드코딩 금지, Vault 환경변수 주입만 허용
 * - publicKey 는 모든 서비스, privateKey 는 발급자(user-service)만 보유
 */
data class JwtProperties(
    val issuer: String = "gymplan",
    val accessTokenTtl: Duration = Duration.ofMinutes(30),
    val refreshTokenTtl: Duration = Duration.ofDays(7),
    /** PEM 포맷 RSA 공개키 (-----BEGIN PUBLIC KEY----- ...) */
    val publicKey: String,
    /** PEM 포맷 RSA 개인키 — user-service(발급자)만 주입, 다른 서비스는 null */
    val privateKey: String? = null,
)

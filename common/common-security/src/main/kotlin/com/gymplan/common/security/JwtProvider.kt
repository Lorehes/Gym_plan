package com.gymplan.common.security

import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.UnauthorizedException
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * RS256 JWT 발급/검증.
 *
 * - 발급(create*)은 user-service 만 호출 (privateKey 필요)
 * - 검증(parse)은 모든 서비스가 호출 (publicKey 만 있으면 됨)
 *
 * 클레임:
 *   sub:   userId (String)
 *   email: 사용자 이메일
 *   type:  "access" | "refresh"
 */
class JwtProvider(
    private val properties: JwtProperties,
) {
    private val publicKey: PublicKey = parsePublicKey(properties.publicKey)
    private val privateKey: PrivateKey? = properties.privateKey?.let(::parsePrivateKey)

    private val parser =
        Jwts.parser()
            .verifyWith(publicKey)
            .requireIssuer(properties.issuer)
            .build()

    // ───── 발급 ─────

    fun createAccessToken(
        userId: Long,
        email: String,
    ): String {
        val key = privateKey ?: error("privateKey 없이 토큰 발급 불가 — user-service 만 발급 가능")
        val now = Instant.now()
        return Jwts.builder()
            .issuer(properties.issuer)
            .subject(userId.toString())
            .claim("email", email)
            .claim("type", "access")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(properties.accessTokenTtl)))
            .signWith(key, Jwts.SIG.RS256)
            .compact()
    }

    fun createRefreshToken(userId: Long): String {
        val key = privateKey ?: error("privateKey 없이 토큰 발급 불가 — user-service 만 발급 가능")
        val now = Instant.now()
        // Refresh Token 은 초 단위 해상도의 iat 만으로는 충돌 가능성이 있어 (같은 초 안에 rotation 이
        // 일어나면 동일 토큰이 재발급되어 재사용 탐지 인덱스가 무너진다) 매 발급마다 고유 JTI 를 부여한다.
        return Jwts.builder()
            .issuer(properties.issuer)
            .subject(userId.toString())
            .id(UUID.randomUUID().toString())
            .claim("type", "refresh")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(properties.refreshTokenTtl)))
            .signWith(key, Jwts.SIG.RS256)
            .compact()
    }

    // ───── 검증 ─────

    /**
     * 토큰을 검증하고 클레임을 반환.
     *
     * @throws UnauthorizedException 토큰이 만료/위조/형식 오류인 경우
     */
    fun parse(token: String): JwtPayload {
        try {
            val claims = parser.parseSignedClaims(token).payload
            return JwtPayload(
                userId = claims.subject.toLong(),
                email = claims["email"] as? String,
                type = claims["type"] as? String ?: "access",
            )
        } catch (e: ExpiredJwtException) {
            throw UnauthorizedException(ErrorCode.AUTH_EXPIRED_TOKEN, cause = e)
        } catch (e: JwtException) {
            throw UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN, cause = e)
        } catch (e: IllegalArgumentException) {
            throw UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN, cause = e)
        }
    }

    // ───── PEM 파싱 ─────

    private fun parsePublicKey(pem: String): PublicKey {
        val der = pemToDer(pem, "PUBLIC KEY")
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    }

    private fun parsePrivateKey(pem: String): PrivateKey {
        val der = pemToDer(pem, "PRIVATE KEY")
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun pemToDer(
        pem: String,
        type: String,
    ): ByteArray {
        val cleaned =
            pem
                .replace("-----BEGIN $type-----", "")
                .replace("-----END $type-----", "")
                .replace("\\s".toRegex(), "")
        return Base64.getDecoder().decode(cleaned)
    }
}

/**
 * JWT 검증 결과로 노출되는 페이로드.
 */
data class JwtPayload(
    val userId: Long,
    val email: String?,
    val type: String,
) {
    fun isAccess(): Boolean = type == "access"

    fun isRefresh(): Boolean = type == "refresh"
}

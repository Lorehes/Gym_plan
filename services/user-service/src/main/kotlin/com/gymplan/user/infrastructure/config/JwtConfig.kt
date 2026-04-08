package com.gymplan.user.infrastructure.config

import com.gymplan.common.security.JwtProperties
import com.gymplan.common.security.JwtProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * user-service 전용 JWT 설정.
 *
 * 이 서비스는 토큰 **발급자**이므로 privateKey 가 반드시 주입되어야 한다.
 * 키는 Vault 경유 환경변수 주입만 허용 (docs/context/security-guide.md):
 *
 *   JWT_PUBLIC_KEY=...  (PEM)
 *   JWT_PRIVATE_KEY=... (PEM, user-service 전용)
 */
@Configuration
@EnableConfigurationProperties(UserServiceJwtProperties::class)
class JwtConfig {
    @Bean
    fun jwtProperties(props: UserServiceJwtProperties): JwtProperties =
        JwtProperties(
            issuer = props.issuer,
            accessTokenTtl = props.accessTokenTtl,
            refreshTokenTtl = props.refreshTokenTtl,
            publicKey = requireNotNull(props.publicKey) { "gymplan.jwt.public-key 설정이 필요합니다." },
            privateKey =
                requireNotNull(props.privateKey) {
                    "gymplan.jwt.private-key 설정이 필요합니다 (user-service 는 토큰 발급자)."
                },
        )

    @Bean
    fun jwtProvider(jwtProperties: JwtProperties): JwtProvider = JwtProvider(jwtProperties)
}

@ConfigurationProperties(prefix = "gymplan.jwt")
data class UserServiceJwtProperties(
    val issuer: String = "gymplan",
    val accessTokenTtl: Duration = Duration.ofMinutes(30),
    val refreshTokenTtl: Duration = Duration.ofDays(7),
    val publicKey: String? = null,
    val privateKey: String? = null,
)

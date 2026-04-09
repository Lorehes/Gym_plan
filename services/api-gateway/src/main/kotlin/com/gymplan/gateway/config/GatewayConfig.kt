package com.gymplan.gateway.config

import com.gymplan.common.security.JwtProperties
import com.gymplan.common.security.JwtProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Gateway 의 인프라 빈 등록.
 *
 *  - JwtProvider: common-security 의 RS256 검증기 (publicKey 만 사용)
 *  - ReactiveStringRedisTemplate: Rate Limit / 세션 조회용 (Reactive 환경)
 */
@Configuration
@EnableConfigurationProperties(
    GatewaySecurityProperties::class,
    GatewayJwtProperties::class,
)
class GatewayConfig {
    @Bean
    fun jwtProperties(props: GatewayJwtProperties): JwtProperties =
        JwtProperties(
            issuer = props.issuer,
            // Gateway 는 발급자가 아니므로 ttl 값은 사용하지 않지만 data class 기본값 그대로.
            publicKey =
                requireNotNull(props.publicKey) {
                    "gymplan.jwt.public-key 설정이 필요합니다. (Vault 환경변수 JWT_PUBLIC_KEY 주입)"
                },
            privateKey = null,
        )

    @Bean
    fun jwtProvider(jwtProperties: JwtProperties): JwtProvider = JwtProvider(jwtProperties)

    @Bean
    fun reactiveStringRedisTemplate(factory: ReactiveRedisConnectionFactory): ReactiveStringRedisTemplate =
        ReactiveStringRedisTemplate(factory)
}

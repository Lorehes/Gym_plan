package com.gymplan.user.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * user-service Spring Security 설정.
 *
 * 중요: 이 서비스는 Gateway 뒤에 위치하므로 JWT 를 직접 검증하지 않는다.
 *       Gateway 가 검증 후 X-User-Id 헤더를 주입해 전달하며, 본 서비스는 헤더만 신뢰한다.
 *       따라서 Spring Security 의 역할은:
 *         - 모든 요청 permitAll (인증 강제는 argument resolver 레벨에서 수행)
 *         - CSRF/세션 비활성화 (무상태 API)
 *         - CORS 화이트리스트 적용
 *
 * 보안 가이드: docs/context/security-guide.md
 */
@Configuration
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .anyRequest().permitAll()
            }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins =
                    listOf(
                        "http://localhost:3000",
                        "https://gymplan.io",
                        "capacitor://localhost",
                    )
                allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
                maxAge = MAX_AGE_SECONDS
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    companion object {
        private const val MAX_AGE_SECONDS = 3600L
    }
}

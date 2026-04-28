package com.gymplan.notification.infrastructure.config

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
 * notification-service 보안 설정.
 *
 * Gateway가 JWT 검증 후 X-User-Id 헤더를 주입.
 * 본 서비스는 X-User-Id 헤더를 신뢰 (JWT 직접 검증 금지).
 * 인증 강제는 CurrentUserIdArgumentResolver에서 수행.
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
                allowedHeaders = listOf("Authorization", "Content-Type", "Accept", "X-Requested-With")
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

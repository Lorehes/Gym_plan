package com.gymplan.plan.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * plan-service Spring Security 설정.
 *
 * Gateway 뒤에 위치하므로 JWT를 직접 검증하지 않는다.
 * Gateway가 JWT 검증 후 X-User-Id 헤더를 주입하며, 본 서비스는 헤더만 신뢰한다.
 * CORS 는 Gateway 단독 책임 — 하위 서비스에서 헤더를 중복 발급하지 않는다.
 *
 * 보안 가이드: docs/context/security-guide.md
 */
@Configuration
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.disable() }
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
}

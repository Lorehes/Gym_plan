package com.gymplan.user.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * 비밀번호 해싱 전략.
 *
 * BCrypt (cost=10).
 * 보안 가이드 (docs/context/security-guide.md) — 평문 저장/전송 금지, BCrypt 해시만 허용.
 */
@Configuration
class PasswordConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)

    companion object {
        private const val BCRYPT_STRENGTH = 10
    }
}

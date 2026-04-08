package com.gymplan.user.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * user-service Redis 설정.
 *
 * 저장하는 모든 값이 문자열(+숫자 직렬화) 이므로 StringRedisTemplate 한 개로 충분하다.
 * 커넥션 설정은 spring.data.redis.* (application.yml) 을 통해 주입.
 */
@Configuration
class RedisConfig {
    @Bean
    fun stringRedisTemplate(factory: RedisConnectionFactory): StringRedisTemplate = StringRedisTemplate(factory)
}

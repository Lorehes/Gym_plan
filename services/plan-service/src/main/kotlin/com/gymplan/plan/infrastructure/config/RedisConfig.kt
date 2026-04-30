package com.gymplan.plan.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * plan-service Redis 설정.
 *
 * PlanCacheManager가 JSON 문자열을 직접 다루므로 StringRedisTemplate 하나로 충분하다.
 * 커넥션 설정은 spring.data.redis.* (application.yml)을 통해 주입.
 */
@Configuration
class RedisConfig {
    @Bean
    fun stringRedisTemplate(factory: RedisConnectionFactory): StringRedisTemplate = StringRedisTemplate(factory)
}

package com.gymplan.notification.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.RedisMessageListenerContainer

@Configuration
class RedisConfig {
    @Bean
    fun stringRedisTemplate(factory: RedisConnectionFactory): StringRedisTemplate = StringRedisTemplate(factory)

    @Bean
    fun redisMessageListenerContainer(factory: RedisConnectionFactory): RedisMessageListenerContainer =
        RedisMessageListenerContainer().apply {
            setConnectionFactory(factory)
        }
}

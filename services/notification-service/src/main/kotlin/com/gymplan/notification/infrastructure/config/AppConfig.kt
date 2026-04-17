package com.gymplan.notification.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@Configuration
class AppConfig {
    @Bean
    fun scheduledExecutorService(): ScheduledExecutorService = Executors.newScheduledThreadPool(SCHEDULER_POOL_SIZE)

    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper().registerKotlinModule()

    companion object {
        private const val SCHEDULER_POOL_SIZE = 4
    }
}

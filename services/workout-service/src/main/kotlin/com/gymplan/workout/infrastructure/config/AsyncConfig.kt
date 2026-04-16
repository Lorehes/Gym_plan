package com.gymplan.workout.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

/** @Async 활성화 — WorkoutEventPublisher Kafka 비동기 발행에 사용. */
@Configuration
@EnableAsync
class AsyncConfig

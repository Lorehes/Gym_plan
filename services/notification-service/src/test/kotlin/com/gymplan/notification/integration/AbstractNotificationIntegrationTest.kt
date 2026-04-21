package com.gymplan.notification.integration

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Phase 2 E2E — notification-service 통합 테스트 기반 클래스.
 *
 * Redis 7 컨테이너를 Testcontainers로 기동하고
 * DynamicPropertySource로 spring.data.redis.* 를 주입합니다.
 *
 * Kafka는 @EmbeddedKafka로 각 테스트 클래스에서 설정합니다.
 */
abstract class AbstractNotificationIntegrationTest {
    companion object {
        @JvmField
        val redis: GenericContainer<*> =
            GenericContainer<Nothing>(DockerImageName.parse("redis:7.0"))
                .withExposedPorts(6379)

        init {
            redis.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }
}

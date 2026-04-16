package com.gymplan.plan.integration

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 통합 테스트 공통 컨테이너 + 프로퍼티 주입.
 *
 * - MySQL 8 + Redis 7 을 Testcontainers 로 기동
 * - spring.jpa.hibernate.ddl-auto=create-drop 오버라이드 (application.yml 은 validate)
 *
 * 명세: docs/specs/plan-service.md §8 — "Testcontainers로 실제 MySQL/Redis 사용"
 */
abstract class AbstractIntegrationTest {
    companion object {
        @JvmStatic
        val mysql: MySQLContainer<*> =
            MySQLContainer<Nothing>(DockerImageName.parse("mysql:8.0"))
                .apply {
                    withDatabaseName("gymplan_plan_test")
                    withUsername("test")
                    withPassword("test")
                    start()
                }

        @JvmStatic
        val redis: GenericContainer<*> =
            GenericContainer<Nothing>(DockerImageName.parse("redis:7-alpine"))
                .apply {
                    withExposedPorts(REDIS_PORT)
                    start()
                }

        private const val REDIS_PORT = 6379

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                val base = mysql.jdbcUrl
                val sep = if ('?' in base) "&" else "?"
                "${base}${sep}serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
            }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }

            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(REDIS_PORT) }
            registry.add("spring.data.redis.password") { "" }
        }
    }
}

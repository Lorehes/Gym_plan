package com.gymplan.user.integration

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 통합 테스트 공통 컨테이너 + 프로퍼티 주입.
 *
 * - MySQL 8 + Redis 7 을 Testcontainers 로 기동
 * - RSA 키는 런타임 생성 → `gymplan.jwt.*` 로 주입
 * - `spring.jpa.hibernate.ddl-auto=create-drop` 오버라이드 (application.yml 은 validate)
 *
 * 모든 통합 테스트는 이 클래스를 상속해 동일한 Spring 컨텍스트(+ 동일한 컨테이너 인스턴스) 를 재사용한다.
 *
 * 명세: docs/specs/user-service.md §4.4 ─ "Testcontainers로 실제 MySQL/Redis 사용 (모킹 금지)"
 */
abstract class AbstractIntegrationTest {
    companion object {
        // init block 에서 start() 를 호출하므로 클래스가 로드되는 시점에 컨테이너가 기동된다.
        // JVM 종료 시 Ryuk 가 정리하므로 명시적 stop() 이 필요 없다.
        @JvmStatic
        val mysql: MySQLContainer<*> =
            MySQLContainer<Nothing>(DockerImageName.parse("mysql:8.0"))
                .apply {
                    withDatabaseName("gymplan_user_test")
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

        private val testKeys: TestRsaKeys.Keys = TestRsaKeys.generate()

        private const val REDIS_PORT = 6379

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            // 테스트는 Hibernate 가 스키마를 생성하도록 덮어씀.
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }

            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(REDIS_PORT) }

            registry.add("gymplan.jwt.issuer") { "gymplan-test" }
            registry.add("gymplan.jwt.access-token-ttl") { "30m" }
            registry.add("gymplan.jwt.refresh-token-ttl") { "7d" }
            registry.add("gymplan.jwt.public-key") { testKeys.publicKeyPem }
            registry.add("gymplan.jwt.private-key") { testKeys.privateKeyPem }
        }
    }
}

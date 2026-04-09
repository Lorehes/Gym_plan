package com.gymplan.gateway

import com.gymplan.common.security.JwtProperties
import com.gymplan.common.security.JwtProvider
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Gateway 통합 테스트 공통 컨테이너 + 프로퍼티 주입.
 *
 * - Redis 7 Testcontainer (Rate Limit)
 * - RSA 키 런타임 생성 → gymplan.jwt.* 주입
 * - mock 백엔드 없이 필터 동작만 검증 (401/429 확인)
 *   테스트용 /api/v1/echo 라우트는 TestRouteConfig 에서 등록.
 */
abstract class AbstractGatewayTest {
    companion object {
        @JvmStatic
        val redis: GenericContainer<*> =
            GenericContainer<Nothing>(DockerImageName.parse("redis:7-alpine"))
                .apply {
                    withExposedPorts(REDIS_PORT)
                    start()
                }

        val testKeys: TestRsaKeys.Keys = TestRsaKeys.generate()

        val testJwtProvider: JwtProvider =
            JwtProvider(
                JwtProperties(
                    issuer = "gymplan-test",
                    publicKey = testKeys.publicKeyPem,
                    privateKey = testKeys.privateKeyPem,
                ),
            )

        private const val REDIS_PORT = 6379

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(REDIS_PORT) }
            registry.add("spring.data.redis.password") { "" }

            registry.add("gymplan.jwt.issuer") { "gymplan-test" }
            registry.add("gymplan.jwt.public-key") { testKeys.publicKeyPem }

            // 테스트에서 세션 체크 비활성화 (user-service 가 없으므로)
            registry.add("gymplan.gateway.security.session-check-enabled") { false }
            // Rate limit 설정 (테스트에서 빠르게 도달하도록 낮은 값 사용)
            registry.add("gymplan.gateway.rate-limit.ip-limit-per-min") { 5 }
            registry.add("gymplan.gateway.rate-limit.user-limit-per-min") { 10 }
        }
    }
}

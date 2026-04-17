package com.gymplan.workout.integration

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName

/**
 * 통합 테스트 공통 컨테이너 + 프로퍼티 주입.
 *
 * - MongoDB 7 + Kafka 7.4 를 Testcontainers 로 기동
 * - 명세: docs/specs/workout-service.md §비기능 요구사항 — "Testcontainers로 실제 MongoDB/Kafka 사용"
 */
abstract class AbstractIntegrationTest {
    companion object {
        @JvmStatic
        val mongo: MongoDBContainer =
            MongoDBContainer(DockerImageName.parse("mongo:7.0"))
                .apply { start() }

        @JvmStatic
        val kafka: ConfluentKafkaContainer =
            ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
                .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri") { mongo.replicaSetUrl }
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
        }
    }
}

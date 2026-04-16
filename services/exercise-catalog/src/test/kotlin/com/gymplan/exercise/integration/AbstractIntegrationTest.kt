package com.gymplan.exercise.integration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName

/**
 * 통합 테스트 공통 컨테이너 + 프로퍼티 주입.
 *
 * - MySQL 8 : exercise CRUD (Source of Truth)
 * - Elasticsearch 8 : 검색 인덱스 (보조 저장소)
 *
 * nori 플러그인은 표준 ES Docker 이미지에 미포함.
 * src/test/resources/elasticsearch/settings.json 에서 nori_analyzer → standard 로 덮어쓴다.
 *
 * @Async ExerciseIndexer: SyncTaskExecutorConfig 로 동기 실행 강제
 *   → 테스트에서 ES 색인 완료 후 바로 검색 검증 가능
 */
abstract class AbstractIntegrationTest {
    companion object {
        @JvmStatic
        val mysql: MySQLContainer<*> =
            MySQLContainer<Nothing>(DockerImageName.parse("mysql:8.0"))
                .apply {
                    withDatabaseName("gymplan_exercise_test")
                    withUsername("test")
                    withPassword("test")
                    start()
                }

        @JvmStatic
        val elasticsearch: ElasticsearchContainer =
            ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.11.1"),
            ).apply {
                withEnv("xpack.security.enabled", "false")
                withEnv("discovery.type", "single-node")
                withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                start()
            }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                // mysql.jdbcUrl 이 '?'를 포함하지 않을 수 있으므로 구분자를 동적으로 결정한다.
                // 포함할 경우 '&', 없으면 '?'로 연결 (잘못 연결하면 DB명에 파라미터가 포함돼 접속 거부됨).
                val base = mysql.jdbcUrl
                val sep = if ('?' in base) "&" else "?"
                "${base}${sep}serverTimezone=UTC&characterEncoding=UTF-8"
            }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }

            registry.add("spring.elasticsearch.uris") {
                "http://${elasticsearch.host}:${elasticsearch.getMappedPort(9200)}"
            }
        }
    }

    /**
     * ExerciseIndexer 의 @Async 를 테스트에서 동기 실행으로 전환.
     *
     * @TransactionalEventListener(AFTER_COMMIT) 는 트랜잭션 커밋 후 이벤트를 발행한다.
     * SyncTaskExecutor 를 사용하면 이벤트 리스너가 같은 스레드에서 즉시 실행되어
     * 테스트 스레드가 ES 색인 완료를 기다리지 않아도 된다.
     */
    @Configuration
    class SyncTaskExecutorConfig {
        @Bean
        fun taskExecutor(): SyncTaskExecutor = SyncTaskExecutor()
    }
}

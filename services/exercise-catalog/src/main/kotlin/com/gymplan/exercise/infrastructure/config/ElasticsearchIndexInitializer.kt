package com.gymplan.exercise.infrastructure.config

import com.gymplan.exercise.infrastructure.search.ExerciseDocument
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.IndexOperations
import org.springframework.stereotype.Component

/**
 * 애플리케이션 시작 시 Elasticsearch 'exercises' 인덱스를 생성한다.
 *
 * Spring Data ES는 @Document(createIndex=true)로 마킹된 엔티티의 인덱스를
 * ES Repository가 있을 때만 자동 생성한다.
 * ExerciseSearchRepository는 ElasticsearchOperations를 직접 사용하므로
 * 인덱스를 수동으로 생성해야 한다.
 *
 * @Order(1) — ExerciseDataSeeder(ApplicationRunner) 보다 먼저 실행되어
 *             색인 준비가 완료된 상태에서 시드 데이터가 색인되도록 한다.
 */
@Component
@Order(1)
class ElasticsearchIndexInitializer(
    private val elasticsearchOperations: ElasticsearchOperations,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val indexOps: IndexOperations = elasticsearchOperations.indexOps(ExerciseDocument::class.java)
        if (!indexOps.exists()) {
            indexOps.createWithMapping()
            log.info("Elasticsearch 'exercises' 인덱스 생성 완료")
        } else {
            log.info("Elasticsearch 'exercises' 인덱스 이미 존재함")
        }
    }
}

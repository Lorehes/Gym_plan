package com.gymplan.exercise.infrastructure.search

import com.gymplan.exercise.domain.repository.ExerciseRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Component

/**
 * MySQL → Elasticsearch 기동 시 동기화.
 *
 * ES 문서 수가 MySQL 행 수보다 적으면 전체를 재색인한다.
 * - ExerciseDataSeeder(@Order 2)가 DB 시드 후 실행되므로 @Order(3)으로 설정
 * - docker compose down -v 후 재기동 시 ES 인덱스가 비어있어도 자동 복구
 * - 이미 동기화된 상태라면 아무 작업 없이 종료
 */
@Component
@Order(3)
class ElasticsearchSyncRunner(
    private val exerciseRepository: ExerciseRepository,
    private val elasticsearchOperations: ElasticsearchOperations,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val mysqlCount = exerciseRepository.count()
        val esCount = elasticsearchOperations.count(
            NativeQuery.builder()
                .withQuery { q -> q.matchAll { it } }
                .build(),
            ExerciseDocument::class.java,
        )

        if (esCount >= mysqlCount) {
            log.info("Elasticsearch 동기화 불필요: ES={}건, MySQL={}건", esCount, mysqlCount)
            return
        }

        log.info("Elasticsearch 동기화 시작: ES={}건, MySQL={}건", esCount, mysqlCount)
        val documents = exerciseRepository.findAll().map { ExerciseDocument.from(it) }
        elasticsearchOperations.save(documents)
        log.info("Elasticsearch 동기화 완료: {}건 색인", documents.size)
    }
}

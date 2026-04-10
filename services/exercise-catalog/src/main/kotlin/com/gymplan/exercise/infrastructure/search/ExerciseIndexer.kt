package com.gymplan.exercise.infrastructure.search

import com.gymplan.exercise.domain.entity.Exercise
import org.slf4j.LoggerFactory
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * MySQL → Elasticsearch 비동기 색인.
 *
 * MySQL 이 Source of Truth, Elasticsearch 는 검색 전용 보조 저장소.
 * 색인 실패 시 로그만 남기고 MySQL 데이터는 유지한다.
 */
@Component
class ExerciseIndexer(
    private val elasticsearchOperations: ElasticsearchOperations,
) {
    private val log = LoggerFactory.getLogger(ExerciseIndexer::class.java)

    @Async
    fun index(exercise: Exercise) {
        try {
            val document = ExerciseDocument.from(exercise)
            elasticsearchOperations.save(document)
            log.info("ES 색인 완료: exerciseId={}", exercise.id)
        } catch (ex: Exception) {
            log.error("ES 색인 실패: exerciseId={}", exercise.id, ex)
        }
    }
}

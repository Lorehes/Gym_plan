package com.gymplan.exercise.infrastructure.search

import com.gymplan.exercise.domain.event.ExerciseCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * MySQL → Elasticsearch 비동기 색인.
 *
 * MySQL 이 Source of Truth, Elasticsearch 는 검색 전용 보조 저장소.
 * 트랜잭션 커밋 후에만 색인을 수행하여 고아 문서를 방지한다.
 * 색인 실패 시 로그만 남기고 MySQL 데이터는 유지한다.
 */
@Component
class ExerciseIndexer(
    private val elasticsearchOperations: ElasticsearchOperations,
) {
    private val log = LoggerFactory.getLogger(ExerciseIndexer::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onExerciseCreated(event: ExerciseCreatedEvent) {
        try {
            val document =
                ExerciseDocument(
                    exerciseId = event.exerciseId,
                    name = event.name,
                    nameEn = event.nameEn,
                    muscleGroup = event.muscleGroup,
                    equipment = event.equipment,
                    difficulty = event.difficulty,
                )
            elasticsearchOperations.save(document)
            log.info("ES 색인 완료: exerciseId={}", event.exerciseId)
        } catch (ex: Exception) {
            log.error("ES 색인 실패: exerciseId={}", event.exerciseId, ex)
        }
    }
}

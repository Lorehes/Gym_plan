package com.gymplan.exercise.domain.event

/**
 * 운동 종목 생성 완료 이벤트.
 *
 * 트랜잭션 커밋 후 Elasticsearch 비동기 색인을 트리거한다.
 */
data class ExerciseCreatedEvent(
    val exerciseId: Long,
    val name: String,
    val nameEn: String?,
    val muscleGroup: String,
    val equipment: String,
    val difficulty: String,
)

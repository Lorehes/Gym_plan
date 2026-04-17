package com.gymplan.workout.domain.repository

import com.gymplan.workout.domain.entity.WorkoutSession
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository

/**
 * 운동 세션 리포지토리.
 *
 * 복잡한 배열 조작($push / $pull / $set)은 WorkoutSessionRepositoryCustom 으로 위임.
 */
interface WorkoutSessionRepository :
    MongoRepository<WorkoutSession, String>,
    WorkoutSessionRepositoryCustom {
    /** 진행 중 세션 조회 (completedAt = null → IN_PROGRESS). */
    fun findByUserIdAndCompletedAtIsNull(userId: String): WorkoutSession?

    /** 세션 단건 조회 (소유자 검증 포함). */
    fun findByIdAndUserId(
        id: String,
        userId: String,
    ): WorkoutSession?

    /** 운동 히스토리 — 페이징. */
    fun findByUserId(
        userId: String,
        pageable: Pageable,
    ): Page<WorkoutSession>
}

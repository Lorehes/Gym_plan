package com.gymplan.workout.domain.repository

import com.gymplan.workout.domain.entity.SetRecord
import java.time.Instant

/**
 * MongoDB $push / $pull / $set 연산이 필요한 커스텀 리포지토리 인터페이스.
 *
 * 핵심 원칙: 전체 문서 교체(save) 대신 $push로 배열에 append.
 *
 * 구현체: WorkoutSessionRepositoryImpl
 */
interface WorkoutSessionRepositoryCustom {
    /**
     * 세트 기록 추가 ($push).
     * - exerciseId가 이미 존재하면 해당 exercise의 sets 배열에 push
     * - 존재하지 않으면 새 SessionExercise를 exercises 배열에 push
     *
     * @return 수정된 문서 수 (0이면 세션 없음 또는 완료됨)
     */
    fun pushSet(
        sessionId: String,
        userId: String,
        exerciseId: String,
        exerciseName: String,
        muscleGroup: String,
        set: SetRecord,
    ): Long

    /**
     * 세트 기록 수정.
     *
     * @return 수정된 문서 수
     */
    fun updateSet(
        sessionId: String,
        userId: String,
        exerciseId: String,
        setNo: Int,
        newSet: SetRecord,
    ): Long

    /**
     * 세트 기록 삭제 ($pull).
     *
     * @return 수정된 문서 수
     */
    fun pullSet(
        sessionId: String,
        userId: String,
        exerciseId: String,
        setNo: Int,
    ): Long

    /**
     * 운동 완료 처리 — 원자적 업데이트.
     * completedAt = null 조건을 filter에 포함해 중복 완료를 DB 레벨에서 방지한다.
     *
     * @return 수정된 문서 수 (0이면 이미 완료된 세션)
     */
    fun completeSession(
        sessionId: String,
        userId: String,
        completedAt: Instant,
        durationSec: Long,
        totalVolume: Double,
        totalSets: Int,
        notes: String?,
    ): Long
}

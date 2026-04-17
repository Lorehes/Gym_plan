package com.gymplan.workout.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * 운동 세션 MongoDB 단일 문서.
 *
 * 설계 원칙 (docs/database/mongodb-schema.md):
 *  - 세션 전체(exercises + sets)를 단일 문서로 관리 → JOIN 없이 저지연 읽기/쓰기
 *  - completedAt = null → IN_PROGRESS, not null → COMPLETED
 *  - planName, exerciseName 비정규화 스냅샷 — 원본 삭제 후에도 기록 보존
 *
 * 인덱스:
 *  - (userId, startedAt DESC) → 히스토리 페이징
 *  - (userId, completedAt)    → 진행 중 세션 단건 조회
 */
@Document(collection = "workout_sessions")
@CompoundIndexes(
    CompoundIndex(name = "idx_userId_startedAt", def = "{'userId': 1, 'startedAt': -1}"),
    CompoundIndex(name = "idx_userId_completedAt", def = "{'userId': 1, 'completedAt': 1}"),
)
data class WorkoutSession(
    @Id val id: String? = null,
    val userId: String,
    val planId: String? = null,
    val planName: String? = null,
    val startedAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val totalVolume: Double = 0.0,
    val totalSets: Int = 0,
    val durationSec: Long = 0,
    val notes: String? = null,
    val exercises: List<SessionExercise> = emptyList(),
)

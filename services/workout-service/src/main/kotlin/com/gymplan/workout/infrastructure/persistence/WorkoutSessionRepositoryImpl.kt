package com.gymplan.workout.infrastructure.persistence

import com.gymplan.workout.domain.entity.SessionExercise
import com.gymplan.workout.domain.entity.SetRecord
import com.gymplan.workout.domain.repository.WorkoutSessionRepositoryCustom
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.Instant

/**
 * MongoDB $push / $pull 연산 구현체.
 *
 * 핵심 원칙: 전체 문서 교체(save) 대신 $push로 배열에 append.
 *
 * pushSet 두 단계:
 *   1. exercises.$.sets 에 $push (exerciseId 존재 시)
 *   2. modifiedCount == 0이면 exercises 배열에 새 SessionExercise $push
 */
class WorkoutSessionRepositoryImpl(
    private val mongoTemplate: MongoTemplate,
) : WorkoutSessionRepositoryCustom {
    override fun pushSet(
        sessionId: String,
        userId: String,
        exerciseId: String,
        exerciseName: String,
        muscleGroup: String,
        set: SetRecord,
    ): Long {
        val setDoc = set.toDocument()

        // 시도 1: 이미 존재하는 exerciseId의 sets 배열에 $push
        val existingExerciseQuery =
            Query(
                Criteria.where("_id").`is`(ObjectId(sessionId))
                    .and("userId").`is`(userId)
                    .and("completedAt").isNull
                    .and("exercises.exerciseId").`is`(exerciseId),
            )
        val pushToExistingUpdate = Update().push("exercises.\$.sets", setDoc)
        val result1 = mongoTemplate.updateFirst(existingExerciseQuery, pushToExistingUpdate, COLLECTION)

        if (result1.modifiedCount > 0) return result1.modifiedCount

        // 시도 2: exerciseId가 없으므로 새 SessionExercise를 exercises 배열에 $push
        val newExerciseQuery =
            Query(
                Criteria.where("_id").`is`(ObjectId(sessionId))
                    .and("userId").`is`(userId)
                    .and("completedAt").isNull,
            )
        val newExercise =
            SessionExercise(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                muscleGroup = muscleGroup,
                sets = listOf(set),
            ).toDocument()
        val pushNewExerciseUpdate = Update().push("exercises", newExercise)
        val result2 = mongoTemplate.updateFirst(newExerciseQuery, pushNewExerciseUpdate, COLLECTION)

        return result2.modifiedCount
    }

    override fun updateSet(
        sessionId: String,
        userId: String,
        exerciseId: String,
        setNo: Int,
        newSet: SetRecord,
    ): Long {
        // arrayFilters로 중첩 배열 내 특정 세트를 타겟팅
        val query =
            Query(
                Criteria.where("_id").`is`(ObjectId(sessionId))
                    .and("userId").`is`(userId)
                    .and("completedAt").isNull,
            )
        val update =
            Update()
                .set("exercises.\$[ex].sets.\$[s].reps", newSet.reps)
                .set("exercises.\$[ex].sets.\$[s].weightKg", newSet.weightKg)
                .set("exercises.\$[ex].sets.\$[s].isSuccess", newSet.isSuccess)
                .filterArray(Criteria.where("ex.exerciseId").`is`(exerciseId))
                .filterArray(Criteria.where("s.setNo").`is`(setNo))

        val result = mongoTemplate.updateFirst(query, update, COLLECTION)
        return result.modifiedCount
    }

    override fun pullSet(
        sessionId: String,
        userId: String,
        exerciseId: String,
        setNo: Int,
    ): Long {
        // exercises.$ 의 sets 배열에서 setNo가 일치하는 원소를 $pull
        val query =
            Query(
                Criteria.where("_id").`is`(ObjectId(sessionId))
                    .and("userId").`is`(userId)
                    .and("completedAt").isNull
                    .and("exercises.exerciseId").`is`(exerciseId),
            )
        val update = Update().pull("exercises.\$.sets", Document("setNo", setNo))
        val result = mongoTemplate.updateFirst(query, update, COLLECTION)
        return result.modifiedCount
    }

    override fun completeSession(
        sessionId: String,
        userId: String,
        completedAt: Instant,
        durationSec: Long,
        totalVolume: Double,
        totalSets: Int,
        notes: String?,
    ): Long {
        // completedAt = null 조건 포함 → 원자적 중복 완료 방지
        val query =
            Query(
                Criteria.where("_id").`is`(ObjectId(sessionId))
                    .and("userId").`is`(userId)
                    .and("completedAt").isNull,
            )
        val update =
            Update()
                .set("completedAt", completedAt)
                .set("durationSec", durationSec)
                .set("totalVolume", totalVolume)
                .set("totalSets", totalSets)

        if (notes != null) update.set("notes", notes)

        val result = mongoTemplate.updateFirst(query, update, COLLECTION)
        return result.modifiedCount
    }

    // ─────────────────── 내부 헬퍼 ───────────────────

    private fun SetRecord.toDocument() =
        Document().apply {
            put("setNo", setNo)
            put("reps", reps)
            put("weightKg", weightKg)
            put("isSuccess", isSuccess)
            put("completedAt", completedAt)
        }

    private fun SessionExercise.toDocument() =
        Document().apply {
            put("exerciseId", exerciseId)
            put("exerciseName", exerciseName)
            put("muscleGroup", muscleGroup)
            put("sets", sets.map { it.toDocument() })
        }

    companion object {
        private const val COLLECTION = "workout_sessions"
    }
}

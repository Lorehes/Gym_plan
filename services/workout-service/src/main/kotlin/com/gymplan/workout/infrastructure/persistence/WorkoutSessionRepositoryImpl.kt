package com.gymplan.workout.infrastructure.persistence

import com.gymplan.workout.domain.entity.SessionExercise
import com.gymplan.workout.domain.entity.SessionStatus
import com.gymplan.workout.domain.entity.SetRecord
import com.gymplan.workout.domain.repository.WorkoutSessionRepositoryCustom
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * MongoDB $push / $pull 연산 구현체.
 *
 * 핵심 원칙: 전체 문서 교체(save) 대신 $push로 배열에 append.
 *
 * pushSet — 단일 원자적 파이프라인 업데이트 (MongoDB 4.2+):
 *   $cond로 exerciseId 존재 여부를 판단, 두 경로(기존 exercise / 신규 exercise)를
 *   한 번의 왕복으로 처리하여 Race Condition(중복 exercise 삽입) 원천 차단.
 */
@Component
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
        val newExerciseDoc =
            SessionExercise(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                muscleGroup = muscleGroup,
                sets = listOf(set),
            ).toDocument()

        // 단일 원자적 aggregation pipeline update
        // - exerciseId 존재 시: $map으로 해당 exercise의 sets에 새 세트 $concatArrays
        // - exerciseId 없을 시: exercises 배열에 새 exercise $concatArrays
        val pipeline =
            listOf(
                Document(
                    "\$set",
                    Document(
                        "exercises",
                        Document(
                            "\$cond",
                            Document()
                                .append("if", Document("\$in", listOf(exerciseId, "\$exercises.exerciseId")))
                                .append(
                                    "then",
                                    Document(
                                        "\$map",
                                        Document()
                                            .append("input", "\$exercises")
                                            .append("as", "ex")
                                            .append(
                                                "in",
                                                Document(
                                                    "\$cond",
                                                    Document()
                                                        .append("if", Document("\$eq", listOf("\$\$ex.exerciseId", exerciseId)))
                                                        .append(
                                                            "then",
                                                            Document(
                                                                "\$mergeObjects",
                                                                listOf(
                                                                    "\$\$ex",
                                                                    Document(
                                                                        "sets",
                                                                        Document(
                                                                            "\$concatArrays",
                                                                            listOf("\$\$ex.sets", listOf(setDoc)),
                                                                        ),
                                                                    ),
                                                                ),
                                                            ),
                                                        )
                                                        .append("else", "\$\$ex"),
                                                ),
                                            ),
                                    ),
                                )
                                .append(
                                    "else",
                                    Document(
                                        "\$concatArrays",
                                        listOf("\$exercises", listOf(newExerciseDoc)),
                                    ),
                                ),
                        ),
                    ),
                ),
            )

        val filter =
            Document("_id", ObjectId(sessionId))
                .append("userId", userId)
                .append("completedAt", null)

        val result = mongoTemplate.getCollection(COLLECTION).updateOne(filter, pipeline)
        return result.modifiedCount
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
        // status = IN_PROGRESS 조건 포함 → 원자적 중복 종료 방지
        val query =
            Query(
                Criteria.where("_id").`is`(ObjectId(sessionId))
                    .and("userId").`is`(userId)
                    .and("status").`is`(SessionStatus.IN_PROGRESS),
            )
        val update =
            Update()
                .set("status", SessionStatus.COMPLETED)
                .set("completedAt", completedAt)
                .set("durationSec", durationSec)
                .set("totalVolume", totalVolume)
                .set("totalSets", totalSets)

        if (notes != null) update.set("notes", notes)

        val result = mongoTemplate.updateFirst(query, update, COLLECTION)
        return result.modifiedCount
    }

    override fun cancelSession(
        sessionId: String,
        userId: String,
        cancelledAt: Instant,
    ): Long {
        // status = IN_PROGRESS 조건 포함 → 이미 종료된 세션의 재취소를 원자적으로 차단
        val query =
            Query(
                Criteria.where("_id").`is`(ObjectId(sessionId))
                    .and("userId").`is`(userId)
                    .and("status").`is`(SessionStatus.IN_PROGRESS),
            )
        val update =
            Update()
                .set("status", SessionStatus.CANCELLED)
                .set("completedAt", cancelledAt)

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

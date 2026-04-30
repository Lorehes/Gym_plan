package com.gymplan.plan.integration

import com.gymplan.plan.domain.entity.PlanExercise
import com.gymplan.plan.domain.entity.WorkoutPlan
import com.gymplan.plan.domain.repository.PlanExerciseRepository
import com.gymplan.plan.domain.repository.WorkoutPlanRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.math.BigDecimal

/**
 * 루틴 CRUD 통합 테스트.
 *
 * 명세: docs/specs/plan-service.md §8
 *
 * 검증 항목:
 *   - TC-05: 타인 루틴 접근 차단 (403)
 *   - TC-06: 운동 추가 (exercise-catalog 호출 없음)
 *   - TC-07: 운동 순서 변경 — orderedIds 불일치 (400)
 *   - TC-08: 루틴 삭제 (soft delete)
 *   - TC-09: exerciseName 누락 (400)
 *   - TC-10: muscleGroup 허용값 외 (400)
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlanControllerIntegrationTest : AbstractIntegrationTest() {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var workoutPlanRepository: WorkoutPlanRepository

    @Autowired lateinit var planExerciseRepository: PlanExerciseRepository

    @Autowired lateinit var redis: StringRedisTemplate

    private val userId = 1L
    private val otherUserId = 2L

    @BeforeEach
    fun setUp() {
        planExerciseRepository.deleteAll()
        workoutPlanRepository.deleteAll()
        redis.delete(redis.keys("plan:*"))
    }

    // ─────────────────── 루틴 CRUD ───────────────────

    @Test
    fun `루틴 생성 - 201 Created`() {
        mockMvc.post("/api/v1/plans") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "가슴/삼두 루틴", "dayOfWeek": 0}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.name") { value("가슴/삼두 루틴") }
            jsonPath("$.data.dayOfWeek") { value(0) }
        }
    }

    @Test
    fun `루틴 생성 - name 누락 시 400`() {
        mockMvc.post("/api/v1/plans") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"dayOfWeek": 0}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `루틴 목록 조회 - 내 루틴만 반환`() {
        workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "내 루틴", dayOfWeek = 0))
        workoutPlanRepository.save(WorkoutPlan(userId = otherUserId, name = "다른 사람 루틴", dayOfWeek = 1))

        mockMvc.get("/api/v1/plans") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].name") { value("내 루틴") }
        }
    }

    // ─────────────────── TC-05: 타인 루틴 접근 차단 ───────────────────

    @Test
    fun `TC-05 타인 루틴 접근 시 403 Forbidden`() {
        val otherPlan =
            workoutPlanRepository.save(
                WorkoutPlan(userId = otherUserId, name = "타인 루틴", dayOfWeek = 1),
            )

        mockMvc.get("/api/v1/plans/${otherPlan.id}") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("PLAN_ACCESS_DENIED") }
        }
    }

    // ─────────────────── TC-06: 운동 추가 ───────────────────

    @Test
    fun `TC-06 운동 추가 - exercise-catalog 호출 없이 exerciseName muscleGroup 저장`() {
        val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "내 루틴", dayOfWeek = 0))

        mockMvc.post("/api/v1/plans/${plan.id}/exercises") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "exerciseId": 20,
                  "exerciseName": "랫풀다운",
                  "muscleGroup": "BACK",
                  "targetSets": 4,
                  "targetReps": 12,
                  "restSeconds": 60
                }
                """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.exerciseName") { value("랫풀다운") }
            jsonPath("$.data.muscleGroup") { value("BACK") }
            jsonPath("$.data.targetSets") { value(4) }
        }

        // DB 저장 확인
        val exercises = planExerciseRepository.findByPlanIdOrderByOrderIndexAsc(plan.id!!)
        assertThat(exercises).hasSize(1)
        assertThat(exercises[0].exerciseName).isEqualTo("랫풀다운")
        assertThat(exercises[0].muscleGroup).isEqualTo("BACK")
        assertThat(exercises[0].exerciseId).isEqualTo(20L)

        // 캐시 무효화 확인
        assertThat(redis.hasKey("plan:cache:$userId:${plan.id}")).isFalse()
    }

    // ─────────────────── TC-07: orderedIds 불일치 ───────────────────

    @Test
    fun `TC-07 운동 순서 변경 - orderedIds 불일치 시 400`() {
        val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴", dayOfWeek = 0))
        repeat(4) { i ->
            planExerciseRepository.save(
                PlanExercise(
                    plan = plan,
                    exerciseId = (i + 1).toLong(),
                    exerciseName = "운동$i",
                    muscleGroup = "CHEST",
                    orderIndex = i,
                ),
            )
        }

        mockMvc.put("/api/v1/plans/${plan.id}/exercises/reorder") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderedIds": [1, 2, 3]}""" // 4개여야 하는데 3개
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `운동 순서 변경 - 정상`() {
        val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴", dayOfWeek = 0))
        val exercises =
            (0 until 3).map { i ->
                planExerciseRepository.save(
                    PlanExercise(
                        plan = plan,
                        exerciseId = (i + 1).toLong(),
                        exerciseName = "운동$i",
                        muscleGroup = "CHEST",
                        orderIndex = i,
                    ),
                )
            }

        val reversed = exercises.reversed().map { it.id!! }
        mockMvc.put("/api/v1/plans/${plan.id}/exercises/reorder") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderedIds": $reversed}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.reordered") { value(true) }
        }

        val reordered = planExerciseRepository.findByPlanIdOrderByOrderIndexAsc(plan.id!!)
        assertThat(reordered.map { it.id }).isEqualTo(reversed)
    }

    // ─────────────────── TC-08: 루틴 삭제 ───────────────────

    @Test
    fun `TC-08 루틴 삭제 - soft delete, 이후 조회 시 404`() {
        val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "삭제할 루틴", dayOfWeek = 0))

        mockMvc.delete("/api/v1/plans/${plan.id}") {
            header("X-User-Id", userId)
        }.andExpect { status { isNoContent() } }

        // DB row는 남아있고 is_active=false
        val deleted = workoutPlanRepository.findById(plan.id!!).orElseThrow()
        assertThat(deleted.isActive).isFalse()

        // 이후 조회 시 404
        mockMvc.get("/api/v1/plans/${plan.id}") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("PLAN_NOT_FOUND") }
        }
    }

    // ─────────────────── TC-09: exerciseName 누락 ───────────────────

    @Test
    fun `TC-09 운동 추가 - exerciseName 누락 시 400`() {
        val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴", dayOfWeek = 0))

        mockMvc.post("/api/v1/plans/${plan.id}/exercises") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"exerciseId": 20, "muscleGroup": "BACK"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_FAILED") }
            jsonPath("$.error.details.exerciseName") { exists() }
        }
    }

    // ─────────────────── TC-10: muscleGroup 허용값 외 ───────────────────

    @Test
    fun `TC-10 운동 추가 - muscleGroup 허용값 외 시 400`() {
        val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴", dayOfWeek = 0))

        mockMvc.post("/api/v1/plans/${plan.id}/exercises") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "exerciseId": 20,
                  "exerciseName": "랫풀다운",
                  "muscleGroup": "INVALID_GROUP"
                }
                """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_FAILED") }
            jsonPath("$.error.details.muscleGroup") { exists() }
        }
    }

    // ─────────────────── X-User-Id 없음 ───────────────────

    @Test
    fun `X-User-Id 헤더 없으면 401`() {
        mockMvc.get("/api/v1/plans/today")
            .andExpect { status { isUnauthorized() } }
    }

    // ─────────────────── TC-12: 운동 수정 (PUT /exercises/{exerciseItemId}) ───────────────────

    @Nested
    @DisplayName("TC-12: PUT /api/v1/plans/{planId}/exercises/{exerciseItemId}")
    inner class UpdateExercise {
        @Test
        @DisplayName("TC-12-1: 정상 수정 — targetSets·targetWeightKg 변경 + 캐시 무효화")
        fun `TC-12-1 운동 수정 - 정상`() {
            val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴", dayOfWeek = 0))
            val exercise =
                planExerciseRepository.save(
                    PlanExercise(
                        plan = plan,
                        exerciseId = 10L,
                        exerciseName = "벤치프레스",
                        muscleGroup = "CHEST",
                        orderIndex = 0,
                        targetSets = 3,
                        targetReps = 10,
                        targetWeight = BigDecimal("60.00"),
                        restSeconds = 90,
                    ),
                )
            // 캐시 미리 설정
            redis.opsForValue().set("plan:today:$userId", "{}")
            redis.opsForValue().set("plan:cache:$userId:${plan.id}", "{}")

            mockMvc.put("/api/v1/plans/${plan.id}/exercises/${exercise.id}") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "targetSets": 5,
                      "targetWeightKg": 80.00,
                      "notes": "무게 증가"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.data.targetSets") { value(5) }
                jsonPath("$.data.targetWeightKg") { value(80.00) }
                jsonPath("$.data.notes") { value("무게 증가") }
                // 변경하지 않은 필드는 유지
                jsonPath("$.data.exerciseName") { value("벤치프레스") }
                jsonPath("$.data.muscleGroup") { value("CHEST") }
                jsonPath("$.data.restSeconds") { value(90) }
            }

            // DB 반영 확인
            val updated = planExerciseRepository.findById(exercise.id!!).orElseThrow()
            assertThat(updated.targetSets).isEqualTo(5)
            assertThat(updated.targetWeight).isEqualByComparingTo(BigDecimal("80.00"))
            assertThat(updated.notes).isEqualTo("무게 증가")

            // 캐시 무효화 확인
            assertThat(redis.hasKey("plan:today:$userId")).isFalse()
            assertThat(redis.hasKey("plan:cache:$userId:${plan.id}")).isFalse()
        }

        @Test
        @DisplayName("TC-12-2: 존재하지 않는 planId → 404 PLAN_NOT_FOUND")
        fun `TC-12-2 운동 수정 - 존재하지 않는 planId`() {
            mockMvc.put("/api/v1/plans/99999/exercises/1") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = """{"targetSets": 4}"""
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("PLAN_NOT_FOUND") }
            }
        }

        @Test
        @DisplayName("TC-12-3: 타인 루틴의 운동 수정 시도 → 403 PLAN_ACCESS_DENIED")
        fun `TC-12-3 운동 수정 - 타인 루틴`() {
            val otherPlan =
                workoutPlanRepository.save(
                    WorkoutPlan(userId = otherUserId, name = "타인 루틴", dayOfWeek = 1),
                )
            val exercise =
                planExerciseRepository.save(
                    PlanExercise(
                        plan = otherPlan,
                        exerciseId = 10L,
                        exerciseName = "스쿼트",
                        muscleGroup = "LEGS",
                        orderIndex = 0,
                    ),
                )

            mockMvc.put("/api/v1/plans/${otherPlan.id}/exercises/${exercise.id}") {
                header("X-User-Id", userId) // userId != otherUserId
                contentType = MediaType.APPLICATION_JSON
                content = """{"targetSets": 4}"""
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("PLAN_ACCESS_DENIED") }
            }
        }

        @Test
        @DisplayName("TC-12-4: 존재하지 않는 exerciseItemId → 404 EXERCISE_NOT_FOUND")
        fun `TC-12-4 운동 수정 - 존재하지 않는 exerciseItemId`() {
            val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴", dayOfWeek = 0))

            mockMvc.put("/api/v1/plans/${plan.id}/exercises/99999") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = """{"targetSets": 4}"""
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("EXERCISE_NOT_FOUND") }
            }
        }

        @Test
        @DisplayName("TC-12-5: 다른 planId에 속한 exerciseItemId → 403 PLAN_ACCESS_DENIED")
        fun `TC-12-5 운동 수정 - 다른 plan의 exerciseItemId`() {
            val planA = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴A", dayOfWeek = 0))
            val planB = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴B", dayOfWeek = 1))
            val exerciseInB =
                planExerciseRepository.save(
                    PlanExercise(
                        plan = planB,
                        exerciseId = 20L,
                        exerciseName = "데드리프트",
                        muscleGroup = "BACK",
                        orderIndex = 0,
                    ),
                )

            // planA로 요청했지만 exerciseItemId는 planB 소속
            mockMvc.put("/api/v1/plans/${planA.id}/exercises/${exerciseInB.id}") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = """{"targetSets": 4}"""
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("PLAN_ACCESS_DENIED") }
            }
        }

        @Test
        @DisplayName("TC-12-6: muscleGroup 유효하지 않은 값 → 400 VALIDATION_FAILED")
        fun `TC-12-6 운동 수정 - muscleGroup 유효하지 않은 값`() {
            val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴", dayOfWeek = 0))
            val exercise =
                planExerciseRepository.save(
                    PlanExercise(
                        plan = plan,
                        exerciseId = 10L,
                        exerciseName = "벤치프레스",
                        muscleGroup = "CHEST",
                        orderIndex = 0,
                    ),
                )

            mockMvc.put("/api/v1/plans/${plan.id}/exercises/${exercise.id}") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = """{"muscleGroup": "INVALID_GROUP"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("VALIDATION_FAILED") }
                jsonPath("$.error.details.muscleGroup") { exists() }
            }
        }

        @Test
        @DisplayName("TC-12-7: X-User-Id 없으면 401")
        fun `TC-12-7 운동 수정 - X-User-Id 없음`() {
            mockMvc.put("/api/v1/plans/1/exercises/1") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"targetSets": 4}"""
            }.andExpect {
                status { isUnauthorized() }
            }
        }
    }

    // ─────────────────── TC-13: 운동 삭제 (DELETE /exercises/{exerciseItemId}) ───────────────────

    @Nested
    @DisplayName("TC-13: DELETE /api/v1/plans/{planId}/exercises/{exerciseItemId}")
    inner class DeleteExercise {
        @Test
        @DisplayName("TC-13-1: 정상 삭제 → 204 + DB 제거 + 캐시 무효화")
        fun `TC-13-1 운동 삭제 - 정상`() {
            val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴", dayOfWeek = 0))
            val e1 =
                planExerciseRepository.save(
                    PlanExercise(plan = plan, exerciseId = 10L, exerciseName = "벤치프레스", muscleGroup = "CHEST", orderIndex = 0),
                )
            val e2 =
                planExerciseRepository.save(
                    PlanExercise(plan = plan, exerciseId = 20L, exerciseName = "딥스", muscleGroup = "CHEST", orderIndex = 1),
                )
            // 캐시 미리 설정
            redis.opsForValue().set("plan:today:$userId", "{}")
            redis.opsForValue().set("plan:cache:$userId:${plan.id}", "{}")

            mockMvc.delete("/api/v1/plans/${plan.id}/exercises/${e1.id}") {
                header("X-User-Id", userId)
            }.andExpect {
                status { isNoContent() }
            }

            // DB에서 삭제됐는지 확인
            assertThat(planExerciseRepository.findById(e1.id!!)).isEmpty()
            // 같은 루틴의 다른 운동은 유지
            assertThat(planExerciseRepository.findById(e2.id!!)).isPresent()

            // 캐시 무효화 확인
            assertThat(redis.hasKey("plan:today:$userId")).isFalse()
            assertThat(redis.hasKey("plan:cache:$userId:${plan.id}")).isFalse()
        }

        @Test
        @DisplayName("TC-13-2: 존재하지 않는 planId → 404 PLAN_NOT_FOUND")
        fun `TC-13-2 운동 삭제 - 존재하지 않는 planId`() {
            mockMvc.delete("/api/v1/plans/99999/exercises/1") {
                header("X-User-Id", userId)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("PLAN_NOT_FOUND") }
            }
        }

        @Test
        @DisplayName("TC-13-3: 타인 루틴의 운동 삭제 시도 → 403 PLAN_ACCESS_DENIED")
        fun `TC-13-3 운동 삭제 - 타인 루틴`() {
            val otherPlan =
                workoutPlanRepository.save(
                    WorkoutPlan(userId = otherUserId, name = "타인 루틴", dayOfWeek = 1),
                )
            val exercise =
                planExerciseRepository.save(
                    PlanExercise(
                        plan = otherPlan,
                        exerciseId = 10L,
                        exerciseName = "스쿼트",
                        muscleGroup = "LEGS",
                        orderIndex = 0,
                    ),
                )

            mockMvc.delete("/api/v1/plans/${otherPlan.id}/exercises/${exercise.id}") {
                header("X-User-Id", userId) // userId != otherUserId
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("PLAN_ACCESS_DENIED") }
            }

            // DB에 그대로 남아있어야 함
            assertThat(planExerciseRepository.findById(exercise.id!!)).isPresent()
        }

        @Test
        @DisplayName("TC-13-4: 존재하지 않는 exerciseItemId → 404 EXERCISE_NOT_FOUND")
        fun `TC-13-4 운동 삭제 - 존재하지 않는 exerciseItemId`() {
            val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴", dayOfWeek = 0))

            mockMvc.delete("/api/v1/plans/${plan.id}/exercises/99999") {
                header("X-User-Id", userId)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("EXERCISE_NOT_FOUND") }
            }
        }

        @Test
        @DisplayName("TC-13-5: 다른 planId에 속한 exerciseItemId → 403 PLAN_ACCESS_DENIED")
        fun `TC-13-5 운동 삭제 - 다른 plan의 exerciseItemId`() {
            val planA = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴A", dayOfWeek = 0))
            val planB = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴B", dayOfWeek = 1))
            val exerciseInB =
                planExerciseRepository.save(
                    PlanExercise(
                        plan = planB,
                        exerciseId = 20L,
                        exerciseName = "데드리프트",
                        muscleGroup = "BACK",
                        orderIndex = 0,
                    ),
                )

            // planA로 요청했지만 exerciseItemId는 planB 소속
            mockMvc.delete("/api/v1/plans/${planA.id}/exercises/${exerciseInB.id}") {
                header("X-User-Id", userId)
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("PLAN_ACCESS_DENIED") }
            }

            // planB의 운동은 삭제되지 않아야 함
            assertThat(planExerciseRepository.findById(exerciseInB.id!!)).isPresent()
        }

        @Test
        @DisplayName("TC-13-6: X-User-Id 없으면 401")
        fun `TC-13-6 운동 삭제 - X-User-Id 없음`() {
            mockMvc.delete("/api/v1/plans/1/exercises/1")
                .andExpect { status { isUnauthorized() } }
        }
    }
}

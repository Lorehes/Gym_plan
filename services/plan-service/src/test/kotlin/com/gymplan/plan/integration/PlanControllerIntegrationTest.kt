package com.gymplan.plan.integration

import com.gymplan.plan.domain.entity.PlanExercise
import com.gymplan.plan.domain.entity.WorkoutPlan
import com.gymplan.plan.domain.repository.PlanExerciseRepository
import com.gymplan.plan.domain.repository.WorkoutPlanRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
        val otherPlan = workoutPlanRepository.save(
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
            content = """
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
        assertThat(redis.hasKey("plan:cache:${userId}:${plan.id}")).isFalse()
    }

    // ─────────────────── TC-07: orderedIds 불일치 ───────────────────

    @Test
    fun `TC-07 운동 순서 변경 - orderedIds 불일치 시 400`() {
        val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴", dayOfWeek = 0))
        repeat(4) { i ->
            planExerciseRepository.save(
                PlanExercise(
                    plan = plan, exerciseId = (i + 1).toLong(),
                    exerciseName = "운동$i", muscleGroup = "CHEST",
                    orderIndex = i,
                ),
            )
        }

        mockMvc.put("/api/v1/plans/${plan.id}/exercises/reorder") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderedIds": [1, 2, 3]}"""  // 4개여야 하는데 3개
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `운동 순서 변경 - 정상`() {
        val plan = workoutPlanRepository.save(WorkoutPlan(userId = userId, name = "루틴", dayOfWeek = 0))
        val exercises = (0 until 3).map { i ->
            planExerciseRepository.save(
                PlanExercise(
                    plan = plan, exerciseId = (i + 1).toLong(),
                    exerciseName = "운동$i", muscleGroup = "CHEST",
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
            content = """
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
}

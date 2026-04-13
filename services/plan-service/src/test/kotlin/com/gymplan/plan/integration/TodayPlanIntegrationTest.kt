package com.gymplan.plan.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.gymplan.plan.application.dto.ExerciseItemResponse
import com.gymplan.plan.application.dto.TodayPlanResponse
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/**
 * 오늘의 루틴 조회 통합 테스트.
 *
 * 명세: docs/specs/plan-service.md §8 TC-01, TC-02, TC-03, TC-04, TC-11
 *
 * 검증 항목:
 *   - TC-01: 캐시 HIT → 응답 정상 반환
 *   - TC-02: 캐시 MISS → DB 조회 후 Redis 저장
 *   - TC-03: 오늘 루틴 없음 → data: null (200, 404 아님)
 *   - TC-04: 루틴 수정 후 캐시 무효화
 *   - TC-11: exercise-catalog 호출 없이도 exerciseName/muscleGroup 반환
 */
@SpringBootTest
@AutoConfigureMockMvc
class TodayPlanIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var redis: StringRedisTemplate
    @Autowired lateinit var workoutPlanRepository: WorkoutPlanRepository
    @Autowired lateinit var planExerciseRepository: PlanExerciseRepository

    private val userId = 42L
    private val todayDow = LocalDate.now(ZoneId.of("Asia/Seoul")).dayOfWeek.value - 1

    @BeforeEach
    fun setUp() {
        planExerciseRepository.deleteAll()
        workoutPlanRepository.deleteAll()
        redis.delete(redis.keys("plan:*"))
    }

    // ─────────────────── TC-01: 캐시 HIT ───────────────────

    @Test
    fun `TC-01 캐시 HIT - Redis에 데이터 있으면 DB 쿼리 없이 반환`() {
        val cached = TodayPlanResponse(
            planId = 999L,
            name = "캐시된 루틴",
            dayOfWeek = todayDow,
            exercises = listOf(
                ExerciseItemResponse(
                    id = 1L, exerciseId = 10L,
                    exerciseName = "벤치프레스", muscleGroup = "CHEST",
                    orderIndex = 0, targetSets = 4, targetReps = 10,
                    targetWeightKg = BigDecimal("70.0"), restSeconds = 90, notes = null,
                ),
            ),
        )
        redis.opsForValue().set(
            "plan:today:$userId",
            objectMapper.writeValueAsString(cached),
        )

        mockMvc.get("/api/v1/plans/today") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.planId") { value(999) }
            jsonPath("$.data.name") { value("캐시된 루틴") }
            jsonPath("$.data.exercises[0].exerciseName") { value("벤치프레스") }
            jsonPath("$.data.exercises[0].muscleGroup") { value("CHEST") }
        }.andReturn()

        // Redis 키 여전히 존재 (TTL 갱신하지 않음)
        assertThat(redis.hasKey("plan:today:$userId")).isTrue()
    }

    // ─────────────────── TC-02: 캐시 MISS ───────────────────

    @Test
    fun `TC-02 캐시 MISS - DB 조회 후 Redis에 저장`() {
        val plan = savePlanWithExercise(userId, todayDow, "가슴 루틴", "벤치프레스", "CHEST")

        assertThat(redis.hasKey("plan:today:$userId")).isFalse()

        mockMvc.get("/api/v1/plans/today") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.planId") { value(plan.id!!) }
            jsonPath("$.data.name") { value("가슴 루틴") }
            jsonPath("$.data.exercises[0].exerciseName") { value("벤치프레스") }
            jsonPath("$.data.exercises[0].muscleGroup") { value("CHEST") }
        }

        // 캐시 미스 후 Redis 저장 확인
        assertThat(redis.hasKey("plan:today:$userId")).isTrue()
        val ttl = redis.getExpire("plan:today:$userId")
        assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(600L)
    }

    // ─────────────────── TC-03: 오늘 루틴 없음 ───────────────────

    @Test
    fun `TC-03 오늘 요일에 배정된 루틴 없음 - 200 OK data=null`() {
        val otherDow = (todayDow + 1) % 7
        savePlanWithExercise(userId, otherDow, "다른 요일 루틴", "스쿼트", "LEGS")

        mockMvc.get("/api/v1/plans/today") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data") { value(null as Any?) }
        }
    }

    // ─────────────────── TC-04: 수정 후 캐시 무효화 ───────────────────

    @Test
    fun `TC-04 루틴 수정 후 plan today 캐시 무효화`() {
        val plan = savePlanWithExercise(userId, todayDow, "원래 이름", "벤치프레스", "CHEST")
        // 캐시 미리 채우기
        mockMvc.get("/api/v1/plans/today") { header("X-User-Id", userId) }
        assertThat(redis.hasKey("plan:today:$userId")).isTrue()

        // 루틴 수정
        mockMvc.put("/api/v1/plans/${plan.id}") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "수정된 이름"}"""
        }.andExpect { status { isOk() } }

        // 캐시 삭제 확인
        assertThat(redis.hasKey("plan:today:$userId")).isFalse()
        assertThat(redis.hasKey("plan:cache:${userId}:${plan.id}")).isFalse()

        // 재조회 시 수정된 이름 반환
        mockMvc.get("/api/v1/plans/today") {
            header("X-User-Id", userId)
        }.andExpect {
            jsonPath("$.data.name") { value("수정된 이름") }
        }
    }

    // ─────────────────── TC-11: exercise-catalog 없이도 정상 ───────────────────

    @Test
    fun `TC-11 exercise-catalog 호출 없이 exerciseName과 muscleGroup이 plan_exercises에서 반환됨`() {
        // 비정규화 저장된 운동 데이터로 루틴 생성
        savePlanWithExercise(userId, todayDow, "등 루틴", "랫풀다운", "BACK")

        // exercise-catalog가 다운되어도 plan-service는 plan_exercises에서 직접 조회
        mockMvc.get("/api/v1/plans/today") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.exercises[0].exerciseName") { value("랫풀다운") }
            jsonPath("$.data.exercises[0].muscleGroup") { value("BACK") }
        }
    }

    // ─────────────────── 헬퍼 ───────────────────

    private fun savePlanWithExercise(
        userId: Long,
        dayOfWeek: Int,
        planName: String,
        exerciseName: String,
        muscleGroup: String,
    ): WorkoutPlan {
        val plan = workoutPlanRepository.save(
            WorkoutPlan(userId = userId, name = planName, dayOfWeek = dayOfWeek),
        )
        planExerciseRepository.save(
            PlanExercise(
                plan = plan,
                exerciseId = 10L,
                exerciseName = exerciseName,
                muscleGroup = muscleGroup,
                orderIndex = 0,
                targetSets = 4,
                targetReps = 10,
                targetWeight = BigDecimal("70.0"),
                restSeconds = 90,
            ),
        )
        return plan
    }
}

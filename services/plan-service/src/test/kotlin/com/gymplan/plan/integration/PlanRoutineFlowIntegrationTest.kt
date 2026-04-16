package com.gymplan.plan.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import com.gymplan.plan.domain.repository.PlanExerciseRepository
import com.gymplan.plan.domain.repository.WorkoutPlanRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * 루틴 생성 → 운동 추가 → 오늘의 루틴 조회 E2E 흐름 통합 테스트.
 *
 * 기존 [PlanControllerIntegrationTest], [TodayPlanIntegrationTest] 는 DB/Redis 를 직접 조작해
 * 개별 케이스를 독립적으로 검증하는 반면, 이 테스트는 **HTTP API 를 통해 전체 흐름을 체이닝**한다.
 *
 * ------------------------------------------------------------------
 * 시나리오 2 — 루틴 생성 → 운동 종목 추가 (TC-06)
 * ------------------------------------------------------------------
 * STEP-1. POST /api/v1/plans (오늘 요일로 루틴 생성) → 201 + planId
 * STEP-2. POST /api/v1/plans/{planId}/exercises × 2 (벤치프레스, 트라이셉스 푸시다운)
 * STEP-3. GET /api/v1/plans/{planId} → 운동 2개 포함, orderIndex 오름차순 확인
 * STEP-4. DB 직접 조회 → exerciseName/muscleGroup 비정규화 저장 확인 (exercise-catalog 호출 없음)
 *
 * ------------------------------------------------------------------
 * 시나리오 3 — 오늘의 루틴 조회: 캐시 미스 → Redis 저장 → 캐시 히트 (TC-01, TC-02, TC-04)
 * ------------------------------------------------------------------
 * STEP-5. GET /api/v1/plans/today (최초 조회 → 캐시 MISS) → 200 + DB 조회
 * STEP-6. Redis plan:today:{userId} 키 생성 확인 + TTL ≤ 600초 검증
 * STEP-7. GET /api/v1/plans/today (재조회 → 캐시 HIT) → 동일 데이터 반환
 * STEP-8. PUT /api/v1/plans/{planId} → 캐시 즉시 무효화 확인
 * STEP-9. GET /api/v1/plans/today → 수정된 이름으로 응답 (캐시 미스 후 재조회)
 *
 * 추가 검증 — 오늘 루틴 없음:
 * STEP-10. 다른 요일 루틴만 있으면 200 OK + data: null 반환 (404 아님, §인수기준 MUST)
 *
 * 명세: docs/specs/plan-service.md §8 TC-01, TC-02, TC-04, TC-06
 * 테스트 정책: Testcontainers 실제 MySQL/Redis 사용 (모킹 금지)
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlanRoutineFlowIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var redis: StringRedisTemplate
    @Autowired lateinit var workoutPlanRepository: WorkoutPlanRepository
    @Autowired lateinit var planExerciseRepository: PlanExerciseRepository

    /** 기존 테스트(userId=1,2,42)와 격리하기 위해 별도 userId 사용 */
    private val userId = 99L

    /** 서버 기준 오늘의 요일 (0=월 ~ 6=일, Asia/Seoul) */
    private val todayDow = LocalDate.now(ZoneId.of("Asia/Seoul")).dayOfWeek.value - 1

    @BeforeEach
    fun setUp() {
        planExerciseRepository.deleteAll()
        workoutPlanRepository.deleteAll()
        redis.delete(redis.keys("plan:*"))
    }

    // ─────────────────────────────────────────────────────────────────
    // 시나리오 2 + 3: 루틴 생성 → 운동 추가 → 오늘의 루틴 캐시 흐름
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("시나리오 2+3: 루틴 생성 → 운동 추가 → 오늘의 루틴 캐시 미스 → 히트 전체 흐름")
    fun `시나리오2and3_루틴생성운동추가오늘루틴캐시흐름`() {

        // ─── STEP-1: 루틴 생성 (오늘 요일로 배정) ───
        val createPlanResult = mockMvc.post("/api/v1/plans") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "가슴/삼두 루틴", "dayOfWeek": $todayDow}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.planId") { isNumber() }
            jsonPath("$.data.name") { value("가슴/삼두 루틴") }
            jsonPath("$.data.dayOfWeek") { value(todayDow) }
        }.andReturn()

        val planId = objectMapper
            .readTree(createPlanResult.response.contentAsString)
            .path("data").path("planId").asLong()
        assertThat(planId).isPositive()

        // ─── STEP-2: 운동 첫 번째 추가 (벤치프레스) ───
        mockMvc.post("/api/v1/plans/$planId/exercises") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "exerciseId":    10,
                  "exerciseName":  "벤치프레스",
                  "muscleGroup":   "CHEST",
                  "targetSets":    4,
                  "targetReps":    10,
                  "targetWeightKg": 70.0,
                  "restSeconds":   90
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.exerciseName") { value("벤치프레스") }
            jsonPath("$.data.muscleGroup") { value("CHEST") }
            jsonPath("$.data.orderIndex") { value(0) }
            jsonPath("$.data.targetSets") { value(4) }
        }

        // ─── STEP-2: 운동 두 번째 추가 (트라이셉스 푸시다운) ───
        // orderIndex 를 생략 → 마지막 순서로 자동 배정 (§인수기준 MUST)
        mockMvc.post("/api/v1/plans/$planId/exercises") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "exerciseId":   25,
                  "exerciseName": "트라이셉스 푸시다운",
                  "muscleGroup":  "ARMS",
                  "targetSets":   3,
                  "targetReps":   12,
                  "restSeconds":  60
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.exerciseName") { value("트라이셉스 푸시다운") }
            jsonPath("$.data.muscleGroup") { value("ARMS") }
            jsonPath("$.data.orderIndex") { value(1) }  // 자동 배정
        }

        // ─── STEP-3: 루틴 상세 조회 → 운동 2개 + orderIndex 오름차순 ───
        mockMvc.get("/api/v1/plans/$planId") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.exercises.length()") { value(2) }
            jsonPath("$.data.exercises[0].exerciseName") { value("벤치프레스") }
            jsonPath("$.data.exercises[0].orderIndex") { value(0) }
            jsonPath("$.data.exercises[1].exerciseName") { value("트라이셉스 푸시다운") }
            jsonPath("$.data.exercises[1].orderIndex") { value(1) }
        }

        // ─── STEP-4: DB 저장 검증 (exerciseName/muscleGroup 비정규화 확인) ───
        // plan-service 는 exercise-catalog 를 HTTP 호출하지 않으므로
        // 클라이언트가 전달한 exerciseName/muscleGroup 이 plan_exercises 에 그대로 저장됨.
        val dbExercises = planExerciseRepository.findByPlanIdOrderByOrderIndexAsc(planId)
        assertThat(dbExercises).hasSize(2)
        assertThat(dbExercises[0].exerciseName)
            .describedAs("exerciseName 은 비정규화로 plan_exercises 에 저장되어야 한다")
            .isEqualTo("벤치프레스")
        assertThat(dbExercises[0].muscleGroup).isEqualTo("CHEST")
        assertThat(dbExercises[0].exerciseId).isEqualTo(10L)
        assertThat(dbExercises[1].exerciseName).isEqualTo("트라이셉스 푸시다운")

        // ─── STEP-5: 오늘의 루틴 최초 조회 (캐시 MISS) ───
        assertThat(redis.hasKey("plan:today:$userId"))
            .describedAs("최초 조회 전에는 캐시 키가 없어야 한다")
            .isFalse()

        mockMvc.get("/api/v1/plans/today") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.planId") { value(planId) }
            jsonPath("$.data.name") { value("가슴/삼두 루틴") }
            jsonPath("$.data.exercises.length()") { value(2) }
            jsonPath("$.data.exercises[0].exerciseName") { value("벤치프레스") }
            jsonPath("$.data.exercises[0].muscleGroup") { value("CHEST") }
        }

        // ─── STEP-6: 캐시 미스 후 Redis 저장 확인 (TTL ≤ 600초 = 10분) ───
        assertThat(redis.hasKey("plan:today:$userId"))
            .describedAs("캐시 미스 후 Redis 에 plan:today:{userId} 키가 저장되어야 한다")
            .isTrue()
        val ttl = redis.getExpire("plan:today:$userId")
        assertThat(ttl)
            .describedAs("캐시 TTL 은 0 초 초과 600 초 이하여야 한다 (10분)")
            .isGreaterThan(0L)
            .isLessThanOrEqualTo(600L)

        // ─── STEP-7: 오늘의 루틴 재조회 (캐시 HIT) ───
        mockMvc.get("/api/v1/plans/today") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.planId") { value(planId) }
            jsonPath("$.data.name") { value("가슴/삼두 루틴") }
            jsonPath("$.data.exercises.length()") { value(2) }
        }

        // 캐시 키 여전히 존재 (HIT 시 TTL 갱신하지 않음)
        assertThat(redis.hasKey("plan:today:$userId"))
            .describedAs("캐시 히트 후에도 Redis 키가 유지되어야 한다")
            .isTrue()

        // ─── STEP-8: 루틴 이름 수정 → 캐시 즉시 무효화 ───
        mockMvc.put("/api/v1/plans/$planId") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "수정된 가슴/삼두 루틴"}"""
        }.andExpect { status { isOk() } }

        assertThat(redis.hasKey("plan:today:$userId"))
            .describedAs("루틴 수정 후 plan:today 캐시가 즉시 무효화되어야 한다")
            .isFalse()
        assertThat(redis.hasKey("plan:cache:$planId"))
            .describedAs("루틴 수정 후 plan:cache:{planId} 도 무효화되어야 한다")
            .isFalse()

        // ─── STEP-9: 수정 후 재조회 → 수정된 이름 반환 ───
        mockMvc.get("/api/v1/plans/today") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.name") { value("수정된 가슴/삼두 루틴") }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 시나리오 3 추가 — 오늘 요일에 루틴 없으면 200 OK + data: null
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("시나리오 3 — 오늘 요일에 배정된 루틴 없으면 200 OK data=null 반환 (404 아님, §인수기준 MUST)")
    fun `오늘루틴없으면200OK_datanull`() {
        // 다른 요일에만 루틴 생성
        val otherDow = (todayDow + 1) % 7
        mockMvc.post("/api/v1/plans") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "다른 요일 루틴", "dayOfWeek": $otherDow}"""
        }.andExpect { status { isCreated() } }

        mockMvc.get("/api/v1/plans/today") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isOk() }                      // 200 OK (404 아님 — 명세 §인수기준 MUST)
            jsonPath("$.success") { value(true) }
            jsonPath("$.data") { value(null as Any?) }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 보안 — X-User-Id 헤더 없으면 401
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("X-User-Id 헤더 없으면 401 (plan-service 는 헤더를 직접 신뢰)")
    fun `XUserId헤더없으면401`() {
        mockMvc.get("/api/v1/plans/today")
            .andExpect { status { isUnauthorized() } }
    }
}

package com.gymplan.plan.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.gymplan.plan.application.dto.ExerciseItemResponse
import com.gymplan.plan.application.dto.TodayPlanResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal

/**
 * 오늘의 루틴 조회 API 응답 시간 벤치마크.
 *
 * 성능 목표 (docs/context/performance-goals.md):
 *   - P95 < 200ms (Redis 캐시 히트 기준)
 *   - P95 < 200ms (Redis 캐시 미스 기준도 동일 목표 적용)
 *
 * 방법론:
 *   - Testcontainers 환경 (실제 Redis/MySQL) 에서 MockMvc 로 엔드투엔드 측정
 *   - 50회 워밍업 후 150회 측정 → P50/P95/P99 계산
 *   - MockMvc는 HTTP 소켓 오버헤드가 없으므로 프로덕션 대비 다소 낙관적이나,
 *     캐시 히트 경로의 Redis 왕복 및 Jackson 직렬화 비용은 동일하게 측정됨
 *
 * @Tag("benchmark") — 기본 빌드에 포함됨. CI에서 제외하려면 -Dexclude.tag=benchmark 지정.
 *
 * 명세: docs/context/performance-goals.md §1 (오늘의 루틴 P95 < 200ms)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("benchmark")
class TodayPlanLatencyBenchmarkTest : AbstractIntegrationTest() {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var objectMapper: ObjectMapper

    @Autowired lateinit var redis: StringRedisTemplate

    private val userId = 777L

    @BeforeEach
    fun setUp() {
        redis.delete(redis.keys("plan:*"))
    }

    // ─── 캐시 HIT 응답 시간 ───

    @Test
    @DisplayName("캐시 HIT P95 < 200ms — Redis → Jackson → HTTP 응답 전체 경로")
    fun `캐시HIT_P95_200ms_이하`() {
        // Redis에 오늘의 루틴 직접 세팅 (cache HIT 시나리오)
        val cachedResponse =
            TodayPlanResponse(
                planId = 1L,
                name = "가슴/삼두 루틴",
                dayOfWeek = 0,
                exercises =
                    List(5) { i ->
                        ExerciseItemResponse(
                            id = (i + 1).toLong(),
                            exerciseId = (i + 10).toLong(),
                            exerciseName = "운동종목-$i",
                            muscleGroup = "CHEST",
                            orderIndex = i,
                            targetSets = 4,
                            targetReps = 10,
                            targetWeightKg = BigDecimal("70.0"),
                            restSeconds = 90,
                            notes = null,
                        )
                    },
            )
        redis.opsForValue().set(
            "plan:today:$userId",
            objectMapper.writeValueAsString(cachedResponse),
        )

        val latencies = measureLatencies(WARMUP_COUNT + MEASURE_COUNT) { callTodayPlan() }
        reportAndAssert("캐시 HIT", latencies)
    }

    // ─── 캐시 MISS 응답 시간 ───

    @Test
    @DisplayName("캐시 MISS P95 < 200ms — Redis miss → MySQL JOIN → Redis SET → 응답 전체 경로")
    fun `캐시MISS_P95_200ms_이하`() {
        // 캐시 없는 상태 → 매번 Redis GET(miss) + DB 조회 + Redis SET 발생
        // BeforeEach에서 캐시를 비웠으므로 최초 호출은 MISS, 이후는 HIT
        // MISS 측정을 위해 각 호출 전 캐시 삭제

        val latencies =
            measureLatencies(WARMUP_COUNT + MEASURE_COUNT) {
                redis.delete("plan:today:$userId") // 매번 캐시 무효화 → 강제 MISS
                callTodayPlan()
            }
        reportAndAssert("캐시 MISS", latencies)
    }

    // ─── 헬퍼 ───

    private fun callTodayPlan(): Long {
        val start = System.nanoTime()
        mockMvc.get("/api/v1/plans/today") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isOk() }
        }
        return (System.nanoTime() - start) / 1_000_000L // ns → ms
    }

    private fun measureLatencies(
        total: Int,
        block: () -> Long,
    ): List<Long> {
        val all = (1..total).map { block() }
        return all.drop(WARMUP_COUNT) // 워밍업 제외
    }

    private fun reportAndAssert(
        label: String,
        latenciesMs: List<Long>,
    ) {
        val sorted = latenciesMs.sorted()
        val p50 = percentile(sorted, 50)
        val p95 = percentile(sorted, 95)
        val p99 = percentile(sorted, 99)
        val avg = sorted.average().toLong()
        val max = sorted.last()

        println(
            """
            ┌─────────────────────────────────────────────┐
            │ 오늘의 루틴 응답 시간 벤치마크 — $label
            ├─────────────────────────────────────────────┤
            │  측정 횟수 : ${latenciesMs.size}회 (워밍업 ${WARMUP_COUNT}회 제외)
            │  P50       : ${p50}ms
            │  P95       : ${p95}ms  ← 목표: < 200ms
            │  P99       : ${p99}ms
            │  평균      : ${avg}ms
            │  최대      : ${max}ms
            └─────────────────────────────────────────────┘
            """.trimIndent(),
        )

        assertThat(p95)
            .describedAs("$label P95 응답 시간이 200ms 이하여야 한다 (docs/context/performance-goals.md)")
            .isLessThanOrEqualTo(P95_TARGET_MS)
    }

    private fun percentile(
        sorted: List<Long>,
        pct: Int,
    ): Long {
        val idx = (sorted.size * pct / 100.0).toInt().coerceAtMost(sorted.size - 1)
        return sorted[idx]
    }

    companion object {
        private const val WARMUP_COUNT = 50
        private const val MEASURE_COUNT = 150
        private const val P95_TARGET_MS = 200L
    }
}

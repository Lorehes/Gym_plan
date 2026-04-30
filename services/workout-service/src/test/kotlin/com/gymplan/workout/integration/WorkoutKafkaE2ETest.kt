package com.gymplan.workout.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.gymplan.workout.domain.repository.WorkoutSessionRepository
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.util.Properties

/**
 * Phase 2 E2E — 시나리오 A
 *
 * 운동 세션 시작 → 세트 기록 → 완료
 *   → workout.set.logged × N Kafka 이벤트 발행 확인
 *   → workout.session.completed × 1 Kafka 이벤트 발행 확인
 *
 * 참조: docs/specs/workout-service.md §TC-04, TC-08, TC-11
 *       docs/architecture/kafka-events.md
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkoutKafkaE2ETest : AbstractIntegrationTest() {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var objectMapper: ObjectMapper

    @Autowired lateinit var sessionRepository: WorkoutSessionRepository

    private val userId = 99L

    private lateinit var setLoggedConsumer: KafkaConsumer<String, String>
    private lateinit var sessionCompletedConsumer: KafkaConsumer<String, String>

    @BeforeEach
    fun setUp() {
        sessionRepository.deleteAll()
        setLoggedConsumer = buildConsumer("e2e-set-${System.nanoTime()}", TOPIC_SET_LOGGED)
        sessionCompletedConsumer = buildConsumer("e2e-completed-${System.nanoTime()}", TOPIC_SESSION_COMPLETED)
    }

    @AfterEach
    fun tearDown() {
        runCatching { setLoggedConsumer.close() }
        runCatching { sessionCompletedConsumer.close() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // E2E-A: 전체 운동 흐름 + Kafka 이벤트 검증
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName(
        "E2E-A: 세션 시작 → 세트 3개 기록 → 완료 " +
            "→ workout.set.logged × 3 + workout.session.completed × 1 발행 검증",
    )
    fun `E2E-A 전체 운동 흐름 Kafka 이벤트 검증`() {
        // ── Step 1: 세션 시작 ──
        val sessionId = startSession(planId = 12, planName = "가슴/삼두 루틴")

        // ── Step 2: 세트 3개 기록 (벤치프레스 2세트 + 스쿼트 1세트) ──
        recordSet(sessionId, "10", "벤치프레스", "CHEST", setNo = 1, reps = 10, weightKg = 70.0)
        recordSet(sessionId, "10", "벤치프레스", "CHEST", setNo = 2, reps = 8, weightKg = 75.0)
        recordSet(sessionId, "20", "스쿼트", "LEGS", setNo = 1, reps = 10, weightKg = 80.0)

        // ── Step 3: 운동 완료 ──
        mockMvc.post("/api/v1/sessions/$sessionId/complete") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"notes": "E2E 테스트"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalSets") { value(3) }
        }

        // ── Step 4: workout.set.logged 이벤트 3건 검증 ──
        val setEvents = pollEvents(setLoggedConsumer, expectedCount = 3, sessionId = sessionId)
        assertThat(setEvents).`as`("세트 기록 이벤트 3건이 발행되어야 함").hasSize(3)
        setEvents.forEach { evt ->
            assertThat(evt["eventType"]).isEqualTo("WORKOUT_SET_LOGGED")
            assertThat(evt["userId"]).isEqualTo(userId.toString())
            assertThat(evt["sessionId"]).isEqualTo(sessionId)
        }
        assertThat(setEvents.map { it["exerciseId"] }.toSet())
            .containsExactlyInAnyOrder("10", "20")

        // ── Step 5: workout.session.completed 이벤트 1건 검증 ──
        val completedEvents = pollEvents(sessionCompletedConsumer, expectedCount = 1, sessionId = sessionId)
        assertThat(completedEvents).`as`("세션 완료 이벤트 1건이 발행되어야 함").hasSize(1)
        val evt = completedEvents[0]
        assertThat(evt["eventType"]).isEqualTo("WORKOUT_SESSION_COMPLETED")
        assertThat(evt["userId"]).isEqualTo(userId.toString())
        assertThat(evt["sessionId"]).isEqualTo(sessionId)
        assertThat(evt["totalSets"]).isEqualTo(3)
        assertThat(evt["planName"]).isEqualTo("가슴/삼두 루틴")
        @Suppress("UNCHECKED_CAST")
        val muscleGroups = evt["muscleGroups"] as List<String>
        assertThat(muscleGroups).containsExactlyInAnyOrder("CHEST", "LEGS")
    }

    @Test
    @DisplayName("E2E-A-2: API 응답은 세트 기록 완료 즉시 반환, Kafka 이벤트는 비동기 발행 (TC-11)")
    fun `E2E-A-2 세트 기록 API 응답 반환 후 Kafka 이벤트 비동기 발행`() {
        val sessionId = startSession()

        // API 호출 시간과 Kafka 이벤트 발행 시간 측정
        val apiCallTime = System.currentTimeMillis()
        recordSet(sessionId, "10", "벤치프레스", "CHEST", setNo = 1, reps = 10, weightKg = 70.0)
        val apiReturnTime = System.currentTimeMillis()

        // API는 즉시 반환됨 (Kafka 발행 대기 없음)
        assertThat(apiReturnTime - apiCallTime).`as`("API 응답은 300ms 이내").isLessThan(300)

        // Kafka 이벤트는 비동기로 발행됨 (API 응답 이후)
        val setEvents = pollEvents(setLoggedConsumer, expectedCount = 1, sessionId = sessionId)
        assertThat(setEvents).hasSize(1)
        assertThat(setEvents[0]["eventType"]).isEqualTo("WORKOUT_SET_LOGGED")
    }

    @Test
    @DisplayName("E2E-A-3: 중복 완료 요청 시 workout.session.completed 이벤트 중복 발행 없음 (TC-10)")
    fun `E2E-A-3 중복 완료 시 Kafka 이벤트 한 번만 발행`() {
        val sessionId = startSession()

        // 첫 번째 완료
        mockMvc.post("/api/v1/sessions/$sessionId/complete") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isOk() } }

        // 두 번째 완료 시도 → 409
        mockMvc.post("/api/v1/sessions/$sessionId/complete") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isConflict() } }

        // 첫 번째 완료 이벤트 수신
        val events = pollEvents(sessionCompletedConsumer, expectedCount = 1, sessionId = sessionId)
        assertThat(events).hasSize(1)

        // 추가 이벤트가 없는지 2초 더 폴링하여 확인
        Thread.sleep(2_000)
        val extra = pollEvents(sessionCompletedConsumer, expectedCount = 0, sessionId = sessionId, timeoutMs = 200)
        assertThat(events.size + extra.size).`as`("이벤트는 정확히 1건만 발행되어야 함").isEqualTo(1)
    }

    // ──────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────────────

    private fun startSession(
        planId: Int? = null,
        planName: String? = null,
    ): String {
        val body = if (planId != null) """{"planId": $planId, "planName": "$planName"}""" else "{}"
        val result =
            mockMvc.post("/api/v1/sessions") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isCreated() } }.andReturn()
        return objectMapper.readTree(result.response.contentAsString)["data"]["sessionId"].asText()
    }

    private fun recordSet(
        sessionId: String,
        exerciseId: String,
        exerciseName: String,
        muscleGroup: String,
        setNo: Int,
        reps: Int,
        weightKg: Double,
    ) {
        mockMvc.post("/api/v1/sessions/$sessionId/sets") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "exerciseId": "$exerciseId",
                  "exerciseName": "$exerciseName",
                  "muscleGroup": "$muscleGroup",
                  "setNo": $setNo,
                  "reps": $reps,
                  "weightKg": $weightKg,
                  "isSuccess": true
                }
                """.trimIndent()
        }.andExpect { status { isCreated() } }
    }

    private fun buildConsumer(
        groupId: String,
        topic: String,
    ): KafkaConsumer<String, String> =
        KafkaConsumer<String, String>(
            Properties().apply {
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
                put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
            },
        ).also { it.subscribe(listOf(topic)) }

    private fun pollEvents(
        consumer: KafkaConsumer<String, String>,
        expectedCount: Int,
        sessionId: String,
        timeoutMs: Long = 10_000,
    ): List<Map<*, *>> {
        val events = mutableListOf<Map<*, *>>()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (events.size < expectedCount && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(300)).forEach { record ->
                val node = objectMapper.readValue(record.value(), Map::class.java)
                if (node["sessionId"] == sessionId) events += node
            }
        }
        return events
    }

    companion object {
        private const val TOPIC_SET_LOGGED = "workout.set.logged"
        private const val TOPIC_SESSION_COMPLETED = "workout.session.completed"
    }
}

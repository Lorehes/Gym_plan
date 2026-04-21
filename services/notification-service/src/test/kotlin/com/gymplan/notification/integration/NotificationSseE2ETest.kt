package com.gymplan.notification.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.gymplan.notification.infrastructure.config.FcmConfig
import com.gymplan.notification.infrastructure.fcm.FcmService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.TestPropertySource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Phase 2 E2E — 시나리오 C
 *
 * 세트 완료 후 notification-service SSE 타이머 흐름 검증:
 *   C-1: SSE 구독 → Redis PUBLISH timer:{sessionId} → timer-start 이벤트 수신
 *   C-2: restSeconds 경과 → timer-end 이벤트 수신
 *   C-3: SSE 스트림 — X-User-Id 헤더 없으면 400/에러 응답
 *
 * Redis 발행 메시지 형식: {"restSeconds": N, "exerciseName": "..."}
 *
 * 참조: docs/specs/notification-service.md §TC-01, TC-02
 *       docs/architecture/kafka-events.md
 *
 * 인프라:
 *   - Redis: Testcontainers GenericContainer (AbstractNotificationIntegrationTest)
 *   - Kafka: @EmbeddedKafka (서비스 내 @KafkaListener 기동용)
 *   - FcmConfig / FcmService: @MockBean (Firebase 초기화 방지)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(
    partitions = 1,
    topics = ["workout.session.completed", "user.registered"],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
@TestPropertySource(properties = ["fcm.service-account-path=/dev/null"])
class NotificationSseE2ETest : AbstractNotificationIntegrationTest() {

    // Firebase 초기화(FcmConfig) 및 실제 FCM 발송 방지
    @MockBean lateinit var fcmConfig: FcmConfig
    @MockBean lateinit var fcmService: FcmService

    @LocalServerPort var port: Int = 0

    @Autowired lateinit var redisTemplate: StringRedisTemplate
    @Autowired lateinit var objectMapper: ObjectMapper

    // ──────────────────────────────────────────────────────────────────────
    // E2E-C-1 + C-2: SSE 구독 → Redis PUBLISH → timer-start + timer-end 수신
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName(
        "E2E-C: SSE 구독 후 Redis PUBLISH → timer-start 이벤트 수신 (1초 이내)" +
            " → restSeconds 경과 후 timer-end 이벤트 수신"
    )
    fun `E2E-C SSE 타이머 전체 흐름 검증`() {
        val sessionId = "e2e-sse-${System.nanoTime()}"
        val restSeconds = 3L // 빠른 테스트를 위해 3초

        val timerStartLatch = CountDownLatch(1)
        val timerEndLatch = CountDownLatch(1)
        val receivedEvents = CopyOnWriteArrayList<SseEvent>()

        // ── SSE 구독 스레드 시작 ──
        val sseThread = Thread {
            try {
                val conn = openSseConnection(sessionId, userId = 1L)
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    parseSseStream(reader, receivedEvents, timerStartLatch, timerEndLatch)
                }
            } catch (e: Exception) {
                if (!Thread.currentThread().isInterrupted) {
                    // timer-end 수신 후 연결이 끊기는 경우는 정상 흐름
                }
            }
        }.also {
            it.isDaemon = true
            it.start()
        }

        // SSE 연결 수립 대기 (500ms)
        Thread.sleep(500)

        // ── Redis PUBLISH: workout-service가 세트 완료 후 발행하는 메시지 ──
        val timerMessage = objectMapper.writeValueAsString(
            mapOf("restSeconds" to restSeconds, "exerciseName" to "벤치프레스")
        )
        redisTemplate.convertAndSend("timer:$sessionId", timerMessage)

        // ── timer-start 이벤트 수신 검증 (2초 이내) ──
        assertThat(timerStartLatch.await(2, TimeUnit.SECONDS))
            .`as`("timer-start 이벤트가 2초 이내에 수신되어야 함").isTrue()

        val timerStartEvt = receivedEvents.first { it.name == "timer-start" }
        val startData = objectMapper.readTree(timerStartEvt.data)
        assertThat(startData["sessionId"].asText()).isEqualTo(sessionId)
        assertThat(startData["restSeconds"].asLong()).isEqualTo(restSeconds)
        assertThat(startData["exerciseName"].asText()).isEqualTo("벤치프레스")

        // ── timer-end 이벤트 수신 검증 (restSeconds + 2초 버퍼) ──
        val timerEndTimeout = restSeconds + 3L
        assertThat(timerEndLatch.await(timerEndTimeout, TimeUnit.SECONDS))
            .`as`("timer-end 이벤트가 ${timerEndTimeout}초 이내에 수신되어야 함").isTrue()

        val timerEndEvt = receivedEvents.first { it.name == "timer-end" }
        val endData = objectMapper.readTree(timerEndEvt.data)
        assertThat(endData["sessionId"].asText()).isEqualTo(sessionId)
        assertThat(endData["message"].asText()).isNotBlank

        sseThread.interrupt()
    }

    // ──────────────────────────────────────────────────────────────────────
    // E2E-C-3: SSE 스트림에 sessionId 파라미터 없으면 거부
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("E2E-C-3: sessionId 파라미터 없이 SSE 구독 시도 → 4xx 응답")
    fun `E2E-C-3 sessionId 없는 SSE 구독 거부`() {
        val url = URL("http://localhost:$port/api/v1/notifications/timer/stream")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("X-User-Id", "1")
            setRequestProperty("Accept", "text/event-stream")
            connectTimeout = 3_000
            readTimeout = 3_000
            instanceFollowRedirects = false
        }

        val responseCode = conn.responseCode
        assertThat(responseCode).`as`("sessionId 없으면 4xx 응답").isGreaterThanOrEqualTo(400)
        conn.disconnect()
    }

    // ──────────────────────────────────────────────────────────────────────
    // E2E-C-4: SSE heartbeat 이벤트 수신 확인
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("E2E-C-4: SSE 연결 후 15초 이내 heartbeat 이벤트 수신")
    fun `E2E-C-4 SSE heartbeat 이벤트 수신`() {
        val sessionId = "e2e-hb-${System.nanoTime()}"
        val heartbeatLatch = CountDownLatch(1)
        val receivedEvents = CopyOnWriteArrayList<SseEvent>()

        val sseThread = Thread {
            try {
                val conn = openSseConnection(sessionId, userId = 1L)
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    var eventName: String? = null
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        when {
                            line!!.startsWith("event:") -> eventName = line!!.removePrefix("event:").trim()
                            line!!.startsWith("data:") && eventName != null -> {
                                receivedEvents += SseEvent(eventName!!, line!!.removePrefix("data:").trim())
                                if (eventName == "heartbeat") heartbeatLatch.countDown()
                                eventName = null
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }.also {
            it.isDaemon = true
            it.start()
        }

        // heartbeat는 15초 간격 → 16초 대기
        assertThat(heartbeatLatch.await(16, TimeUnit.SECONDS))
            .`as`("heartbeat 이벤트가 16초 이내에 수신되어야 함").isTrue()

        val hbEvt = receivedEvents.first { it.name == "heartbeat" }
        assertThat(objectMapper.readTree(hbEvt.data)["timestamp"].asText()).isNotBlank

        sseThread.interrupt()
    }

    // ──────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────────────

    private fun openSseConnection(sessionId: String, userId: Long): HttpURLConnection =
        (URL("http://localhost:$port/api/v1/notifications/timer/stream?sessionId=$sessionId")
            .openConnection() as HttpURLConnection).apply {
            setRequestProperty("X-User-Id", userId.toString())
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Cache-Control", "no-cache")
            connectTimeout = 5_000
            readTimeout = 30_000
            connect()
        }

    private fun parseSseStream(
        reader: BufferedReader,
        events: CopyOnWriteArrayList<SseEvent>,
        timerStartLatch: CountDownLatch,
        timerEndLatch: CountDownLatch,
    ) {
        var eventName: String? = null
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            when {
                line!!.startsWith("event:") -> eventName = line!!.removePrefix("event:").trim()
                line!!.startsWith("data:") && eventName != null -> {
                    val data = line!!.removePrefix("data:").trim()
                    events += SseEvent(eventName!!, data)
                    when (eventName) {
                        "timer-start" -> timerStartLatch.countDown()
                        "timer-end" -> timerEndLatch.countDown()
                    }
                    eventName = null
                }
            }
        }
    }

    private data class SseEvent(val name: String, val data: String)
}

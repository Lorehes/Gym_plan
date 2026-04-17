package com.gymplan.notification.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.gymplan.notification.application.dto.HeartbeatEvent
import com.gymplan.notification.application.dto.TimerEndEvent
import com.gymplan.notification.application.dto.TimerPublishMessage
import com.gymplan.notification.application.dto.TimerStartEvent
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Service
class TimerSseService(
    private val listenerContainer: RedisMessageListenerContainer,
    private val scheduler: ScheduledExecutorService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(TimerSseService::class.java)
    private val activeEmitters = ConcurrentHashMap<String, EmitterHolder>()

    private data class EmitterHolder(
        val emitter: SseEmitter,
        val userId: Long,
        val listener: MessageListener,
        val heartbeatFuture: ScheduledFuture<*>,
    )

    fun subscribe(
        sessionId: String,
        userId: Long,
    ): SseEmitter {
        val emitter = SseEmitter(SSE_TIMEOUT_MS)

        val listener =
            MessageListener { message, _ ->
                handleTimerMessage(emitter, sessionId, message.body)
            }

        val channel = ChannelTopic("timer:$sessionId")
        listenerContainer.addMessageListener(listener, channel)

        val heartbeatFuture =
            scheduler.scheduleAtFixedRate(
                { sendHeartbeat(emitter) },
                HEARTBEAT_INTERVAL_SEC,
                HEARTBEAT_INTERVAL_SEC,
                TimeUnit.SECONDS,
            )

        activeEmitters[sessionId] = EmitterHolder(emitter, userId, listener, heartbeatFuture)

        emitter.onCompletion { cleanup(sessionId) }
        emitter.onTimeout { cleanup(sessionId) }
        emitter.onError { cleanup(sessionId) }

        log.info("SSE 타이머 구독 시작: sessionId={}, userId={}", sessionId, userId)
        return emitter
    }

    private fun handleTimerMessage(
        emitter: SseEmitter,
        sessionId: String,
        body: ByteArray,
    ) {
        val payload = String(body)
        val (restSeconds, exerciseName) = parseMessage(payload)

        sendEvent(emitter, "timer-start", TimerStartEvent(sessionId, restSeconds, exerciseName))

        scheduler.schedule(
            { sendEvent(emitter, "timer-end", TimerEndEvent(sessionId)) },
            restSeconds,
            TimeUnit.SECONDS,
        )

        log.info("타이머 시작: sessionId={}, restSeconds={}", sessionId, restSeconds)
    }

    private fun parseMessage(payload: String): Pair<Long, String> =
        try {
            val msg = objectMapper.readValue(payload, TimerPublishMessage::class.java)
            msg.restSeconds to msg.exerciseName
        } catch (_: Exception) {
            // 단순 숫자 형식 (하위 호환)
            (payload.trim().toLongOrNull() ?: 60L) to ""
        }

    private fun sendHeartbeat(emitter: SseEmitter) {
        sendEvent(emitter, "heartbeat", HeartbeatEvent(Instant.now().toString()))
    }

    private fun sendEvent(
        emitter: SseEmitter,
        eventName: String,
        data: Any,
    ) {
        try {
            emitter.send(
                SseEmitter.event()
                    .name(eventName)
                    .data(objectMapper.writeValueAsString(data)),
            )
        } catch (_: IOException) {
            // 연결 끊김 — 다음 cleanup 콜백에서 정리됨
        }
    }

    private fun cleanup(sessionId: String) {
        activeEmitters.remove(sessionId)?.let { holder ->
            holder.heartbeatFuture.cancel(false)
            listenerContainer.removeMessageListener(holder.listener)
            log.info("SSE 타이머 구독 종료: sessionId={}", sessionId)
        }
    }

    companion object {
        private const val SSE_TIMEOUT_MS = 30 * 60 * 1000L  // 30분
        private const val HEARTBEAT_INTERVAL_SEC = 15L
    }
}

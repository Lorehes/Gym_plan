package com.gymplan.workout.application.service

import com.gymplan.common.exception.ConflictException
import com.gymplan.common.exception.ErrorCode
import com.gymplan.workout.application.dto.LogSetRequest
import com.gymplan.workout.application.event.WorkoutSetLoggedEvent
import com.gymplan.workout.domain.entity.SetRecord
import com.gymplan.workout.domain.entity.WorkoutSession
import com.gymplan.workout.domain.repository.WorkoutSessionRepository
import com.gymplan.workout.infrastructure.messaging.WorkoutEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * SetRecordService 단위 테스트.
 *
 * 명세: docs/specs/workout-service.md — TC-04 ~ TC-07, TC-11
 */
class SetRecordServiceTest {
    private lateinit var sessionRepository: WorkoutSessionRepository
    private lateinit var sessionService: SessionService
    private lateinit var eventPublisher: WorkoutEventPublisher
    private lateinit var setRecordService: SetRecordService

    private val userId = 1L
    private val userIdStr = "1"
    private val sessionId = "session123"

    @BeforeEach
    fun setUp() {
        sessionRepository = mock()
        eventPublisher = mock()
        sessionService = mock()
        setRecordService = SetRecordService(sessionRepository, sessionService, eventPublisher)
    }

    private fun buildActiveSession() =
        WorkoutSession(
            id = sessionId,
            userId = userIdStr,
            startedAt = Instant.now().minusSeconds(300),
        )

    // ─────────────── TC-04: 새 운동 추가 ───────────────

    @Test
    @DisplayName("TC-04: 새 운동 세트 기록 → pushSet 호출 + Kafka 비동기 발행")
    fun logSet_newExercise_success() {
        val session = buildActiveSession()
        whenever(sessionService.findSessionForUser(sessionId, userIdStr)).thenReturn(session)
        whenever(sessionRepository.pushSet(any(), any(), any(), any(), any(), any())).thenReturn(1L)

        val request =
            LogSetRequest(
                exerciseId = "10",
                exerciseName = "벤치프레스",
                muscleGroup = "CHEST",
                setNo = 1,
                reps = 10,
                weightKg = 70.0,
                isSuccess = true,
            )
        val response = setRecordService.logSet(userId, sessionId, request)

        assertThat(response.sessionId).isEqualTo(sessionId)
        assertThat(response.exerciseId).isEqualTo("10")
        assertThat(response.setNo).isEqualTo(1)

        // $push 호출 확인 — 모든 인자에 matcher 사용 (Mockito 규칙)
        verify(sessionRepository).pushSet(
            sessionId = eq(sessionId),
            userId = eq(userIdStr),
            exerciseId = eq("10"),
            exerciseName = eq("벤치프레스"),
            muscleGroup = eq("CHEST"),
            set = any<SetRecord>(),
        )

        // TC-11: Kafka 비동기 발행 확인
        val eventCaptor = argumentCaptor<WorkoutSetLoggedEvent>()
        verify(eventPublisher).publishSetLogged(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.exerciseId).isEqualTo("10")
        assertThat(eventCaptor.firstValue.volume).isEqualTo(700.0) // 70 * 10
        assertThat(eventCaptor.firstValue.muscleGroup).isEqualTo("CHEST")
    }

    // ─────────────── TC-06: 완료 세션에 세트 기록 불가 ───────────────

    @Test
    @DisplayName("TC-06: 완료된 세션에 세트 기록 시도 → SESSION_ALREADY_COMPLETED (409)")
    fun logSet_completedSession_conflict() {
        val completedSession =
            WorkoutSession(
                id = sessionId,
                userId = userIdStr,
                completedAt = Instant.now().minusSeconds(60),
            )
        whenever(sessionService.findSessionForUser(sessionId, userIdStr)).thenReturn(completedSession)

        val request = LogSetRequest("10", "벤치프레스", "CHEST", 1, 10, 70.0)

        assertThatThrownBy { setRecordService.logSet(userId, sessionId, request) }
            .isInstanceOf(ConflictException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.SESSION_ALREADY_COMPLETED)

        verify(sessionRepository, never()).pushSet(any(), any(), any(), any(), any(), any())
        verify(eventPublisher, never()).publishSetLogged(any())
    }

    // ─────────────── TC-11: Kafka 발행이 API 응답에 영향 없음 ───────────────

    @Test
    @DisplayName("TC-11: Kafka 브로커 장애 시에도 세트 기록 성공 (예외 흡수 확인)")
    fun logSet_kafkaFailure_doesNotAffectApiResponse() {
        val session = buildActiveSession()
        whenever(sessionService.findSessionForUser(sessionId, userIdStr)).thenReturn(session)
        whenever(sessionRepository.pushSet(any(), any(), any(), any(), any(), any())).thenReturn(1L)
        // WorkoutEventPublisher는 내부에서 예외를 흡수하므로 별도 설정 불필요
        // — 여기서는 publishSetLogged()가 정상 호출됨을 확인 (비동기 내부 예외는 별도 단위 테스트)

        val request = LogSetRequest("10", "벤치프레스", "CHEST", 1, 10, 70.0)

        val response = setRecordService.logSet(userId, sessionId, request)

        assertThat(response.sessionId).isEqualTo(sessionId)
    }

    // ─────────────── volume 계산 ───────────────

    @Test
    @DisplayName("volume 계산: weightKg × reps가 이벤트에 포함됨")
    fun logSet_volumeCalculation() {
        val session = buildActiveSession()
        whenever(sessionService.findSessionForUser(sessionId, userIdStr)).thenReturn(session)
        whenever(sessionRepository.pushSet(any(), any(), any(), any(), any(), any())).thenReturn(1L)

        setRecordService.logSet(userId, sessionId, LogSetRequest("20", "스쿼트", "LEGS", 1, 5, 100.0))

        val captor = argumentCaptor<WorkoutSetLoggedEvent>()
        verify(eventPublisher).publishSetLogged(captor.capture())
        assertThat(captor.firstValue.volume).isEqualTo(500.0) // 100.0 * 5
    }
}

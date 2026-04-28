package com.gymplan.workout.application.service

import com.gymplan.common.exception.ConflictException
import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.NotFoundException
import com.gymplan.common.exception.UnauthorizedException
import com.gymplan.workout.application.dto.CompleteSessionRequest
import com.gymplan.workout.application.dto.StartSessionRequest
import com.gymplan.workout.application.event.WorkoutSessionCompletedEvent
import com.gymplan.workout.domain.entity.SessionExercise
import com.gymplan.workout.domain.entity.SessionStatus
import com.gymplan.workout.domain.entity.SetRecord
import com.gymplan.workout.domain.entity.WorkoutSession
import com.gymplan.workout.domain.repository.WorkoutSessionRepository
import com.gymplan.workout.infrastructure.messaging.WorkoutEventPublisher
import com.gymplan.workout.infrastructure.metrics.WorkoutMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * SessionService 단위 테스트.
 *
 * 명세: docs/specs/workout-service.md — TC-01 ~ TC-03, TC-08 ~ TC-10
 */
class SessionServiceTest {
    private lateinit var sessionRepository: WorkoutSessionRepository
    private lateinit var eventPublisher: WorkoutEventPublisher
    private lateinit var sessionService: SessionService

    private val userId = 1L
    private val userIdStr = "1"

    @BeforeEach
    fun setUp() {
        sessionRepository = mock()
        eventPublisher = mock()
        sessionService = SessionService(sessionRepository, eventPublisher, WorkoutMetrics(SimpleMeterRegistry()))
    }

    // ─────────────── TC-01: 정상 세션 시작 ───────────────

    @Nested
    @DisplayName("세션 시작")
    inner class StartSession {
        @Test
        @DisplayName("TC-01: 진행 중 세션 없을 때 플랜 지정 세션 시작 → 201")
        fun startSession_withPlan_success() {
            whenever(sessionRepository.findByUserIdAndCompletedAtIsNull(userIdStr)).thenReturn(null)
            val saved = WorkoutSession(id = "abc123", userId = userIdStr, planId = "12", planName = "가슴/삼두 루틴")
            whenever(sessionRepository.save(any<WorkoutSession>())).thenReturn(saved)

            val response = sessionService.startSession(userId, StartSessionRequest(planId = 12, planName = "가슴/삼두 루틴"))

            assertThat(response.sessionId).isEqualTo("abc123")
            assertThat(response.status).isEqualTo("IN_PROGRESS")
        }

        @Test
        @DisplayName("TC-02: planId 없이 자유 운동 세션 시작 → planId=null")
        fun startSession_freeWorkout_planIdNull() {
            whenever(sessionRepository.findByUserIdAndCompletedAtIsNull(userIdStr)).thenReturn(null)
            val saved = WorkoutSession(id = "abc456", userId = userIdStr)
            whenever(sessionRepository.save(any<WorkoutSession>())).thenReturn(saved)

            sessionService.startSession(userId, StartSessionRequest())

            val captor = argumentCaptor<WorkoutSession>()
            verify(sessionRepository).save(captor.capture())
            assertThat(captor.firstValue.planId).isNull()
            assertThat(captor.firstValue.planName).isNull()
        }

        @Test
        @DisplayName("TC-03: 이미 IN_PROGRESS 세션 있으면 SESSION_ALREADY_ACTIVE (409)")
        fun startSession_alreadyActive_conflict() {
            val active = WorkoutSession(id = "existing", userId = userIdStr)
            whenever(sessionRepository.findByUserIdAndCompletedAtIsNull(userIdStr)).thenReturn(active)

            assertThatThrownBy { sessionService.startSession(userId, StartSessionRequest()) }
                .isInstanceOf(ConflictException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SESSION_ALREADY_ACTIVE)

            verify(sessionRepository, never()).save(any<WorkoutSession>())
        }
    }

    // ─────────────── TC-08: 운동 완료 ───────────────

    @Nested
    @DisplayName("운동 완료")
    inner class CompleteSession {
        @Test
        @DisplayName("TC-08: 정상 완료 → durationSec / totalVolume / totalSets 계산 후 반환")
        fun completeSession_success() {
            val sets =
                listOf(
                    SetRecord(setNo = 1, reps = 10, weightKg = 70.0, isSuccess = true),
                    SetRecord(setNo = 2, reps = 8, weightKg = 70.0, isSuccess = true),
                )
            val session =
                WorkoutSession(
                    id = "s1",
                    userId = userIdStr,
                    startedAt = Instant.now().minusSeconds(3600),
                    exercises =
                        listOf(
                            SessionExercise("10", "벤치프레스", "CHEST", sets),
                        ),
                )
            whenever(sessionRepository.findByIdAndUserId("s1", userIdStr)).thenReturn(session)
            whenever(sessionRepository.completeSession(any(), any(), any(), any(), any(), any(), anyOrNull()))
                .thenReturn(1L)

            val response = sessionService.completeSession(userId, "s1", CompleteSessionRequest(notes = "좋음"))

            assertThat(response.sessionId).isEqualTo("s1")
            // totalVolume = 70*10 + 70*8 = 700 + 560 = 1260
            assertThat(response.totalVolume).isEqualTo(1260.0)
            assertThat(response.totalSets).isEqualTo(2)
            assertThat(response.durationSec).isGreaterThan(3500)

            // Kafka 비동기 발행 확인
            val eventCaptor = argumentCaptor<WorkoutSessionCompletedEvent>()
            verify(eventPublisher).publishSessionCompleted(eventCaptor.capture())
            assertThat(eventCaptor.firstValue.sessionId).isEqualTo("s1")
            assertThat(eventCaptor.firstValue.totalVolume).isEqualTo(1260.0)
            assertThat(eventCaptor.firstValue.muscleGroups).containsExactly("CHEST")
        }

        @Test
        @DisplayName("TC-09: totalVolume 계산 검증 — 다종목 합산")
        fun completeSession_totalVolumeCalculation() {
            val session =
                WorkoutSession(
                    id = "s2",
                    userId = userIdStr,
                    startedAt = Instant.now().minusSeconds(600),
                    exercises =
                        listOf(
                            SessionExercise(
                                "10",
                                "벤치프레스",
                                "CHEST",
                                listOf(
                                    SetRecord(1, 10, 70.0, true),
                                    SetRecord(2, 8, 70.0, true),
                                ),
                            ),
                            SessionExercise(
                                "20",
                                "스쿼트",
                                "LEGS",
                                listOf(SetRecord(1, 10, 80.0, true)),
                            ),
                        ),
                )
            whenever(sessionRepository.findByIdAndUserId("s2", userIdStr)).thenReturn(session)
            whenever(sessionRepository.completeSession(any(), any(), any(), any(), any(), any(), anyOrNull()))
                .thenReturn(1L)

            val response = sessionService.completeSession(userId, "s2", CompleteSessionRequest())

            // 70*10 + 70*8 + 80*10 = 700 + 560 + 800 = 2060
            assertThat(response.totalVolume).isEqualTo(2060.0)
            assertThat(response.totalSets).isEqualTo(3)
        }

        @Test
        @DisplayName("TC-10: 이미 완료된 세션 재완료 → SESSION_ALREADY_COMPLETED (409)")
        fun completeSession_alreadyCompleted() {
            val completedSession =
                WorkoutSession(
                    id = "s3",
                    userId = userIdStr,
                    completedAt = Instant.now().minusSeconds(60),
                )
            whenever(sessionRepository.findByIdAndUserId("s3", userIdStr)).thenReturn(completedSession)

            assertThatThrownBy { sessionService.completeSession(userId, "s3", CompleteSessionRequest()) }
                .isInstanceOf(ConflictException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SESSION_ALREADY_COMPLETED)

            verify(eventPublisher, never()).publishSessionCompleted(any())
        }

        @Test
        @DisplayName("DB 레벨 동시성 — modifiedCount=0이면 SESSION_ALREADY_COMPLETED")
        fun completeSession_racecondition_alreadyCompleted() {
            val session = WorkoutSession(id = "s4", userId = userIdStr, startedAt = Instant.now().minusSeconds(300))
            whenever(sessionRepository.findByIdAndUserId("s4", userIdStr)).thenReturn(session)
            // 원자적 업데이트 실패 (다른 스레드가 먼저 완료)
            whenever(sessionRepository.completeSession(any(), any(), any(), any(), any(), any(), anyOrNull()))
                .thenReturn(0L)

            assertThatThrownBy { sessionService.completeSession(userId, "s4", CompleteSessionRequest()) }
                .isInstanceOf(ConflictException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SESSION_ALREADY_COMPLETED)
        }
    }

    // ─────────────── 세션 취소 ───────────────

    @Nested
    @DisplayName("세션 취소")
    inner class CancelSession {
        @Test
        @DisplayName("IN_PROGRESS 세션 취소 → CANCELLED 전이 + Kafka 미발행")
        fun cancelSession_inProgress_success() {
            val session =
                WorkoutSession(
                    id = "c1",
                    userId = userIdStr,
                    startedAt = Instant.now().minusSeconds(120),
                    status = SessionStatus.IN_PROGRESS,
                )
            whenever(sessionRepository.findByIdAndUserId("c1", userIdStr)).thenReturn(session)
            whenever(sessionRepository.cancelSession(any(), any(), any())).thenReturn(1L)

            sessionService.cancelSession(userId, "c1")

            verify(sessionRepository).cancelSession(eq("c1"), eq(userIdStr), any())
            // Kafka 미발행 (analytics 미반영)
            verify(eventPublisher, never()).publishSessionCompleted(any())
        }

        @Test
        @DisplayName("이미 COMPLETED 세션 취소 시도 → SESSION_ALREADY_TERMINATED (409)")
        fun cancelSession_alreadyCompleted_conflict() {
            val session =
                WorkoutSession(
                    id = "c2",
                    userId = userIdStr,
                    completedAt = Instant.now().minusSeconds(60),
                    status = SessionStatus.COMPLETED,
                )
            whenever(sessionRepository.findByIdAndUserId("c2", userIdStr)).thenReturn(session)

            assertThatThrownBy { sessionService.cancelSession(userId, "c2") }
                .isInstanceOf(ConflictException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SESSION_ALREADY_TERMINATED)

            verify(sessionRepository, never()).cancelSession(any(), any(), any())
            verify(eventPublisher, never()).publishSessionCompleted(any())
        }

        @Test
        @DisplayName("이미 CANCELLED 세션 재취소 → SESSION_ALREADY_TERMINATED (409)")
        fun cancelSession_alreadyCancelled_conflict() {
            val session =
                WorkoutSession(
                    id = "c3",
                    userId = userIdStr,
                    completedAt = Instant.now().minusSeconds(30),
                    status = SessionStatus.CANCELLED,
                )
            whenever(sessionRepository.findByIdAndUserId("c3", userIdStr)).thenReturn(session)

            assertThatThrownBy { sessionService.cancelSession(userId, "c3") }
                .isInstanceOf(ConflictException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SESSION_ALREADY_TERMINATED)

            verify(sessionRepository, never()).cancelSession(any(), any(), any())
        }

        @Test
        @DisplayName("타인 세션 취소 시도 → 401 (소유권 노출 방지)")
        fun cancelSession_otherUser_unauthorized() {
            whenever(sessionRepository.findByIdAndUserId("other", userIdStr)).thenReturn(null)
            whenever(sessionRepository.existsById("other")).thenReturn(true)

            assertThatThrownBy { sessionService.cancelSession(userId, "other") }
                .isInstanceOf(UnauthorizedException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN)

            verify(sessionRepository, never()).cancelSession(any(), any(), any())
        }

        @Test
        @DisplayName("존재하지 않는 sessionId 취소 → SESSION_NOT_FOUND (404)")
        fun cancelSession_notFound() {
            whenever(sessionRepository.findByIdAndUserId("nope", userIdStr)).thenReturn(null)
            whenever(sessionRepository.existsById("nope")).thenReturn(false)

            assertThatThrownBy { sessionService.cancelSession(userId, "nope") }
                .isInstanceOf(NotFoundException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND)
        }

        @Test
        @DisplayName("DB 레벨 동시성 — modifiedCount=0이면 SESSION_ALREADY_TERMINATED")
        fun cancelSession_raceCondition() {
            val session =
                WorkoutSession(
                    id = "c4",
                    userId = userIdStr,
                    startedAt = Instant.now().minusSeconds(60),
                    status = SessionStatus.IN_PROGRESS,
                )
            whenever(sessionRepository.findByIdAndUserId("c4", userIdStr)).thenReturn(session)
            // 다른 스레드가 먼저 종료 → 원자적 업데이트 실패
            whenever(sessionRepository.cancelSession(any(), any(), any())).thenReturn(0L)

            assertThatThrownBy { sessionService.cancelSession(userId, "c4") }
                .isInstanceOf(ConflictException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SESSION_ALREADY_TERMINATED)
        }
    }

    // ─────────────── TC-12: 타인 세션 접근 불가 ───────────────

    @Nested
    @DisplayName("세션 소유권 검증")
    inner class OwnershipCheck {
        @Test
        @DisplayName("TC-12: 타인 세션 sessionId로 완료 시도 → 401 (소유권 노출 방지)")
        fun getSession_otherUser_unauthorized() {
            whenever(sessionRepository.findByIdAndUserId("other", userIdStr)).thenReturn(null)
            whenever(sessionRepository.existsById("other")).thenReturn(true)

            assertThatThrownBy { sessionService.getSession(userId, "other") }
                .isInstanceOf(UnauthorizedException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN)
        }

        @Test
        @DisplayName("존재하지 않는 sessionId → SESSION_NOT_FOUND (404)")
        fun getSession_notFound() {
            whenever(sessionRepository.findByIdAndUserId("nonexistent", userIdStr)).thenReturn(null)
            whenever(sessionRepository.existsById("nonexistent")).thenReturn(false)

            assertThatThrownBy { sessionService.getSession(userId, "nonexistent") }
                .isInstanceOf(NotFoundException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND)
        }
    }
}

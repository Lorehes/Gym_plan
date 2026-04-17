package com.gymplan.workout.application.service

import com.gymplan.common.dto.PageResponse
import com.gymplan.common.exception.ConflictException
import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.NotFoundException
import com.gymplan.common.exception.UnauthorizedException
import com.gymplan.workout.application.dto.CompleteSessionRequest
import com.gymplan.workout.application.dto.CompleteSessionResponse
import com.gymplan.workout.application.dto.SessionDetailResponse
import com.gymplan.workout.application.dto.SessionSummaryResponse
import com.gymplan.workout.application.dto.StartSessionRequest
import com.gymplan.workout.application.dto.StartSessionResponse
import com.gymplan.workout.application.dto.toDetailResponse
import com.gymplan.workout.application.dto.toSummaryResponse
import com.gymplan.workout.application.event.WorkoutSessionCompletedEvent
import com.gymplan.workout.domain.entity.WorkoutSession
import com.gymplan.workout.domain.repository.WorkoutSessionRepository
import com.gymplan.workout.infrastructure.messaging.WorkoutEventPublisher
import org.owasp.html.HtmlPolicyBuilder
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 운동 세션 시작·완료·조회 유스케이스.
 *
 * 명세: docs/specs/workout-service.md §인수 기준 — 세션 시작, 운동 완료
 *
 * Kafka 발행 원칙:
 *   - API 응답 후 비동기 발행 (WorkoutEventPublisher @Async)
 *   - Kafka 장애가 API 응답에 영향을 주지 않음
 */
@Service
class SessionService(
    private val sessionRepository: WorkoutSessionRepository,
    private val eventPublisher: WorkoutEventPublisher,
) {
    private val log = LoggerFactory.getLogger(SessionService::class.java)

    // notes 필드: 모든 HTML 태그 제거 (XSS 방어, 명세 §보안)
    private val notesPolicy = HtmlPolicyBuilder().toFactory()

    // ─────────────────── 세션 시작 ───────────────────

    fun startSession(
        userId: Long,
        request: StartSessionRequest,
    ): StartSessionResponse {
        val userIdStr = userId.toString()

        // 동시 활성 세션 1개만 허용
        if (sessionRepository.findByUserIdAndCompletedAtIsNull(userIdStr) != null) {
            throw ConflictException(ErrorCode.SESSION_ALREADY_ACTIVE)
        }

        val session =
            WorkoutSession(
                userId = userIdStr,
                planId = request.planId?.toString(),
                planName = request.planName,
            )
        val saved = sessionRepository.save(session)
        log.info("세션 시작: userId={}, sessionId={}", userId, saved.id)

        return StartSessionResponse(
            sessionId = saved.id!!,
            startedAt = saved.startedAt,
        )
    }

    // ─────────────────── 진행 중 세션 조회 ───────────────────

    fun getActiveSession(userId: Long): SessionDetailResponse? {
        val session = sessionRepository.findByUserIdAndCompletedAtIsNull(userId.toString())
        return session?.toDetailResponse()
    }

    // ─────────────────── 운동 완료 ───────────────────

    fun completeSession(
        userId: Long,
        sessionId: String,
        request: CompleteSessionRequest,
    ): CompleteSessionResponse {
        val userIdStr = userId.toString()
        val session = findSessionForUser(sessionId, userIdStr)

        if (session.completedAt != null) {
            throw ConflictException(ErrorCode.SESSION_ALREADY_COMPLETED)
        }

        val now = Instant.now()
        val durationSec = ChronoUnit.SECONDS.between(session.startedAt, now)
        val totalSets = session.exercises.sumOf { it.sets.size }
        val totalVolume =
            session.exercises.sumOf { ex ->
                ex.sets.sumOf { s -> s.weightKg * s.reps }
            }

        // notes: HTML 태그 전부 제거 (저장 전 서버 측 XSS 방어)
        val sanitizedNotes = request.notes?.let { notesPolicy.sanitize(it) }

        // completedAt = null 조건 포함 → DB 레벨 중복 완료 방지 (동시성 안전)
        val modified =
            sessionRepository.completeSession(
                sessionId = sessionId,
                userId = userIdStr,
                completedAt = now,
                durationSec = durationSec,
                totalVolume = totalVolume,
                totalSets = totalSets,
                notes = sanitizedNotes,
            )
        if (modified == 0L) {
            throw ConflictException(ErrorCode.SESSION_ALREADY_COMPLETED)
        }

        log.info(
            "세션 완료: userId={}, sessionId={}, durationSec={}, totalVolume={}, totalSets={}",
            userId,
            sessionId,
            durationSec,
            totalVolume,
            totalSets,
        )

        // API 응답 후 비동기 Kafka 발행
        val muscleGroups = session.exercises.map { it.muscleGroup }.distinct()
        eventPublisher.publishSessionCompleted(
            WorkoutSessionCompletedEvent(
                sessionId = sessionId,
                userId = userIdStr,
                planId = session.planId,
                planName = session.planName,
                startedAt = session.startedAt,
                completedAt = now,
                durationSec = durationSec,
                totalVolume = totalVolume,
                totalSets = totalSets,
                muscleGroups = muscleGroups,
            ),
        )

        return CompleteSessionResponse(
            sessionId = sessionId,
            durationSec = durationSec,
            totalVolume = totalVolume,
            totalSets = totalSets,
        )
    }

    // ─────────────────── 히스토리 조회 ───────────────────

    fun getHistory(
        userId: Long,
        pageable: Pageable,
    ): PageResponse<SessionSummaryResponse> {
        val page = sessionRepository.findByUserId(userId.toString(), pageable)
        return PageResponse.from(page) { it.toSummaryResponse() }
    }

    // ─────────────────── 세션 상세 조회 ───────────────────

    fun getSession(
        userId: Long,
        sessionId: String,
    ): SessionDetailResponse {
        return findSessionForUser(sessionId, userId.toString()).toDetailResponse()
    }

    // ─────────────────── 내부 헬퍼 ───────────────────

    fun findSessionForUser(
        sessionId: String,
        userId: String,
    ): WorkoutSession {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
            ?: run {
                // 세션 존재 여부와 소유권을 구분하지 않음 (보안: 타인 세션 존재 여부 노출 금지)
                if (sessionRepository.existsById(sessionId)) {
                    throw UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN)
                }
                throw NotFoundException(ErrorCode.SESSION_NOT_FOUND)
            }
    }
}

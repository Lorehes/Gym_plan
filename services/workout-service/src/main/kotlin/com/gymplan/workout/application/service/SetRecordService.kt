package com.gymplan.workout.application.service

import com.gymplan.common.exception.ConflictException
import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.NotFoundException
import com.gymplan.workout.application.dto.LogSetRequest
import com.gymplan.workout.application.dto.SetRecordResponse
import com.gymplan.workout.application.dto.UpdateSetRequest
import com.gymplan.workout.application.event.WorkoutSetLoggedEvent
import com.gymplan.workout.domain.entity.SetRecord
import com.gymplan.workout.domain.repository.WorkoutSessionRepository
import com.gymplan.workout.infrastructure.messaging.WorkoutEventPublisher
import com.gymplan.workout.infrastructure.metrics.WorkoutMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 세트 기록 추가·수정·삭제 유스케이스.
 *
 * 명세: docs/specs/workout-service.md §인수 기준 — 세트 기록
 *
 * MongoDB 업데이트 원칙:
 *   - $push 로 배열에 append (전체 문서 교체 금지)
 *   - exerciseId 존재 여부에 따라 두 경로로 분기
 *
 * Kafka 발행: API 응답 후 비동기 (@Async)
 */
@Service
class SetRecordService(
    private val sessionRepository: WorkoutSessionRepository,
    private val sessionService: SessionService,
    private val eventPublisher: WorkoutEventPublisher,
    private val workoutMetrics: WorkoutMetrics,
) {
    private val log = LoggerFactory.getLogger(SetRecordService::class.java)

    // ─────────────────── 세트 추가 ───────────────────

    fun logSet(
        userId: Long,
        sessionId: String,
        request: LogSetRequest,
    ): SetRecordResponse {
        val userIdStr = userId.toString()
        val session = sessionService.findSessionForUser(sessionId, userIdStr)

        if (session.completedAt != null) {
            throw ConflictException(ErrorCode.SESSION_ALREADY_COMPLETED)
        }

        val set =
            SetRecord(
                setNo = request.setNo,
                reps = request.reps,
                weightKg = request.weightKg,
                isSuccess = request.isSuccess,
                completedAt = Instant.now(),
            )

        // $push: exerciseId 존재 여부에 따라 두 경로 분기
        val modified =
            sessionRepository.pushSet(
                sessionId = sessionId,
                userId = userIdStr,
                exerciseId = request.exerciseId,
                exerciseName = request.exerciseName,
                muscleGroup = request.muscleGroup,
                set = set,
            )

        if (modified == 0L) {
            throw ConflictException(ErrorCode.SESSION_ALREADY_COMPLETED)
        }

        workoutMetrics.setsLogged.increment()
        log.info(
            "세트 기록: userId={}, sessionId={}, exerciseId={}, setNo={}",
            userId,
            sessionId,
            request.exerciseId,
            request.setNo,
        )

        // API 응답 후 비동기 Kafka 발행
        eventPublisher.publishSetLogged(
            WorkoutSetLoggedEvent(
                sessionId = sessionId,
                userId = userIdStr,
                exerciseId = request.exerciseId,
                exerciseName = request.exerciseName,
                muscleGroup = request.muscleGroup,
                setNo = request.setNo,
                reps = request.reps,
                weightKg = request.weightKg,
                volume = request.weightKg * request.reps,
                isSuccess = request.isSuccess,
            ),
        )

        return SetRecordResponse(
            sessionId = sessionId,
            exerciseId = request.exerciseId,
            setNo = request.setNo,
        )
    }

    // ─────────────────── 세트 수정 ───────────────────

    fun updateSet(
        userId: Long,
        sessionId: String,
        exerciseId: String,
        setNo: Int,
        request: UpdateSetRequest,
    ) {
        val userIdStr = userId.toString()
        val session = sessionService.findSessionForUser(sessionId, userIdStr)

        if (session.completedAt != null) {
            throw ConflictException(ErrorCode.SESSION_ALREADY_COMPLETED)
        }

        val existing =
            session.exercises
                .find { it.exerciseId == exerciseId }
                ?.sets
                ?.find { it.setNo == setNo }
                ?: throw NotFoundException(ErrorCode.SESSION_NOT_FOUND)

        val updated =
            SetRecord(
                setNo = setNo,
                reps = request.reps ?: existing.reps,
                weightKg = request.weightKg ?: existing.weightKg,
                isSuccess = request.isSuccess ?: existing.isSuccess,
                completedAt = existing.completedAt,
            )

        val modified =
            sessionRepository.updateSet(
                sessionId = sessionId,
                userId = userIdStr,
                exerciseId = exerciseId,
                setNo = setNo,
                newSet = updated,
            )

        if (modified == 0L) {
            throw NotFoundException(ErrorCode.SESSION_NOT_FOUND)
        }

        log.info("세트 수정: userId={}, sessionId={}, exerciseId={}, setNo={}", userId, sessionId, exerciseId, setNo)
    }

    // ─────────────────── 세트 삭제 ───────────────────

    fun deleteSet(
        userId: Long,
        sessionId: String,
        exerciseId: String,
        setNo: Int,
    ) {
        val userIdStr = userId.toString()
        val session = sessionService.findSessionForUser(sessionId, userIdStr)

        if (session.completedAt != null) {
            throw ConflictException(ErrorCode.SESSION_ALREADY_COMPLETED)
        }

        val modified =
            sessionRepository.pullSet(
                sessionId = sessionId,
                userId = userIdStr,
                exerciseId = exerciseId,
                setNo = setNo,
            )

        if (modified == 0L) {
            throw NotFoundException(ErrorCode.SESSION_NOT_FOUND)
        }

        log.info("세트 삭제: userId={}, sessionId={}, exerciseId={}, setNo={}", userId, sessionId, exerciseId, setNo)
    }
}

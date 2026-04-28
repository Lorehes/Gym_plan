package com.gymplan.workout.presentation.controller

import com.gymplan.common.dto.ApiResponse
import com.gymplan.common.dto.PageResponse
import com.gymplan.common.security.CurrentUserId
import com.gymplan.workout.application.dto.CompleteSessionRequest
import com.gymplan.workout.application.dto.CompleteSessionResponse
import com.gymplan.workout.application.dto.LogSetRequest
import com.gymplan.workout.application.dto.SessionDetailResponse
import com.gymplan.workout.application.dto.SessionSummaryResponse
import com.gymplan.workout.application.dto.SetRecordResponse
import com.gymplan.workout.application.dto.StartSessionRequest
import com.gymplan.workout.application.dto.StartSessionResponse
import com.gymplan.workout.application.dto.UpdateSetRequest
import com.gymplan.workout.application.service.SessionService
import com.gymplan.workout.application.service.SetRecordService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 운동 세션 API 엔드포인트.
 *
 * 참조: docs/api/workout-service.md, docs/specs/workout-service.md
 *
 * 인증: Gateway가 주입한 X-User-Id 헤더를 @CurrentUserId로 주입.
 *       workout-service는 JWT를 직접 검증하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/sessions")
class WorkoutSessionController(
    private val sessionService: SessionService,
    private val setRecordService: SetRecordService,
) {
    // ─────────────────── 세션 ───────────────────

    /**
     * 운동 세션 시작.
     * planId 없이 요청하면 자유 운동 세션.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun startSession(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: StartSessionRequest,
    ): ApiResponse<StartSessionResponse> = ApiResponse.success(sessionService.startSession(userId, request))

    /** 진행 중인 세션 조회. 없으면 data: null 반환 (404 아님). */
    @GetMapping("/active")
    fun getActiveSession(
        @CurrentUserId userId: Long,
    ): ApiResponse<SessionDetailResponse?> = ApiResponse.success(sessionService.getActiveSession(userId))

    /** 운동 완료 처리. */
    @PostMapping("/{sessionId}/complete")
    fun completeSession(
        @CurrentUserId userId: Long,
        @PathVariable sessionId: String,
        @Valid @RequestBody request: CompleteSessionRequest,
    ): ApiResponse<CompleteSessionResponse> = ApiResponse.success(sessionService.completeSession(userId, sessionId, request))

    /**
     * 운동 세션 취소 (IN_PROGRESS → CANCELLED).
     * - 본인 세션만 (X-User-Id 검증)
     * - 이미 종료된 세션이면 409 SESSION_ALREADY_TERMINATED
     * - 응답: 204 No Content (Kafka 이벤트 발행 안 함)
     */
    @PostMapping("/{sessionId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelSession(
        @CurrentUserId userId: Long,
        @PathVariable sessionId: String,
    ) {
        sessionService.cancelSession(userId, sessionId)
    }

    /** 운동 히스토리 (페이징). */
    @GetMapping("/history")
    fun getHistory(
        @CurrentUserId userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "startedAt,desc") sort: String,
    ): ApiResponse<PageResponse<SessionSummaryResponse>> {
        val (field, direction) = parseSortParam(sort)
        val pageable = PageRequest.of(page, size, Sort.by(direction, field))
        return ApiResponse.success(sessionService.getHistory(userId, pageable))
    }

    /** 세션 상세 조회. */
    @GetMapping("/{sessionId}")
    fun getSession(
        @CurrentUserId userId: Long,
        @PathVariable sessionId: String,
    ): ApiResponse<SessionDetailResponse> = ApiResponse.success(sessionService.getSession(userId, sessionId))

    // ─────────────────── 세트 기록 ───────────────────

    /** 세트 기록 추가. */
    @PostMapping("/{sessionId}/sets")
    @ResponseStatus(HttpStatus.CREATED)
    fun logSet(
        @CurrentUserId userId: Long,
        @PathVariable sessionId: String,
        @Valid @RequestBody request: LogSetRequest,
    ): ApiResponse<SetRecordResponse> = ApiResponse.success(setRecordService.logSet(userId, sessionId, request))

    /** 세트 기록 수정. */
    @PutMapping("/{sessionId}/sets/{setNo}/{exerciseId}")
    fun updateSet(
        @CurrentUserId userId: Long,
        @PathVariable sessionId: String,
        @PathVariable setNo: Int,
        @PathVariable exerciseId: String,
        @Valid @RequestBody request: UpdateSetRequest,
    ): ApiResponse<Unit> {
        setRecordService.updateSet(userId, sessionId, exerciseId, setNo, request)
        return ApiResponse.success()
    }

    /** 세트 기록 삭제. */
    @DeleteMapping("/{sessionId}/sets/{setNo}/{exerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSet(
        @CurrentUserId userId: Long,
        @PathVariable sessionId: String,
        @PathVariable setNo: Int,
        @PathVariable exerciseId: String,
    ) = setRecordService.deleteSet(userId, sessionId, exerciseId, setNo)

    // ─────────────────── 내부 헬퍼 ───────────────────

    private fun parseSortParam(sort: String): Pair<String, Sort.Direction> {
        val parts = sort.split(",")
        val field = parts.getOrElse(0) { "startedAt" }
        val dir =
            if (parts.getOrElse(1) { "desc" }.lowercase() == "asc") {
                Sort.Direction.ASC
            } else {
                Sort.Direction.DESC
            }
        return field to dir
    }
}

package com.gymplan.notification.presentation.controller

import com.gymplan.common.dto.ApiResponse
import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.ForbiddenException
import com.gymplan.common.security.CurrentUserId
import com.gymplan.notification.application.dto.NotificationSettingsResponse
import com.gymplan.notification.application.dto.UpdateNotificationSettingsRequest
import com.gymplan.notification.application.service.NotificationSettingsService
import com.gymplan.notification.application.service.TimerSseService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * 알림 API 엔드포인트.
 *
 * 참조: docs/api/notification-service.md, docs/specs/notification-service.md
 *
 * 인증: Gateway가 주입한 X-User-Id 헤더를 @CurrentUserId로 주입.
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val timerSseService: TimerSseService,
    private val settingsService: NotificationSettingsService,
) {
    /**
     * 휴식 타이머 SSE 스트림 구독.
     *
     * sessionId 소유권 검증: workout-service 호출 없이 userId 기반 구독.
     * 타인의 sessionId를 알고 있어도 해당 채널의 PUBLISH는 workout-service가 제어.
     */
    @GetMapping("/timer/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribeTimer(
        @CurrentUserId userId: Long,
        @RequestParam sessionId: String,
    ): SseEmitter {
        if (sessionId.isBlank()) {
            throw ForbiddenException(ErrorCode.NOTIFICATION_ACCESS_DENIED)
        }
        return timerSseService.subscribe(sessionId, userId)
    }

    @GetMapping("/settings")
    fun getSettings(
        @CurrentUserId userId: Long,
    ): ApiResponse<NotificationSettingsResponse> = ApiResponse.success(settingsService.getSettings(userId))

    @PutMapping("/settings")
    fun updateSettings(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: UpdateNotificationSettingsRequest,
    ): ApiResponse<NotificationSettingsResponse> = ApiResponse.success(settingsService.updateSettings(userId, request))
}

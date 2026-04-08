package com.gymplan.common.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * 모든 API 응답의 표준 envelope.
 *
 * docs/api/common.md 의 응답 규약과 1:1 매칭:
 * { success, data, error, timestamp }
 *
 * - 성공: success=true, data=..., error=null
 * - 실패: success=false, data=null, error=...
 *
 * 직렬화 시 null 필드는 제외 (Jackson @JsonInclude).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorBody? = null,
    val timestamp: Instant = Instant.now(),
) {
    companion object {
        /** 성공 응답 (데이터 포함) */
        fun <T> success(data: T): ApiResponse<T> =
            ApiResponse(success = true, data = data)

        /** 성공 응답 (데이터 없음 — 삭제, 204 등) */
        fun success(): ApiResponse<Unit> =
            ApiResponse(success = true, data = Unit)

        /** 실패 응답 */
        fun <T> failure(
            code: String,
            message: String,
            details: Map<String, Any?> = emptyMap(),
        ): ApiResponse<T> =
            ApiResponse(
                success = false,
                data = null,
                error = ErrorBody(code = code, message = message, details = details),
            )
    }
}

/**
 * 실패 응답의 error 필드 본문.
 *
 * - code:    docs/api/common.md 의 에러 코드 (예: AUTH_INVALID_TOKEN)
 * - message: 사용자 노출 가능한 한국어 메시지
 * - details: 검증 실패 필드 등 부가 정보 (선택)
 */
data class ErrorBody(
    val code: String,
    val message: String,
    val details: Map<String, Any?> = emptyMap(),
)

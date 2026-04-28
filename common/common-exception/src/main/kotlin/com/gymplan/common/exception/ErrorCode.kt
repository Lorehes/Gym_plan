package com.gymplan.common.exception

import org.springframework.http.HttpStatus

/**
 * 전역 에러 코드 enum.
 *
 * docs/api/common.md 의 에러 코드 표와 1:1 매칭.
 * 새 에러 코드 추가 시 반드시 docs/api/common.md 도 함께 업데이트.
 *
 * - status: 응답 HTTP 상태 코드
 * - code:   API 응답 본문의 error.code 값 (대문자 SNAKE_CASE)
 * - defaultMessage: 기본 메시지 (런타임에 override 가능)
 */
enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val defaultMessage: String,
) {
    // ───── 인증 / 사용자 (user-service) ─────
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED_TOKEN", "만료된 토큰입니다."),
    AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN", "유효하지 않거나 만료된 토큰입니다."),
    AUTH_REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_REUSED", "토큰 재사용이 감지되어 모든 세션이 종료되었습니다."),
    AUTH_DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_ACCOUNT_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_ACCOUNT_LOCKED", "너무 많은 로그인 실패. 5분 후 다시 시도해주세요."),
    AUTH_ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_DISABLED", "비활성화된 계정입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),

    // ───── 루틴 (plan-service) ─────
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "루틴을 찾을 수 없습니다."),
    PLAN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PLAN_ACCESS_DENIED", "해당 루틴에 접근할 권한이 없습니다."),

    // ───── 운동 종목 (exercise-catalog) ─────
    EXERCISE_NOT_FOUND(HttpStatus.NOT_FOUND, "EXERCISE_NOT_FOUND", "운동 종목을 찾을 수 없습니다."),

    // ───── 운동 세션 (workout-service) ─────
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "운동 세션을 찾을 수 없습니다."),
    SESSION_ALREADY_ACTIVE(HttpStatus.CONFLICT, "SESSION_ALREADY_ACTIVE", "이미 진행 중인 세션이 있습니다."),
    SESSION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "SESSION_ALREADY_COMPLETED", "이미 완료된 세션입니다."),
    SESSION_ALREADY_TERMINATED(HttpStatus.CONFLICT, "SESSION_ALREADY_TERMINATED", "이미 종료된 세션입니다."),

    // ───── 알림 (notification-service) ─────
    NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "NOTIFICATION_ACCESS_DENIED", "해당 세션에 대한 접근 권한이 없습니다."),

    // ───── Gateway / 공통 ─────
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),
}

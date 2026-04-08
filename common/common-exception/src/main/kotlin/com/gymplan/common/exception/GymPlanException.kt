package com.gymplan.common.exception

/**
 * GymPlan 도메인 예외의 최상위 부모.
 *
 * 모든 비즈니스 예외는 이 클래스를 상속해서
 * GlobalExceptionHandler 가 단일 경로로 처리할 수 있도록 한다.
 *
 * - errorCode: 응답 코드/상태/기본 메시지를 결정
 * - message:   override 메시지 (없으면 errorCode.defaultMessage)
 * - details:   error.details 에 노출할 부가 정보
 */
open class GymPlanException(
    val errorCode: ErrorCode,
    message: String? = null,
    val details: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(message ?: errorCode.defaultMessage, cause)

// ───── 자주 쓰는 specialization (가독성 + 호출부 단순화) ─────

class NotFoundException(
    errorCode: ErrorCode,
    message: String? = null,
    details: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null,
) : GymPlanException(errorCode, message, details, cause)

class UnauthorizedException(
    errorCode: ErrorCode = ErrorCode.AUTH_INVALID_TOKEN,
    message: String? = null,
    details: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null,
) : GymPlanException(errorCode, message, details, cause)

class ForbiddenException(
    errorCode: ErrorCode,
    message: String? = null,
    details: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null,
) : GymPlanException(errorCode, message, details, cause)

class ConflictException(
    errorCode: ErrorCode,
    message: String? = null,
    details: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null,
) : GymPlanException(errorCode, message, details, cause)

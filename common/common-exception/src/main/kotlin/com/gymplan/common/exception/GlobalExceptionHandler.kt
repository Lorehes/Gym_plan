package com.gymplan.common.exception

import com.gymplan.common.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전역 예외 핸들러.
 *
 * 모든 서비스가 이 클래스를 컴포넌트 스캔으로 가져가도록
 * common-exception 패키지를 @ComponentScan basePackages 에 포함시킬 것.
 *
 * 처리 우선순위:
 *   1. GymPlanException (도메인 예외) → ErrorCode 기반 응답
 *   2. MethodArgumentNotValidException (Spring Validation 실패) → 400 + 필드 정보
 *   3. 그 외 Throwable → 500 INTERNAL_ERROR
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(GymPlanException::class)
    fun handleGymPlanException(ex: GymPlanException): ResponseEntity<ApiResponse<Nothing>> {
        // 4xx 는 info, 5xx 는 error
        val ec = ex.errorCode
        if (ec.status.is5xxServerError) {
            log.error("[{}] {}", ec.code, ex.message, ex)
        } else {
            log.info("[{}] {}", ec.code, ex.message)
        }
        return ResponseEntity
            .status(ec.status)
            .body(
                ApiResponse.failure(
                    code = ec.code,
                    message = ex.message ?: ec.defaultMessage,
                    details = ex.details,
                ),
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val fieldErrors: Map<String, Any?> =
            ex.bindingResult.fieldErrors.associate {
                it.field to (it.defaultMessage ?: "invalid")
            }
        log.info("[VALIDATION_FAILED] {}", fieldErrors)

        val ec = ErrorCode.VALIDATION_FAILED
        return ResponseEntity
            .status(ec.status)
            .body(
                ApiResponse.failure(
                    code = ec.code,
                    message = ec.defaultMessage,
                    details = fieldErrors,
                ),
            )
    }

    @ExceptionHandler(Throwable::class)
    fun handleUnknown(ex: Throwable): ResponseEntity<ApiResponse<Nothing>> {
        log.error("[INTERNAL_ERROR] unhandled exception", ex)
        val ec = ErrorCode.INTERNAL_ERROR
        return ResponseEntity
            .status(ec.status)
            .body(
                ApiResponse.failure(
                    code = ec.code,
                    message = ec.defaultMessage,
                ),
            )
    }
}

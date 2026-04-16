package com.gymplan.common.exception

import com.fasterxml.jackson.databind.JsonMappingException
import com.gymplan.common.dto.ApiResponse
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * 전역 예외 핸들러.
 *
 * 모든 서비스가 이 클래스를 컴포넌트 스캔으로 가져가도록
 * common-exception 패키지를 @ComponentScan basePackages 에 포함시킬 것.
 *
 * 처리 우선순위:
 *   1. GymPlanException (도메인 예외) → ErrorCode 기반 응답
 *   2. MethodArgumentNotValidException (@Valid RequestBody 검증 실패) → 400 + 필드 정보
 *   3. MethodArgumentTypeMismatchException (enum 파라미터 변환 실패) → 400
 *   4. ConstraintViolationException (@Validated + @Max/@Min 쿼리 파라미터) → 400
 *   5. 그 외 Throwable → 500 INTERNAL_ERROR
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

    /**
     * JSON 역직렬화 실패 (missing non-null field, 잘못된 타입 등).
     * MissingKotlinParameterException 은 JsonMappingException 의 서브클래스이므로
     * path 에서 필드명을 추출해 details 에 포함한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing>> {
        val ec = ErrorCode.VALIDATION_FAILED
        val cause = ex.cause
        if (cause is JsonMappingException) {
            val fieldName = cause.path.lastOrNull()?.fieldName
            if (fieldName != null) {
                val details = mapOf(fieldName to "필수 항목입니다")
                log.info("[VALIDATION_FAILED] 필수 파라미터 누락: {}", fieldName)
                return ResponseEntity
                    .status(ec.status)
                    .body(ApiResponse.failure(code = ec.code, message = ec.defaultMessage, details = details))
            }
        }
        log.info("[VALIDATION_FAILED] 요청 본문 파싱 실패: {}", ex.message)
        return ResponseEntity
            .status(ec.status)
            .body(ApiResponse.failure(code = ec.code, message = "요청 본문 형식이 올바르지 않습니다"))
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

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Nothing>> {
        val message = "유효하지 않은 ${ex.name} 값입니다: ${ex.value}"
        log.info("[VALIDATION_FAILED] {}", message)
        val ec = ErrorCode.VALIDATION_FAILED
        return ResponseEntity
            .status(ec.status)
            .body(
                ApiResponse.failure(
                    code = ec.code,
                    message = message,
                ),
            )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ApiResponse<Nothing>> {
        val details: Map<String, Any?> =
            ex.constraintViolations.associate { violation ->
                violation.propertyPath.toString() to violation.message
            }
        log.info("[VALIDATION_FAILED] {}", details)
        val ec = ErrorCode.VALIDATION_FAILED
        return ResponseEntity
            .status(ec.status)
            .body(
                ApiResponse.failure(
                    code = ec.code,
                    message = ec.defaultMessage,
                    details = details,
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

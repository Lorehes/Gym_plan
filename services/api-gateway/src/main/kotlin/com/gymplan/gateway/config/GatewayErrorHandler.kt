package com.gymplan.gateway.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.gymplan.common.dto.ApiResponse
import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.GymPlanException
import org.slf4j.LoggerFactory
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Gateway 전역 에러 핸들러.
 *
 * 모든 에러 응답을 docs/api/common.md 의 표준 ApiResponse 봉투로 변환한다:
 *
 *   {
 *     "success": false,
 *     "data": null,
 *     "error": { "code": "...", "message": "...", "details": {} },
 *     "timestamp": "..."
 *   }
 *
 * 처리 우선순위:
 *   1. GymPlanException (도메인 예외) → ErrorCode 기반 응답
 *   2. ResponseStatusException (Spring/Gateway 내부) → 상태코드 기반 매핑
 *   3. 그 외 Throwable → 500 INTERNAL_ERROR
 *
 * common-exception 의 GlobalExceptionHandler 는 @RestControllerAdvice (WebMvc) 라
 * Reactive 환경에서 동작하지 않으므로 Gateway 는 별도 핸들러가 필요하다.
 *
 * Order=-2: DefaultErrorWebExceptionHandler(@-1) 보다 먼저 동작.
 */
@Component
@Order(-2)
class GatewayErrorHandler(
    private val objectMapper: ObjectMapper,
) : ErrorWebExceptionHandler {
    private val log = LoggerFactory.getLogger(GatewayErrorHandler::class.java)

    override fun handle(
        exchange: ServerWebExchange,
        ex: Throwable,
    ): Mono<Void> {
        val (status, body) = mapError(ex)

        val response = exchange.response
        if (response.isCommitted) {
            return Mono.error(ex)
        }
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_JSON

        val bytes: ByteArray =
            try {
                objectMapper.writeValueAsBytes(body)
            } catch (e: Exception) {
                log.error("응답 직렬화 실패", e)
                "{\"success\":false,\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"serialization failed\"}}".toByteArray()
            }
        val buffer: DataBuffer = response.bufferFactory().wrap(bytes)
        return response.writeWith(Mono.just(buffer))
    }

    private fun mapError(ex: Throwable): Pair<HttpStatus, ApiResponse<Nothing>> {
        return when (ex) {
            is GymPlanException -> {
                val ec = ex.errorCode
                if (ec.status.is5xxServerError) {
                    log.error("[{}] {}", ec.code, ex.message, ex)
                } else {
                    log.info("[{}] {}", ec.code, ex.message)
                }
                ec.status to
                    ApiResponse.failure(
                        code = ec.code,
                        message = ex.message ?: ec.defaultMessage,
                        details = ex.details,
                    )
            }
            is ResponseStatusException -> {
                val status = HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
                val ec = matchErrorCode(status)
                log.info("[{}] {}", ec.code, ex.reason ?: ec.defaultMessage)
                status to
                    ApiResponse.failure(
                        code = ec.code,
                        message = ex.reason ?: ec.defaultMessage,
                    )
            }
            else -> {
                log.error("[INTERNAL_ERROR] unhandled exception", ex)
                val ec = ErrorCode.INTERNAL_ERROR
                ec.status to ApiResponse.failure(code = ec.code, message = ec.defaultMessage)
            }
        }
    }

    /** 표준 ErrorCode 가 없는 raw 상태코드는 가까운 ErrorCode 로 매핑한다. */
    private fun matchErrorCode(status: HttpStatus): ErrorCode {
        return when (status) {
            HttpStatus.UNAUTHORIZED -> ErrorCode.AUTH_INVALID_TOKEN
            HttpStatus.TOO_MANY_REQUESTS -> ErrorCode.RATE_LIMIT_EXCEEDED
            HttpStatus.BAD_REQUEST -> ErrorCode.VALIDATION_FAILED
            else -> ErrorCode.INTERNAL_ERROR
        }
    }

    /** ErrorWebExceptionHandler 우선순위. */
    @Suppress("unused")
    fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE
}

package com.gymplan.exercise.presentation.security

import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.UnauthorizedException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * Gateway 가 주입한 X-User-Id 헤더를 Long 으로 파싱해 컨트롤러 파라미터로 전달.
 *
 * user-service 와 동일한 패턴.
 */
@Component
class CurrentUserIdArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        if (!parameter.hasParameterAnnotation(CurrentUserId::class.java)) return false
        val type = parameter.parameterType
        return type == java.lang.Long::class.java || type == java.lang.Long.TYPE
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val request =
            webRequest.getNativeRequest(HttpServletRequest::class.java)
                ?: throw UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN)

        val header =
            request.getHeader(HEADER_USER_ID)
                ?: throw UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN)

        val userId =
            header.toLongOrNull()
                ?: throw UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN)

        if (userId <= 0) {
            throw UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN)
        }
        return userId
    }

    companion object {
        const val HEADER_USER_ID = "X-User-Id"
    }
}

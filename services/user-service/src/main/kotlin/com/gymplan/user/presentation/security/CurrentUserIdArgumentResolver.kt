package com.gymplan.user.presentation.security

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
 * Gateway 가 주입한 X-User-Id 헤더를 Long 으로 파싱해 컨트롤러 파라미터로 전달한다.
 *
 * 보안 (docs/context/security-guide.md):
 *  - 하위 서비스는 Gateway 의 X-User-Id 헤더만 신뢰한다 (JWT 직접 검증 금지).
 *  - 외부에서 직접 X-User-Id 를 주입하는 시도는 Gateway 에서 차단된다.
 *  - 본 서비스는 Gateway 뒤에 위치한다고 가정하므로, 헤더가 있으면 신뢰해도 안전하다.
 *  - 헤더가 없거나 비정상 값이면 401.
 */
@Component
class CurrentUserIdArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        if (!parameter.hasParameterAnnotation(CurrentUserId::class.java)) return false
        val type = parameter.parameterType
        // Kotlin `Long` → 기본형 long, 박싱 타입 둘 다 허용
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

        return header.toLongOrNull()
            ?: throw UnauthorizedException(ErrorCode.AUTH_INVALID_TOKEN)
    }

    companion object {
        const val HEADER_USER_ID = "X-User-Id"
    }
}

package com.gymplan.user.presentation.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.gymplan.common.exception.GlobalExceptionHandler
import com.gymplan.user.application.service.AuthService
import com.gymplan.common.security.CurrentUserIdArgumentResolver
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * AuthController 검증 slice 테스트.
 *
 * Spring Security / @WebMvcTest 를 우회하고 MockMvc standalone 으로 돌려
 * validation 어노테이션이 실제로 400 을 반환하는지 확인한다 (TC-003).
 */
class AuthControllerValidationTest {
    private val authService: AuthService = mock()
    private val objectMapper = ObjectMapper()
    private val mockMvc: MockMvc =
        MockMvcBuilders.standaloneSetup(AuthController(authService))
            .setCustomArgumentResolvers(CurrentUserIdArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    // ─────────────── TC-003: 비밀번호 길이 검증 ───────────────

    @Test
    @DisplayName("TC-003: 비밀번호 3자는 400 VALIDATION_FAILED")
    fun register_tooShortPassword() {
        val body =
            """
            {"email":"test@example.com","password":"abc","nickname":"철수"}
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.details.password").exists())
    }

    @Test
    @DisplayName("이메일 형식 불량도 VALIDATION_FAILED")
    fun register_invalidEmail() {
        val body =
            """
            {"email":"not-an-email","password":"P@ssw0rd123!","nickname":"철수"}
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    // ─────────────── TC-013: 로그아웃에 X-User-Id 없음 ───────────────

    @Test
    @DisplayName("TC-013: X-User-Id 헤더 없이 /logout 호출 시 401")
    fun logout_withoutUserId() {
        mockMvc
            .perform(post("/api/v1/auth/logout"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"))
    }
}

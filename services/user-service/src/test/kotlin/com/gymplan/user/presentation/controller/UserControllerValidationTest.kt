package com.gymplan.user.presentation.controller

import com.gymplan.common.exception.GlobalExceptionHandler
import com.gymplan.user.application.dto.UserProfileResponse
import com.gymplan.user.application.service.UserService
import com.gymplan.user.presentation.security.CurrentUserIdArgumentResolver
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class UserControllerValidationTest {
    private val userService: UserService = mock()
    private val mockMvc: MockMvc =
        MockMvcBuilders.standaloneSetup(UserController(userService))
            .setCustomArgumentResolvers(CurrentUserIdArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    // ─────────────── TC-014: 프로필 조회 성공 ───────────────

    @Test
    @DisplayName("TC-014: X-User-Id=1 헤더로 내 프로필 조회")
    fun getMe_success() {
        whenever(userService.getMyProfile(1L)).thenReturn(
            UserProfileResponse(
                userId = 1L,
                email = "test@example.com",
                nickname = "철수",
                profileImg = null,
                createdAt = Instant.parse("2026-04-09T00:00:00Z"),
            ),
        )

        mockMvc
            .perform(get("/api/v1/users/me").header("X-User-Id", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").value(1))
            .andExpect(jsonPath("$.data.email").value("test@example.com"))
            .andExpect(jsonPath("$.data.password").doesNotExist())
    }

    // ─────────────── TC-015: X-User-Id 헤더 없음 ───────────────

    @Test
    @DisplayName("TC-015: X-User-Id 헤더 없으면 401")
    fun getMe_missingHeader() {
        mockMvc
            .perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"))
    }

    @Test
    @DisplayName("TC-015b: X-User-Id 가 0 이하이면 401 (Defense-in-Depth)")
    fun getMe_nonPositiveUserId() {
        mockMvc
            .perform(get("/api/v1/users/me").header("X-User-Id", "0"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"))

        mockMvc
            .perform(get("/api/v1/users/me").header("X-User-Id", "-1"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"))
    }

    @Test
    @DisplayName("TC-015c: X-User-Id 가 숫자가 아니면 401")
    fun getMe_nonNumericUserId() {
        mockMvc
            .perform(get("/api/v1/users/me").header("X-User-Id", "abc"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"))
    }

    // ─────────────── TC-017: 닉네임 1자 검증 실패 ───────────────

    @Test
    @DisplayName("TC-017: 닉네임 1자는 VALIDATION_FAILED")
    fun updateMe_nicknameTooShort() {
        val body = """{"nickname":"A"}"""
        mockMvc
            .perform(
                put("/api/v1/users/me")
                    .header("X-User-Id", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.details.nickname").exists())
    }

    // ─────────────── TC-018: http:// URL 거부 ───────────────

    @Test
    @DisplayName("TC-018: http:// 프로필 이미지는 VALIDATION_FAILED (SSRF 방지)")
    fun updateMe_httpProfileImgRejected() {
        val body = """{"profileImg":"http://internal.local/admin"}"""
        mockMvc
            .perform(
                put("/api/v1/users/me")
                    .header("X-User-Id", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.details.profileImg").exists())
    }

    @Test
    @DisplayName("https:// 프로필 이미지는 통과")
    fun updateMe_httpsProfileImgAccepted() {
        whenever(userService.updateMyProfile(eq(1L), any())).thenReturn(
            UserProfileResponse(
                userId = 1L,
                email = "test@example.com",
                nickname = "철수",
                profileImg = "https://cdn.example.com/img.png",
                createdAt = Instant.now(),
            ),
        )
        val body = """{"profileImg":"https://cdn.example.com/img.png"}"""
        mockMvc
            .perform(
                put("/api/v1/users/me")
                    .header("X-User-Id", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.profileImg").value("https://cdn.example.com/img.png"))
    }
}

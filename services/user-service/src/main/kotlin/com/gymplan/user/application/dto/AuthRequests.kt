package com.gymplan.user.application.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * POST /api/v1/auth/register
 *
 * 제약:
 * - email:    RFC 이메일 형식
 * - password: 8~20자 (docs/context/security-guide.md)
 * - nickname: 2~20자
 */
data class RegisterRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
    val password: String,
    @field:NotBlank
    @field:Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
    val nickname: String,
)

/**
 * POST /api/v1/auth/login
 */
data class LoginRequest(
    @field:NotBlank
    @field:Email
    val email: String,
    @field:NotBlank
    val password: String,
)

/**
 * POST /api/v1/auth/refresh
 */
data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String,
)

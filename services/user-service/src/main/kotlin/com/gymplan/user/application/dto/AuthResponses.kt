package com.gymplan.user.application.dto

/**
 * 회원가입 응답 (201).
 * docs/api/user-service.md 의 Response 스키마와 1:1 매칭.
 */
data class RegisterResponse(
    val userId: Long,
    val email: String,
    val nickname: String,
    val accessToken: String,
    val refreshToken: String,
)

/**
 * 로그인 응답 (200).
 */
data class LoginResponse(
    val userId: Long,
    val nickname: String,
    val accessToken: String,
    val refreshToken: String,
)

/**
 * 토큰 갱신 응답 (200). Refresh Token Rotation 으로 두 토큰 모두 재발급.
 */
data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String,
)

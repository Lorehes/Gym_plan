package com.gymplan.user.presentation.controller

import com.gymplan.common.dto.ApiResponse
import com.gymplan.user.application.dto.LoginRequest
import com.gymplan.user.application.dto.LoginResponse
import com.gymplan.user.application.dto.RefreshRequest
import com.gymplan.user.application.dto.RegisterRequest
import com.gymplan.user.application.dto.RegisterResponse
import com.gymplan.user.application.dto.TokenRefreshResponse
import com.gymplan.user.application.service.AuthService
import com.gymplan.user.presentation.security.CurrentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 인증 엔드포인트.
 *
 * 참조: docs/api/user-service.md, docs/specs/user-service.md
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
    ): ResponseEntity<ApiResponse<RegisterResponse>> {
        val response = authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ApiResponse<LoginResponse> = ApiResponse.success(authService.login(request))

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): ApiResponse<TokenRefreshResponse> = ApiResponse.success(authService.refresh(request))

    @PostMapping("/logout")
    fun logout(
        @CurrentUserId userId: Long,
    ): ResponseEntity<Void> {
        authService.logout(userId)
        return ResponseEntity.noContent().build()
    }
}

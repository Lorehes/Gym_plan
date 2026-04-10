package com.gymplan.user.presentation.controller

import com.gymplan.common.dto.ApiResponse
import com.gymplan.user.application.dto.UpdateProfileRequest
import com.gymplan.user.application.dto.UserProfileResponse
import com.gymplan.user.application.service.UserService
import com.gymplan.common.security.CurrentUserId
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 프로필 엔드포인트.
 *
 * 참조: docs/api/user-service.md, docs/specs/user-service.md
 *
 * 모든 엔드포인트는 Gateway 가 주입한 X-User-Id 를 통해 본인 확인.
 * 타인 리소스 접근은 경로 자체에서 차단 (/me 만 제공).
 */
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {
    @GetMapping("/me")
    fun getMyProfile(
        @CurrentUserId userId: Long,
    ): ApiResponse<UserProfileResponse> = ApiResponse.success(userService.getMyProfile(userId))

    @PutMapping("/me")
    fun updateMyProfile(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ApiResponse<UserProfileResponse> = ApiResponse.success(userService.updateMyProfile(userId, request))
}

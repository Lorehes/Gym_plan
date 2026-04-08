package com.gymplan.user.application.dto

import com.gymplan.user.domain.entity.User
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * PUT /api/v1/users/me
 *
 * 부분 업데이트: 두 필드 모두 선택(null 허용).
 *
 * 보안:
 * - profileImg 는 https:// 만 허용 (SSRF 방지, docs/context/security-guide.md)
 * - 이메일/비밀번호는 이 엔드포인트로 변경할 수 없다.
 */
data class UpdateProfileRequest(
    @field:Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
    val nickname: String? = null,
    @field:Size(max = 500)
    @field:Pattern(
        regexp = "^https://.*",
        message = "프로필 이미지는 https URL만 허용됩니다.",
    )
    val profileImg: String? = null,
)

/**
 * GET /api/v1/users/me & PUT /api/v1/users/me 응답.
 *
 * 주의: password 필드는 절대 포함 금지 (TC-014).
 */
data class UserProfileResponse(
    val userId: Long,
    val email: String,
    val nickname: String,
    val profileImg: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User): UserProfileResponse =
            UserProfileResponse(
                userId = user.id ?: error("저장되지 않은 User 는 응답으로 변환 불가"),
                email = user.email,
                nickname = user.nickname,
                profileImg = user.profileImg,
                createdAt = user.createdAt,
            )
    }
}

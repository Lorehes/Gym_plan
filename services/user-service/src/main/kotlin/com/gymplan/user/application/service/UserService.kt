package com.gymplan.user.application.service

import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.NotFoundException
import com.gymplan.user.application.dto.UpdateProfileRequest
import com.gymplan.user.application.dto.UserProfileResponse
import com.gymplan.user.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자 프로필 유스케이스.
 *
 * 명세: docs/specs/user-service.md §2 US-5, US-6
 *
 * 보안:
 *  - 모든 메서드는 Gateway 가 주입한 X-User-Id 를 신뢰한다 (직접 JWT 검증 금지).
 *  - 다른 사용자의 리소스 조회/수정 불가 — 호출부(Controller)가 @CurrentUserId 로만 호출.
 */
@Service
class UserService(
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun getMyProfile(userId: Long): UserProfileResponse {
        val user =
            userRepository.findById(userId).orElseThrow {
                NotFoundException(ErrorCode.USER_NOT_FOUND)
            }
        return UserProfileResponse.from(user)
    }

    /**
     * 프로필 부분 수정. nickname 또는 profileImg 중 하나 이상 제공되어야 한다.
     * 이메일/비밀번호 변경은 이 엔드포인트 범위 밖.
     */
    @Transactional
    fun updateMyProfile(
        userId: Long,
        request: UpdateProfileRequest,
    ): UserProfileResponse {
        val user =
            userRepository.findById(userId).orElseThrow {
                NotFoundException(ErrorCode.USER_NOT_FOUND)
            }
        user.updateProfile(nickname = request.nickname, profileImg = request.profileImg)
        return UserProfileResponse.from(user)
    }
}

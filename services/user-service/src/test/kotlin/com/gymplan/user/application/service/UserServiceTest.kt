package com.gymplan.user.application.service

import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.NotFoundException
import com.gymplan.user.application.dto.UpdateProfileRequest
import com.gymplan.user.domain.entity.User
import com.gymplan.user.domain.entity.UserStatus
import com.gymplan.user.domain.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

class UserServiceTest {
    private lateinit var userRepository: UserRepository
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userRepository = mock()
        userService = UserService(userRepository)
    }

    // ─────────────── TC-014: 프로필 조회 성공 ───────────────

    @Test
    @DisplayName("TC-014: 내 프로필 조회는 password 를 노출하지 않는다")
    fun getMyProfile_success() {
        val user = buildUser(1L, "test@example.com", "HASH", "철수")
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))

        val response = userService.getMyProfile(1L)

        assertThat(response.userId).isEqualTo(1L)
        assertThat(response.email).isEqualTo("test@example.com")
        assertThat(response.nickname).isEqualTo("철수")
        // 응답 DTO 에는 password 필드 자체가 존재하지 않음 (컴파일 타임 보장)
        val fields = response::class.java.declaredFields.map { it.name }
        assertThat(fields).doesNotContain("password")
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회 시 USER_NOT_FOUND")
    fun getMyProfile_notFound() {
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { userService.getMyProfile(999L) }
            .isInstanceOf(NotFoundException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.USER_NOT_FOUND)
    }

    // ─────────────── TC-016: 닉네임만 부분 업데이트 ───────────────

    @Test
    @DisplayName("TC-016: 닉네임만 제공하면 profile_img 는 보존된다")
    fun updateMyProfile_partialNicknameOnly() {
        val user =
            buildUser(1L, "test@example.com", "HASH", "철수").apply {
                updateProfile(nickname = null, profileImg = "https://original.img")
            }
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))

        val response = userService.updateMyProfile(1L, UpdateProfileRequest(nickname = "영희", profileImg = null))

        assertThat(response.nickname).isEqualTo("영희")
        assertThat(response.profileImg).isEqualTo("https://original.img")
        assertThat(user.nickname).isEqualTo("영희")
    }

    @Test
    @DisplayName("profileImg 만 제공해도 닉네임은 보존된다")
    fun updateMyProfile_partialImageOnly() {
        val user = buildUser(1L, "test@example.com", "HASH", "철수")
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))

        val response =
            userService.updateMyProfile(
                1L,
                UpdateProfileRequest(nickname = null, profileImg = "https://new.img"),
            )

        assertThat(response.nickname).isEqualTo("철수")
        assertThat(response.profileImg).isEqualTo("https://new.img")
    }

    // ─────────────── 헬퍼 ───────────────

    private fun buildUser(
        id: Long,
        email: String,
        password: String,
        nickname: String = "철수",
        status: UserStatus = UserStatus.ACTIVE,
    ): User =
        User(email, password, nickname, null, status).apply {
            val field = javaClass.getDeclaredField("id")
            field.isAccessible = true
            field.set(this, id)
        }
}

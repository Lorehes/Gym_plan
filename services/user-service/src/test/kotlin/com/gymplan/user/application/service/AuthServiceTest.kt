package com.gymplan.user.application.service

import com.gymplan.common.exception.ConflictException
import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.ForbiddenException
import com.gymplan.common.exception.UnauthorizedException
import com.gymplan.common.security.JwtPayload
import com.gymplan.common.security.JwtProvider
import com.gymplan.user.application.dto.LoginRequest
import com.gymplan.user.application.dto.RefreshRequest
import com.gymplan.user.application.dto.RegisterRequest
import com.gymplan.user.domain.entity.User
import com.gymplan.user.domain.entity.UserStatus
import com.gymplan.user.domain.repository.UserRepository
import com.gymplan.user.infrastructure.cache.TokenStore
import com.gymplan.user.infrastructure.metrics.UserMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * AuthService 단위 테스트.
 *
 * 명세: docs/specs/user-service.md §7
 * 모든 외부 협력자(Repository, TokenStore, JwtProvider, PasswordEncoder)는 Mockito 로 교체.
 */
class AuthServiceTest {
    private lateinit var userRepository: UserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var jwtProvider: JwtProvider
    private lateinit var tokenStore: TokenStore
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        userRepository = mock()
        passwordEncoder = mock()
        jwtProvider = mock()
        tokenStore = mock()
        authService =
            AuthService(
                userRepository = userRepository,
                passwordEncoder = passwordEncoder,
                jwtProvider = jwtProvider,
                tokenStore = tokenStore,
                userMetrics = UserMetrics(SimpleMeterRegistry()),
            )
    }

    // ─────────────── TC-001: 정상 회원가입 ───────────────

    @Test
    @DisplayName("TC-001: 정상 회원가입 시 BCrypt 해시로 저장하고 토큰을 발급한다")
    fun register_success() {
        val request = RegisterRequest("test@example.com", "P@ssw0rd123!", "철수")
        val hashed = "\$2a\$10\$HASHED"
        whenever(userRepository.existsByEmail("test@example.com")).thenReturn(false)
        whenever(passwordEncoder.encode("P@ssw0rd123!")).thenReturn(hashed)
        whenever(userRepository.save(any<User>())).thenAnswer { invocation ->
            val u = invocation.arguments[0] as User
            u.setIdForTest(1L)
            u
        }
        whenever(jwtProvider.createAccessToken(1L, "test@example.com")).thenReturn("ACCESS")
        whenever(jwtProvider.createRefreshToken(1L)).thenReturn("REFRESH")

        val response = authService.register(request)

        assertThat(response.userId).isEqualTo(1L)
        assertThat(response.email).isEqualTo("test@example.com")
        assertThat(response.nickname).isEqualTo("철수")
        assertThat(response.accessToken).isEqualTo("ACCESS")
        assertThat(response.refreshToken).isEqualTo("REFRESH")

        val saved = argumentCaptor<User>()
        verify(userRepository).save(saved.capture())
        assertThat(saved.firstValue.password).isEqualTo(hashed)
        assertThat(saved.firstValue.password).isNotEqualTo("P@ssw0rd123!") // 평문 저장 금지
        verify(tokenStore).saveRefreshToken("REFRESH", 1L)
    }

    // ─────────────── TC-002: 중복 이메일 회원가입 ───────────────

    @Test
    @DisplayName("TC-002: 이미 존재하는 이메일이면 AUTH_DUPLICATE_EMAIL")
    fun register_duplicateEmail() {
        val request = RegisterRequest("dup@example.com", "P@ssw0rd123!", "철수")
        whenever(userRepository.existsByEmail("dup@example.com")).thenReturn(true)

        assertThatThrownBy { authService.register(request) }
            .isInstanceOf(ConflictException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AUTH_DUPLICATE_EMAIL)

        verify(userRepository, never()).save(any<User>())
    }

    // ─────────────── TC-020: 동시 회원가입 경합 ───────────────

    @Test
    @DisplayName("TC-020: save 시 UNIQUE 제약 위반은 AUTH_DUPLICATE_EMAIL 로 변환된다")
    fun register_raceCondition() {
        val request = RegisterRequest("race@example.com", "P@ssw0rd123!", "철수")
        whenever(userRepository.existsByEmail(any())).thenReturn(false)
        whenever(passwordEncoder.encode(any())).thenReturn("HASH")
        whenever(userRepository.save(any<User>())).thenThrow(
            DataIntegrityViolationException("Duplicate entry"),
        )

        assertThatThrownBy { authService.register(request) }
            .isInstanceOf(ConflictException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AUTH_DUPLICATE_EMAIL)
    }

    // ─────────────── TC-004: 정상 로그인 ───────────────

    @Test
    @DisplayName("TC-004: 정상 로그인 시 토큰 발급 및 실패 카운터 초기화")
    fun login_success() {
        val request = LoginRequest("test@example.com", "P@ssw0rd123!")
        val user = buildUser(id = 1L, email = "test@example.com", password = "HASH")
        whenever(tokenStore.isLocked("test@example.com")).thenReturn(false)
        whenever(userRepository.findByEmail("test@example.com")).thenReturn(user)
        whenever(passwordEncoder.matches("P@ssw0rd123!", "HASH")).thenReturn(true)
        whenever(jwtProvider.createAccessToken(1L, "test@example.com")).thenReturn("AT")
        whenever(jwtProvider.createRefreshToken(1L)).thenReturn("RT")

        val response = authService.login(request)

        assertThat(response.userId).isEqualTo(1L)
        assertThat(response.accessToken).isEqualTo("AT")
        assertThat(response.refreshToken).isEqualTo("RT")
        verify(tokenStore).clearLoginFailures("test@example.com")
        verify(tokenStore).saveRefreshToken("RT", 1L)
    }

    // ─────────────── TC-005: 잘못된 비밀번호 ───────────────

    @Test
    @DisplayName("TC-005: 잘못된 비밀번호는 AUTH_INVALID_CREDENTIALS + 실패 카운터 증가")
    fun login_wrongPassword() {
        val request = LoginRequest("test@example.com", "WrongPass!")
        val user = buildUser(id = 1L, email = "test@example.com", password = "HASH")
        whenever(tokenStore.isLocked(any())).thenReturn(false)
        whenever(userRepository.findByEmail("test@example.com")).thenReturn(user)
        whenever(passwordEncoder.matches("WrongPass!", "HASH")).thenReturn(false)

        assertThatThrownBy { authService.login(request) }
            .isInstanceOf(UnauthorizedException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS)

        verify(tokenStore).recordLoginFailure("test@example.com")
    }

    // ─────────────── TC-006: 존재하지 않는 이메일 ───────────────

    @Test
    @DisplayName("TC-006: 존재하지 않는 이메일도 TC-005 와 동일한 에러를 반환한다")
    fun login_nonExistentEmail() {
        val request = LoginRequest("unknown@example.com", "P@ssw0rd123!")
        whenever(tokenStore.isLocked(any())).thenReturn(false)
        whenever(userRepository.findByEmail("unknown@example.com")).thenReturn(null)

        assertThatThrownBy { authService.login(request) }
            .isInstanceOf(UnauthorizedException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS)

        verify(tokenStore).recordLoginFailure("unknown@example.com")
    }

    // ─────────────── TC-007: 브루트포스 잠금 ───────────────

    @Test
    @DisplayName("TC-007: 잠금 상태에서는 비밀번호 검증 없이 AUTH_ACCOUNT_LOCKED")
    fun login_locked() {
        val request = LoginRequest("locked@example.com", "AnyPassword!")
        whenever(tokenStore.isLocked("locked@example.com")).thenReturn(true)

        assertThatThrownBy { authService.login(request) }
            .isInstanceOf(UnauthorizedException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED)

        verify(userRepository, never()).findByEmail(any())
        verify(passwordEncoder, never()).matches(any(), any())
    }

    // ─────────────── TC-008: 비활성 계정 ───────────────

    @Test
    @DisplayName("TC-008: BANNED 상태 계정은 AUTH_ACCOUNT_DISABLED")
    fun login_bannedAccount() {
        val request = LoginRequest("banned@example.com", "P@ssw0rd123!")
        val user = buildUser(id = 1L, email = "banned@example.com", password = "HASH", status = UserStatus.BANNED)
        whenever(tokenStore.isLocked(any())).thenReturn(false)
        whenever(userRepository.findByEmail("banned@example.com")).thenReturn(user)
        whenever(passwordEncoder.matches(any(), any())).thenReturn(true)

        assertThatThrownBy { authService.login(request) }
            .isInstanceOf(ForbiddenException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AUTH_ACCOUNT_DISABLED)

        verify(jwtProvider, never()).createAccessToken(any(), any())
    }

    // ─────────────── TC-009: Refresh Token 정상 Rotation ───────────────

    @Test
    @DisplayName("TC-009: 유효한 Refresh Token 은 새 토큰 쌍으로 rotation 된다")
    fun refresh_success() {
        val oldToken = "RT_OLD"
        whenever(jwtProvider.parse(oldToken)).thenReturn(JwtPayload(userId = 1L, email = null, type = "refresh"))
        whenever(tokenStore.findUserIdByRefreshToken(oldToken)).thenReturn(1L)
        val user = buildUser(id = 1L, email = "test@example.com", password = "HASH")
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(jwtProvider.createAccessToken(1L, "test@example.com")).thenReturn("AT_NEW")
        whenever(jwtProvider.createRefreshToken(1L)).thenReturn("RT_NEW")

        val response = authService.refresh(RefreshRequest(oldToken))

        assertThat(response.accessToken).isEqualTo("AT_NEW")
        assertThat(response.refreshToken).isEqualTo("RT_NEW")
        verify(tokenStore).deleteRefreshToken(oldToken, 1L)
        verify(tokenStore).saveRefreshToken("RT_NEW", 1L)
    }

    // ─────────────── TC-010: 만료/무효 Refresh Token ───────────────

    @Test
    @DisplayName("TC-010: 만료된 Refresh Token 은 AUTH_INVALID_REFRESH_TOKEN")
    fun refresh_expired() {
        val token = "RT_EXPIRED"
        whenever(jwtProvider.parse(token))
            .thenThrow(UnauthorizedException(ErrorCode.AUTH_EXPIRED_TOKEN))

        assertThatThrownBy { authService.refresh(RefreshRequest(token)) }
            .isInstanceOf(UnauthorizedException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AUTH_INVALID_REFRESH_TOKEN)
    }

    @Test
    @DisplayName("TC-010b: type=access 토큰으로 refresh 시도 시 AUTH_INVALID_REFRESH_TOKEN")
    fun refresh_wrongType() {
        val token = "RT_IS_ACTUALLY_ACCESS"
        whenever(jwtProvider.parse(token)).thenReturn(JwtPayload(1L, null, "access"))

        assertThatThrownBy { authService.refresh(RefreshRequest(token)) }
            .isInstanceOf(UnauthorizedException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AUTH_INVALID_REFRESH_TOKEN)
    }

    // ─────────────── TC-011: Refresh Token 재사용 탐지 ───────────────

    @Test
    @DisplayName("TC-011: Redis 에 없는(이미 rotation된) 토큰은 전체 세션 폐기 후 REUSED 예외")
    fun refresh_reuseDetection() {
        val token = "RT_REUSED"
        whenever(jwtProvider.parse(token)).thenReturn(JwtPayload(1L, null, "refresh"))
        whenever(tokenStore.findUserIdByRefreshToken(token)).thenReturn(null)

        assertThatThrownBy { authService.refresh(RefreshRequest(token)) }
            .isInstanceOf(UnauthorizedException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_REUSED)

        verify(tokenStore).revokeAllRefreshTokens(1L)
        verify(tokenStore, never()).saveRefreshToken(any(), any())
    }

    @Test
    @DisplayName("TC-011b: 클레임의 userId 와 저장소의 userId 가 다르면 전체 세션 폐기 후 REUSED 예외")
    fun refresh_claimStoreMismatch() {
        // JWT 는 userId=1 을 주장하지만, Redis 에는 동일 토큰 해시가 userId=2 로 저장되어 있음.
        // 정상적으로는 발생할 수 없는 상태 — 위조 또는 심각한 저장소 손상을 의미하므로
        // 재사용과 동일하게 클레임이 주장한 userId 의 전체 세션을 폐기한다.
        val token = "RT_FORGED"
        whenever(jwtProvider.parse(token)).thenReturn(JwtPayload(1L, null, "refresh"))
        whenever(tokenStore.findUserIdByRefreshToken(token)).thenReturn(2L)

        assertThatThrownBy { authService.refresh(RefreshRequest(token)) }
            .isInstanceOf(UnauthorizedException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_REUSED)

        verify(tokenStore).revokeAllRefreshTokens(1L)
        verify(tokenStore, never()).deleteRefreshToken(any(), any())
        verify(tokenStore, never()).saveRefreshToken(any(), any())
    }

    // ─────────────── TC-012: 로그아웃 ───────────────

    @Test
    @DisplayName("TC-012: 로그아웃 시 해당 사용자의 모든 Refresh Token 을 폐기한다")
    fun logout_success() {
        authService.logout(42L)
        verify(tokenStore).revokeAllRefreshTokens(42L)
    }

    // ─────────────── 헬퍼 ───────────────

    private fun buildUser(
        id: Long,
        email: String,
        password: String,
        nickname: String = "철수",
        status: UserStatus = UserStatus.ACTIVE,
    ): User =
        User(
            email = email,
            password = password,
            nickname = nickname,
            profileImg = null,
            status = status,
        ).apply { setIdForTest(id) }

    /**
     * JPA Entity 의 id 는 protected set 이라 테스트 용으로 리플렉션으로 주입한다.
     */
    private fun User.setIdForTest(id: Long) {
        val prop = User::class.memberProperties.first { it.name == "id" }
        prop.isAccessible = true
        val field = this::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(this, id)
    }
}

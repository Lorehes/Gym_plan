package com.gymplan.user.application.service

import com.gymplan.common.exception.ConflictException
import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.ForbiddenException
import com.gymplan.common.exception.UnauthorizedException
import com.gymplan.common.security.JwtProvider
import com.gymplan.user.application.dto.LoginRequest
import com.gymplan.user.application.dto.LoginResponse
import com.gymplan.user.application.dto.RefreshRequest
import com.gymplan.user.application.dto.RegisterRequest
import com.gymplan.user.application.dto.RegisterResponse
import com.gymplan.user.application.dto.TokenRefreshResponse
import com.gymplan.user.domain.entity.User
import com.gymplan.user.domain.repository.UserRepository
import com.gymplan.user.infrastructure.cache.TokenStore
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인증 유스케이스: 회원가입, 로그인, 토큰 갱신, 로그아웃.
 *
 * 명세: docs/specs/user-service.md §4, §7
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val tokenStore: TokenStore,
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    /**
     * 회원가입.
     *
     * 처리 순서 (TC-001, TC-002, TC-020):
     *  1. email 중복 확인 (조기 실패)
     *  2. password BCrypt 해싱
     *  3. User 저장
     *  4. 동시 삽입 경합은 DB UNIQUE 제약이 최종 보장 → DataIntegrityViolationException 재해석
     *  5. 토큰 발급 및 Redis 저장
     */
    @Transactional
    fun register(request: RegisterRequest): RegisterResponse {
        val normalizedEmail = request.email.lowercase()

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw ConflictException(ErrorCode.AUTH_DUPLICATE_EMAIL)
        }

        val user =
            User(
                email = normalizedEmail,
                password = passwordEncoder.encode(request.password),
                nickname = request.nickname,
            )

        val saved =
            try {
                userRepository.save(user)
            } catch (e: DataIntegrityViolationException) {
                // UNIQUE 제약 경합 (TC-020)
                throw ConflictException(ErrorCode.AUTH_DUPLICATE_EMAIL, cause = e.toErrorCause())
            }

        val tokens = issueTokens(saved)
        log.info("신규 회원가입 완료: userId={}, email={}", saved.id, maskEmail(saved.email))

        return RegisterResponse(
            userId = saved.id!!,
            email = saved.email,
            nickname = saved.nickname,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
        )
    }

    /**
     * 로그인.
     *
     * 실패 응답은 계정 존재 여부를 노출하지 않기 위해
     * (a) 이메일 미존재와 (b) 비밀번호 불일치 모두 동일하게 AUTH_INVALID_CREDENTIALS 를 던진다.
     * (TC-005, TC-006)
     *
     * 브루트포스 방어 (TC-007):
     *  - 잠금 상태이면 즉시 AUTH_ACCOUNT_LOCKED
     *  - 실패 시 카운터 증가
     *  - 성공 시 카운터 초기화
     */
    @Transactional(readOnly = true)
    fun login(request: LoginRequest): LoginResponse {
        val emailLower = request.email.lowercase()

        if (tokenStore.isLocked(emailLower)) {
            throw UnauthorizedException(ErrorCode.AUTH_ACCOUNT_LOCKED)
        }

        val user = userRepository.findByEmail(emailLower)
        if (user == null || !passwordEncoder.matches(request.password, user.password)) {
            tokenStore.recordLoginFailure(emailLower)
            throw UnauthorizedException(ErrorCode.AUTH_INVALID_CREDENTIALS)
        }

        if (!user.status.canLogin()) {
            // 비활성/BANNED 계정 (TC-008)
            throw ForbiddenException(ErrorCode.AUTH_ACCOUNT_DISABLED)
        }

        tokenStore.clearLoginFailures(emailLower)
        val tokens = issueTokens(user)
        log.info("로그인 성공: userId={}, email={}", user.id, maskEmail(user.email))

        return LoginResponse(
            userId = user.id!!,
            nickname = user.nickname,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
        )
    }

    /**
     * Refresh Token Rotation.
     *
     * 처리 순서 (TC-009, TC-010, TC-011):
     *  1. JWT 파싱 (만료/서명 검증) — 실패 시 AUTH_INVALID_REFRESH_TOKEN
     *  2. type == "refresh" 검사
     *  3. Redis 에서 토큰 하나를 atomic 하게 삭제 시도
     *      - 삭제 성공: 정상 Rotation 경로
     *      - 삭제 실패: 재사용 의심 → 사용자 전체 세션 폐기 + AUTH_REFRESH_TOKEN_REUSED
     *  4. 새 Access/Refresh Token 발급 및 Redis 저장
     */
    @Transactional(readOnly = true)
    fun refresh(request: RefreshRequest): TokenRefreshResponse {
        val payload =
            try {
                jwtProvider.parse(request.refreshToken)
            } catch (e: UnauthorizedException) {
                throw UnauthorizedException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN, cause = e)
            }

        if (!payload.isRefresh()) {
            throw UnauthorizedException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN)
        }

        val userId = payload.userId

        // Redis 상의 기록과 대조. 없으면 이미 rotation 되었거나 만료된 토큰.
        val storedUserId = tokenStore.findUserIdByRefreshToken(request.refreshToken)
        if (storedUserId == null) {
            // 재사용 의심: 이 userId 의 모든 세션을 폐기
            tokenStore.revokeAllRefreshTokens(userId)
            log.warn("Refresh Token 재사용 감지 — 사용자 전체 세션 폐기: userId={}", userId)
            throw UnauthorizedException(ErrorCode.AUTH_REFRESH_TOKEN_REUSED)
        }
        if (storedUserId != userId) {
            // 클레임과 저장소 불일치 (위조 시도) — 안전 측면에서 재사용과 동일 처리
            tokenStore.revokeAllRefreshTokens(userId)
            throw UnauthorizedException(ErrorCode.AUTH_REFRESH_TOKEN_REUSED)
        }

        // 정상 Rotation: 기존 토큰 삭제 후 새로 발급
        tokenStore.deleteRefreshToken(request.refreshToken, userId)

        val user =
            userRepository.findById(userId).orElseThrow {
                UnauthorizedException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN)
            }
        if (!user.status.canLogin()) {
            throw ForbiddenException(ErrorCode.AUTH_ACCOUNT_DISABLED)
        }

        val tokens = issueTokens(user)
        return TokenRefreshResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
        )
    }

    /**
     * 로그아웃 (TC-012).
     *
     * 현재 디자인: 해당 사용자의 모든 Refresh Token 을 폐기한다.
     * 향후 Gateway 에서 sessionId 를 전달하도록 확장되면 단일 세션 로그아웃으로 전환 가능.
     */
    @Transactional(readOnly = true)
    fun logout(userId: Long) {
        tokenStore.revokeAllRefreshTokens(userId)
        log.info("로그아웃 완료: userId={}", userId)
    }

    // ───── 내부 유틸 ─────

    private data class IssuedTokens(val accessToken: String, val refreshToken: String)

    private fun issueTokens(user: User): IssuedTokens {
        val userId = user.id ?: error("저장되지 않은 User 에 토큰 발급 불가")
        val accessToken = jwtProvider.createAccessToken(userId, user.email)
        val refreshToken = jwtProvider.createRefreshToken(userId)
        tokenStore.saveRefreshToken(refreshToken, userId)
        return IssuedTokens(accessToken, refreshToken)
    }

    private fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 1) return "***"
        return "${email.first()}***${email.substring(at)}"
    }

    private fun Throwable.toErrorCause(): Throwable = this
}

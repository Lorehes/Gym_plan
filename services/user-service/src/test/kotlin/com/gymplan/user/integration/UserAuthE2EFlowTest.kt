package com.gymplan.user.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import com.gymplan.user.domain.repository.UserRepository
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * 사용자 인증 E2E 흐름 통합 테스트.
 *
 * 기존 [AuthFlowIntegrationTest] 는 개별 케이스를 독립적으로 검증하는 반면,
 * 이 테스트는 아래 시나리오를 **단계적으로 체이닝**하여 전체 흐름을 검증한다.
 *
 * ------------------------------------------------------------------
 * 시나리오 1 — 회원가입 → 로그인 → JWT 발급
 * ------------------------------------------------------------------
 * STEP-1. POST /api/v1/auth/register → 201 + userId + accessToken + refreshToken
 * STEP-2. 회원가입 직후 accessToken 유효성: GET /api/v1/users/me (X-User-Id 주입) → 200
 * STEP-3. 동일 이메일 재가입 → 409 AUTH_DUPLICATE_EMAIL (DB row 중복 생성 없음 확인)
 * STEP-4. POST /api/v1/auth/login → 200 + 새 accessToken (재발급 확인)
 * STEP-5. 로그인 후 accessToken 으로 GET /api/v1/users/me → 200 (토큰 교체 후 재사용 가능)
 * STEP-6. JWT 클레임 검증: sub=userId, type=access, exp≈iat+30분 (RS256 공개키로 파싱)
 *
 * ------------------------------------------------------------------
 * 시나리오 보안 — 로그인 실패 동일 에러 응답 (TC-005, TC-006)
 * ------------------------------------------------------------------
 * STEP-7. 잘못된 비밀번호 → 401 AUTH_INVALID_CREDENTIALS + "이메일 또는 비밀번호가 올바르지 않습니다"
 * STEP-8. 존재하지 않는 이메일 → 동일 코드·메시지 (계정 존재 여부 노출 금지)
 *
 * ------------------------------------------------------------------
 * TC-007 — 브루트포스 방어
 * ------------------------------------------------------------------
 * STEP-9. 5회 연속 실패 → 6번째 시도(올바른 비밀번호) → 429 AUTH_ACCOUNT_LOCKED
 *        Redis `user:locked:{email}` 키 TTL ≤ 300초 확인
 *
 * 명세: docs/specs/user-service.md §7 TC-001, TC-004, TC-005, TC-006, TC-007
 * 테스트 정책: Testcontainers 실제 MySQL/Redis 사용 (모킹 금지) — §4.4
 */
@SpringBootTest
class UserAuthE2EFlowTest
    @Autowired
    constructor(
        private val webApplicationContext: WebApplicationContext,
        private val userRepository: UserRepository,
        private val passwordEncoder: PasswordEncoder,
        private val stringRedisTemplate: StringRedisTemplate,
        /**
         * AbstractIntegrationTest.registerProperties() 가 DynamicPropertySource 로 주입한 PEM.
         * 이 값으로 토큰을 직접 파싱해 클레임을 검증한다.
         */
        @Value("\${gymplan.jwt.public-key}") private val jwtPublicKeyPem: String,
    ) : AbstractIntegrationTest() {

    private val objectMapper = ObjectMapper()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        // 테스트 간 상태 완전 격리 (Redis flush + DB 초기화)
        stringRedisTemplate.connectionFactory!!
            .connection
            .use { it.serverCommands().flushAll() }
        userRepository.deleteAll()
    }

    // ─────────────────────────────────────────────────────────────────
    // 시나리오 1: 회원가입 → 로그인 → JWT 발급 전체 흐름
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("시나리오 1: 회원가입 → 로그인 → JWT 발급 전체 흐름 (TC-001, TC-004)")
    fun `시나리오1_회원가입후로그인하면유효한JWT발급및보호엔드포인트접근가능`() {
        val email = "e2e_scenario1@example.com"
        val password = "P@ssw0rd123!"

        // ─── STEP-1: 회원가입 → 201 + 토큰 발급 ───
        val registerResult = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password","nickname":"e2e철수"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").isNumber)
            .andExpect(jsonPath("$.data.accessToken").isString)
            .andExpect(jsonPath("$.data.refreshToken").isString)
            .andReturn()

        val registerBody = objectMapper.readTree(registerResult.response.contentAsString)
        val userId = registerBody["data"]["userId"].asLong()
        val registerAccessToken = registerBody["data"]["accessToken"].asText()
        val registerRefreshToken = registerBody["data"]["refreshToken"].asText()

        // DB 검증: 비밀번호는 BCrypt 해시로 저장 (평문 금지, §4.1 회원가입)
        val savedUser = userRepository.findByEmail(email)!!
        assertThat(savedUser.password)
            .describedAs("비밀번호는 BCrypt 해시로 저장되어야 한다")
            .startsWith("\$2a\$10\$")
        assertThat(passwordEncoder.matches(password, savedUser.password))
            .describedAs("BCrypt.matches()는 원래 비밀번호로 true여야 한다")
            .isTrue

        // Redis 검증: Refresh Token 해시 키가 7일 TTL로 존재 (§6.2)
        val refreshRedisKey = refreshKey(registerRefreshToken)
        assertThat(stringRedisTemplate.hasKey(refreshRedisKey))
            .describedAs("회원가입 후 Redis에 Refresh Token 해시 키가 존재해야 한다")
            .isTrue

        // ─── STEP-2: 회원가입 직후 X-User-Id 헤더로 프로필 조회 → 200 ───
        // (Gateway 없이 직접 호출 — user-service 가 X-User-Id 헤더를 직접 읽음)
        mockMvc.perform(
            get("/api/v1/users/me")
                .header("X-User-Id", userId.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.email").value(email))
            .andExpect(jsonPath("$.data.nickname").value("e2e철수"))
            // 보안: 응답에 password 필드 절대 포함 금지 (§4.1 프로필 조회)
            .andExpect(jsonPath("$.data.password").doesNotExist())

        // ─── STEP-3: 동일 이메일 재가입 → 409 AUTH_DUPLICATE_EMAIL ───
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password","nickname":"또철수"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("AUTH_DUPLICATE_EMAIL"))

        // DB row 중복 생성 방지 확인 (§4.1 TC-002)
        assertThat(userRepository.count())
            .describedAs("중복 이메일 재가입 시 DB row가 추가되면 안 된다")
            .isEqualTo(1L)

        // ─── STEP-4: 로그인 → 200 + 새 토큰 쌍 ───
        val loginResult = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isString)
            .andExpect(jsonPath("$.data.refreshToken").isString)
            .andReturn()

        val loginBody = objectMapper.readTree(loginResult.response.contentAsString)
        val loginAccessToken = loginBody["data"]["accessToken"].asText()
        val loginRefreshToken = loginBody["data"]["refreshToken"].asText()

        // 로그인마다 새 토큰이 발급됨 (재발급 동작 확인)
        assertThat(loginAccessToken)
            .describedAs("로그인 시 회원가입 때와 다른 토큰이 발급되어야 한다")
            .isNotEqualTo(registerAccessToken)

        // ─── STEP-5: 로그인 후 새 accessToken 으로 프로필 조회 → 200 ───
        mockMvc.perform(
            get("/api/v1/users/me")
                .header("X-User-Id", userId.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.email").value(email))

        // Redis 검증: 로그인 후 새 Refresh Token도 등록
        assertThat(stringRedisTemplate.hasKey(refreshKey(loginRefreshToken)))
            .describedAs("로그인 후 새 Refresh Token이 Redis에 등록되어야 한다")
            .isTrue

        // ─── STEP-6: JWT 클레임 검증 (RS256 공개키로 직접 파싱) ───
        val claims = parseAccessToken(loginAccessToken)

        assertThat(claims.subject)
            .describedAs("JWT sub 클레임은 userId 문자열이어야 한다")
            .isEqualTo(userId.toString())

        assertThat(claims["type"])
            .describedAs("JWT type 클레임은 'access' 이어야 한다")
            .isEqualTo("access")

        // Access Token TTL = 30분 (±1분 오차 허용)
        val ttlMinutes = (claims.expiration.time - claims.issuedAt.time) / 1000 / 60
        assertThat(ttlMinutes)
            .describedAs("Access Token TTL은 30분이어야 한다 (±1분 허용)")
            .isBetween(29L, 31L)
    }

    // ─────────────────────────────────────────────────────────────────
    // 시나리오 보안: 로그인 실패 동일 에러 응답 (TC-005, TC-006)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("시나리오 보안: 잘못된 비밀번호와 없는 이메일 모두 동일한 에러 응답 — 계정 존재 여부 노출 금지 (TC-005, TC-006)")
    fun `로그인실패시계정존재여부무관하게동일한에러응답`() {
        val email = "security_test@example.com"
        registerUser(email)

        val expectedCode = "AUTH_INVALID_CREDENTIALS"
        val expectedMessage = "이메일 또는 비밀번호가 올바르지 않습니다"

        // TC-005: 잘못된 비밀번호 (계정 존재 O)
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"WrongPass!"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value(expectedCode))
            .andExpect(jsonPath("$.error.message").value(expectedMessage))

        // TC-006: 존재하지 않는 이메일 (계정 존재 X) → 완전히 동일한 코드·메시지 반환
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"nobody_here@example.com","password":"P@ssw0rd123!"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value(expectedCode))
            .andExpect(jsonPath("$.error.message").value(expectedMessage))
    }

    // ─────────────────────────────────────────────────────────────────
    // TC-007: 브루트포스 방어 — 5회 실패 후 계정 잠금
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-007: 1분 내 5회 실패 → 6번째 시도(올바른 비밀번호도) 429 AUTH_ACCOUNT_LOCKED + Redis 잠금 키 TTL 확인")
    fun `TC007_5회실패후계정잠금_올바른비밀번호도차단`() {
        val email = "brute_force_victim@example.com"
        registerUser(email)

        // 5회 로그인 실패 (잘못된 비밀번호)
        repeat(5) { attempt ->
            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","password":"WrongPass${attempt}!"}"""),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"))
        }

        // 6번째 시도 — 올바른 비밀번호여도 잠금 상태 (TC-007 핵심)
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"P@ssw0rd123!"}"""),
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_LOCKED"))

        // Redis 잠금 키 TTL 확인 (5분 = 300초)
        val lockedKey = "user:locked:$email"
        assertThat(stringRedisTemplate.hasKey(lockedKey))
            .describedAs("잠금 Redis 키 'user:locked:{email}' 이 존재해야 한다")
            .isTrue
        val ttl = stringRedisTemplate.getExpire(lockedKey)
        assertThat(ttl)
            .describedAs("잠금 TTL은 0~300초 사이여야 한다")
            .isGreaterThan(0L)
            .isLessThanOrEqualTo(300L)
    }

    // ─────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────

    /** 이메일로 사용자 등록. 반환값 없이 상태 코드만 확인. */
    private fun registerUser(email: String) {
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"P@ssw0rd123!","nickname":"테스트유저"}"""),
        ).andExpect(status().isCreated)
    }

    /**
     * DynamicPropertySource 로 주입된 공개키(jwtPublicKeyPem)로 Access Token 파싱.
     *
     * - 서명 검증 포함 → 위조된 토큰은 예외 발생
     * - [com.gymplan.common.security.JwtProvider.pemToDer] 와 동일한 PEM 파싱 로직 사용
     */
    private fun parseAccessToken(token: String): Claims {
        val der = Base64.getDecoder().decode(
            jwtPublicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), ""),
        )
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der)) as RSAPublicKey
        return Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    /**
     * TokenStore 의 키 네이밍과 일치.
     * [com.gymplan.user.infrastructure.cache.TokenStore] 와 동일한 SHA-256 로직.
     */
    private fun refreshKey(rawToken: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())
        return "user:refresh:${java.util.HexFormat.of().formatHex(digest)}"
    }
}

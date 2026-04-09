package com.gymplan.user.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.gymplan.user.domain.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * end-to-end 인증 흐름 통합 테스트.
 *
 * 실제 MySQL + Redis (Testcontainers) + 전체 Spring 컨텍스트 + MockMvc 로
 * 명세 §7 의 핵심 시나리오(TC-001, TC-004, TC-009, TC-011, TC-012) 를 검증한다.
 *
 * @Transactional 를 사용하지 않는 이유:
 * - TC-009 rotation 테스트는 중간에 Redis 상태 확인이 필요한데,
 *   @Transactional 은 JPA 만 롤백하고 Redis 는 롤백하지 않아 케이스 간 오염 위험이 있음.
 * - 대신 @BeforeEach 에서 Redis FLUSHALL + 테스트별 유니크 이메일을 사용한다.
 */
@SpringBootTest
class AuthFlowIntegrationTest
    @Autowired
    constructor(
        private val webApplicationContext: WebApplicationContext,
        private val userRepository: UserRepository,
        private val passwordEncoder: PasswordEncoder,
        private val stringRedisTemplate: StringRedisTemplate,
    ) : AbstractIntegrationTest() {
        private val objectMapper = ObjectMapper()
        private lateinit var mockMvc: MockMvc

        @BeforeEach
        fun setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
            // 테스트 간 상태 격리
            stringRedisTemplate.connectionFactory!!
                .connection
                .use { it.serverCommands().flushAll() }
            userRepository.deleteAll()
        }

        // ─────────────── TC-001: 정상 회원가입 ───────────────

        @Test
        @DisplayName("TC-001: 회원가입 성공 시 BCrypt 해시 저장 + Redis 에 Refresh Token 저장")
        fun register_persistsUserAndTokens() {
            val email = "tc001@example.com"
            val response =
                mockMvc
                    .perform(
                        post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """
                                {"email":"$email","password":"P@ssw0rd123!","nickname":"철수"}
                                """.trimIndent(),
                            ),
                    )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").isNumber)
                    .andExpect(jsonPath("$.data.accessToken").isString)
                    .andExpect(jsonPath("$.data.refreshToken").isString)
                    .andReturn()

            // DB 검증: BCrypt 해시로 저장됨 (평문 금지)
            val saved = userRepository.findByEmail(email)!!
            assertThat(saved.password).isNotEqualTo("P@ssw0rd123!")
            assertThat(passwordEncoder.matches("P@ssw0rd123!", saved.password)).isTrue

            // Redis 검증: Refresh Token 해시 키가 존재
            val body: JsonNode = objectMapper.readTree(response.response.contentAsString)
            val refreshToken = body["data"]["refreshToken"].asText()
            assertThat(stringRedisTemplate.hasKey(refreshKey(refreshToken))).isTrue
        }

        @Test
        @DisplayName("TC-002: 동일 이메일로 재가입하면 409 AUTH_DUPLICATE_EMAIL")
        fun register_duplicate() {
            val email = "tc002@example.com"
            register(email)

            mockMvc
                .perform(
                    post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"email":"$email","password":"P@ssw0rd123!","nickname":"철수"}
                            """.trimIndent(),
                        ),
                )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("AUTH_DUPLICATE_EMAIL"))
        }

        // ─────────────── TC-004: 정상 로그인 ───────────────

        @Test
        @DisplayName("TC-004: 로그인 성공 시 새 토큰 쌍 발급")
        fun login_success() {
            val email = "tc004@example.com"
            register(email)

            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"email":"$email","password":"P@ssw0rd123!"}
                            """.trimIndent(),
                        ),
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString)
                .andExpect(jsonPath("$.data.refreshToken").isString)
        }

        @Test
        @DisplayName("TC-005/006: 비밀번호 오류 + 없는 이메일 모두 AUTH_INVALID_CREDENTIALS")
        fun login_invalidCredentialsAndUnknownEmail_returnSameCode() {
            val email = "tc005@example.com"
            register(email)

            // 잘못된 비밀번호
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"email":"$email","password":"WrongPass!"}
                            """.trimIndent(),
                        ),
                )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"))

            // 존재하지 않는 이메일 — 동일 응답
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"email":"nobody@example.com","password":"P@ssw0rd123!"}
                            """.trimIndent(),
                        ),
                )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"))
        }

        // ─────────────── TC-009, TC-011: Refresh Token Rotation + 재사용 탐지 ───────────────

        @Test
        @DisplayName("TC-009 + TC-011: Rotation 성공 → 구 토큰으로 재시도 시 REUSED + 전체 세션 폐기")
        fun refresh_rotationAndReuseDetection() {
            val email = "tc009@example.com"
            val registerBody = register(email)
            val oldRefresh = registerBody["data"]["refreshToken"].asText()

            // TC-009: 정상 rotation
            val refreshResponse =
                mockMvc
                    .perform(
                        post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"refreshToken":"$oldRefresh"}"""),
                    )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.data.accessToken").isString)
                    .andExpect(jsonPath("$.data.refreshToken").isString)
                    .andReturn()

            val refreshBody = objectMapper.readTree(refreshResponse.response.contentAsString)
            val newRefresh = refreshBody["data"]["refreshToken"].asText()
            assertThat(newRefresh).isNotEqualTo(oldRefresh)

            // Redis 상태: 구 토큰은 사라지고 신 토큰 존재
            assertThat(stringRedisTemplate.hasKey(refreshKey(oldRefresh))).isFalse
            assertThat(stringRedisTemplate.hasKey(refreshKey(newRefresh))).isTrue

            // TC-011: 구 토큰 재사용 → REUSED + 신 토큰까지 전체 폐기
            mockMvc
                .perform(
                    post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":"$oldRefresh"}"""),
                )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_TOKEN_REUSED"))

            assertThat(stringRedisTemplate.hasKey(refreshKey(newRefresh))).isFalse
        }

        @Test
        @DisplayName("TC-010: 형식이 잘못된 Refresh Token 은 AUTH_INVALID_REFRESH_TOKEN")
        fun refresh_invalidToken() {
            mockMvc
                .perform(
                    post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":"not-a-real-jwt"}"""),
                )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_REFRESH_TOKEN"))
        }

        // ─────────────── TC-012 + TC-013: 로그아웃 ───────────────

        @Test
        @DisplayName("TC-012: 로그아웃은 204 + 해당 사용자의 Refresh Token 을 Redis 에서 폐기")
        fun logout_revokesAllRefreshTokens() {
            val email = "tc012@example.com"
            val registerBody = register(email)
            val refreshToken = registerBody["data"]["refreshToken"].asText()
            val userId = registerBody["data"]["userId"].asLong()

            mockMvc
                .perform(
                    post("/api/v1/auth/logout")
                        .header("X-User-Id", userId.toString()),
                )
                .andExpect(status().isNoContent)

            assertThat(stringRedisTemplate.hasKey(refreshKey(refreshToken))).isFalse

            // 이후 refresh 시도는 재사용으로 간주되어 REUSED 반환
            mockMvc
                .perform(
                    post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":"$refreshToken"}"""),
                )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_TOKEN_REUSED"))
        }

        @Test
        @DisplayName("TC-013: X-User-Id 없이 /logout 호출 시 401")
        fun logout_withoutUserId() {
            mockMvc
                .perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"))
        }

        // ─────────────── 헬퍼 ───────────────

        private fun register(email: String): JsonNode {
            val result =
                mockMvc
                    .perform(
                        post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """
                                {"email":"$email","password":"P@ssw0rd123!","nickname":"철수"}
                                """.trimIndent(),
                            ),
                    )
                    .andExpect(status().isCreated)
                    .andReturn()
            return objectMapper.readTree(result.response.contentAsString)
        }

        /**
         * TokenStore 의 키 네이밍과 일치해야 한다.
         * 프로덕션 코드의 private 함수를 노출하지 않기 위해 테스트에서 동일 로직을 재구성한다.
         */
        private fun refreshKey(rawToken: String): String {
            val digest =
                java.security.MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())
            val hex = java.util.HexFormat.of().formatHex(digest)
            return "user:refresh:$hex"
        }
    }

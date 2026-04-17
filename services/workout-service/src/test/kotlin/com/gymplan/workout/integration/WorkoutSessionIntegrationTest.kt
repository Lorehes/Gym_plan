package com.gymplan.workout.integration

import com.gymplan.workout.domain.repository.WorkoutSessionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * 운동 세션 통합 테스트.
 *
 * 명세: docs/specs/workout-service.md §테스트 케이스
 * TC-01 ~ TC-12 (전체 흐름 포함)
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkoutSessionIntegrationTest : AbstractIntegrationTest() {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var sessionRepository: WorkoutSessionRepository

    private val userId = 1L
    private val otherUserId = 2L

    @BeforeEach
    fun setUp() {
        sessionRepository.deleteAll()
    }

    // ─────────────────── TC-01: 정상 세션 시작 ───────────────────

    @Test
    @DisplayName("TC-01: 플랜 지정 세션 시작 → 201, IN_PROGRESS, planName 저장")
    fun `TC-01 세션 시작 - 플랜 지정`() {
        mockMvc.post("/api/v1/sessions") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"planId": 12, "planName": "가슴/삼두 루틴"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.status") { value("IN_PROGRESS") }
            jsonPath("$.data.sessionId") { exists() }
            jsonPath("$.data.startedAt") { exists() }
        }

        val session = sessionRepository.findByUserIdAndCompletedAtIsNull(userId.toString())
        assertThat(session).isNotNull
        assertThat(session!!.planName).isEqualTo("가슴/삼두 루틴")
        assertThat(session.completedAt).isNull()
    }

    // ─────────────────── TC-02: 자유 운동 세션 ───────────────────

    @Test
    @DisplayName("TC-02: planId 없는 자유 운동 세션 → planId=null")
    fun `TC-02 자유 운동 세션 시작`() {
        mockMvc.post("/api/v1/sessions") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.status") { value("IN_PROGRESS") }
        }

        val session = sessionRepository.findByUserIdAndCompletedAtIsNull(userId.toString())
        assertThat(session!!.planId).isNull()
    }

    // ─────────────────── TC-03: 중복 세션 차단 ───────────────────

    @Test
    @DisplayName("TC-03: 이미 IN_PROGRESS 세션 있을 때 재시작 → 409 SESSION_ALREADY_ACTIVE")
    fun `TC-03 중복 세션 차단`() {
        // 첫 번째 세션 시작
        mockMvc.post("/api/v1/sessions") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isCreated() } }

        // 두 번째 세션 시작 시도
        mockMvc.post("/api/v1/sessions") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("SESSION_ALREADY_ACTIVE") }
        }

        // DB에 세션 하나만 존재
        assertThat(
            sessionRepository.findByUserId(userId.toString(), org.springframework.data.domain.PageRequest.of(0, 10)).totalElements,
        ).isEqualTo(1L)
    }

    // ─────────────────── TC-04 ~ TC-05: 세트 기록 ───────────────────

    @Nested
    @DisplayName("세트 기록")
    inner class SetRecord {
        private fun startSession(): String {
            val result =
                mockMvc.post("/api/v1/sessions") {
                    header("X-User-Id", userId)
                    contentType = MediaType.APPLICATION_JSON
                    content = "{}"
                }.andReturn()
            val body = com.fasterxml.jackson.databind.ObjectMapper().readTree(result.response.contentAsString)
            return body["data"]["sessionId"].asText()
        }

        @Test
        @DisplayName("TC-04: 새 운동 세트 기록 → exercises 배열에 추가 (전체 문서 교체 아님)")
        fun `TC-04 세트 기록 - 새 운동`() {
            val sessionId = startSession()

            mockMvc.post("/api/v1/sessions/$sessionId/sets") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "exerciseId": "10",
                      "exerciseName": "벤치프레스",
                      "muscleGroup": "CHEST",
                      "setNo": 1,
                      "reps": 10,
                      "weightKg": 70.0,
                      "isSuccess": true
                    }
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.success") { value(true) }
            }

            val session = sessionRepository.findById(sessionId).orElseThrow()
            assertThat(session.exercises).hasSize(1)
            assertThat(session.exercises[0].exerciseId).isEqualTo("10")
            assertThat(session.exercises[0].sets).hasSize(1)
            assertThat(session.exercises[0].sets[0].weightKg).isEqualTo(70.0)
        }

        @Test
        @DisplayName("TC-05: 같은 운동에 세트 추가 → sets 배열에만 append, exercises 크기 유지")
        fun `TC-05 세트 기록 - 기존 운동에 세트 추가`() {
            val sessionId = startSession()

            val setBody =
                """
                {
                  "exerciseId": "10",
                  "exerciseName": "벤치프레스",
                  "muscleGroup": "CHEST",
                  "setNo": %d,
                  "reps": 10,
                  "weightKg": 70.0,
                  "isSuccess": true
                }
                """.trimIndent()

            mockMvc.post("/api/v1/sessions/$sessionId/sets") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = setBody.format(1)
            }.andExpect { status { isCreated() } }

            mockMvc.post("/api/v1/sessions/$sessionId/sets") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = setBody.format(2)
            }.andExpect { status { isCreated() } }

            val session = sessionRepository.findById(sessionId).orElseThrow()
            assertThat(session.exercises).hasSize(1) // exercises 1개 유지
            assertThat(session.exercises[0].sets).hasSize(2) // sets에 2세트
        }

        @Test
        @DisplayName("TC-06: 완료된 세션에 세트 기록 → 409 SESSION_ALREADY_COMPLETED")
        fun `TC-06 완료 세션에 세트 기록 불가`() {
            val sessionId = startSession()

            // 세션 완료
            mockMvc.post("/api/v1/sessions/$sessionId/complete") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isOk() } }

            // 완료 후 세트 기록 시도
            mockMvc.post("/api/v1/sessions/$sessionId/sets") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = """{"exerciseId":"10","exerciseName":"벤치프레스","muscleGroup":"CHEST","setNo":1,"reps":10,"weightKg":70.0}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("SESSION_ALREADY_COMPLETED") }
            }
        }

        @Test
        @DisplayName("TC-07: weightKg ≤ 0 → 400 VALIDATION_FAILED")
        fun `TC-07 세트 기록 - 유효성 검사 실패`() {
            val sessionId = startSession()

            mockMvc.post("/api/v1/sessions/$sessionId/sets") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = """{"exerciseId":"10","exerciseName":"벤치프레스","muscleGroup":"CHEST","setNo":1,"reps":10,"weightKg":0.0}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("VALIDATION_FAILED") }
            }
        }
    }

    // ─────────────────── TC-08: 운동 완료 ───────────────────

    @Nested
    @DisplayName("운동 완료")
    inner class CompleteSession {
        @Test
        @DisplayName("TC-08: 정상 완료 → durationSec / totalVolume / totalSets 반환 + DB 저장")
        fun `TC-08 운동 완료`() {
            // 세션 시작
            val startResult =
                mockMvc.post("/api/v1/sessions") {
                    header("X-User-Id", userId)
                    contentType = MediaType.APPLICATION_JSON
                    content = "{}"
                }.andReturn()
            val sessionId =
                com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(startResult.response.contentAsString)["data"]["sessionId"].asText()

            // 세트 16개 기록 (2종목 × 8세트)
            repeat(8) { i ->
                mockMvc.post("/api/v1/sessions/$sessionId/sets") {
                    header("X-User-Id", userId)
                    contentType = MediaType.APPLICATION_JSON
                    val n = i + 1
                    content = """{"exerciseId":"10","exerciseName":"벤치","muscleGroup":"CHEST","setNo":$n,"reps":10,"weightKg":60.0}"""
                }
                mockMvc.post("/api/v1/sessions/$sessionId/sets") {
                    header("X-User-Id", userId)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"exerciseId":"20","exerciseName":"스쿼트","muscleGroup":"LEGS","setNo":${i + 1},"reps":10,"weightKg":80.0}"""
                }
            }

            // 완료
            mockMvc.post("/api/v1/sessions/$sessionId/complete") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = """{"notes": "오늘 컨디션 좋음"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.sessionId") { value(sessionId) }
                // 총 볼륨 = (60*10 + 80*10) * 8 = 1400 * 8 = 11200
                jsonPath("$.data.totalVolume") { value(11200.0) }
                jsonPath("$.data.totalSets") { value(16) }
                jsonPath("$.data.durationSec") { isNumber() }
            }

            // DB 저장 확인
            val session = sessionRepository.findById(sessionId).orElseThrow()
            assertThat(session.completedAt).isNotNull
            assertThat(session.notes).isEqualTo("오늘 컨디션 좋음")
            assertThat(session.totalVolume).isEqualTo(11200.0)
            assertThat(session.totalSets).isEqualTo(16)
        }

        @Test
        @DisplayName("TC-10: 중복 완료 요청 → 409 SESSION_ALREADY_COMPLETED")
        fun `TC-10 중복 완료 차단`() {
            val startResult =
                mockMvc.post("/api/v1/sessions") {
                    header("X-User-Id", userId)
                    contentType = MediaType.APPLICATION_JSON
                    content = "{}"
                }.andReturn()
            val sessionId =
                com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(startResult.response.contentAsString)["data"]["sessionId"].asText()

            mockMvc.post("/api/v1/sessions/$sessionId/complete") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isOk() } }

            mockMvc.post("/api/v1/sessions/$sessionId/complete") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("SESSION_ALREADY_COMPLETED") }
            }
        }
    }

    // ─────────────────── 공통: 인증 ───────────────────

    @Test
    @DisplayName("X-User-Id 헤더 없으면 401")
    fun `X-User-Id 없으면 401`() {
        mockMvc.post("/api/v1/sessions") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    @DisplayName("진행 중 세션 조회 — 없으면 data: null (404 아님)")
    fun `활성 세션 없을 때 null 반환`() {
        mockMvc.get("/api/v1/sessions/active") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data") { value(null) }
        }
    }

    @Test
    @DisplayName("세트 수정 PUT — 완료 세션이면 409")
    fun `PUT sets - 완료 세션`() {
        val startResult =
            mockMvc.post("/api/v1/sessions") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andReturn()
        val sessionId =
            com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(startResult.response.contentAsString)["data"]["sessionId"].asText()

        mockMvc.post("/api/v1/sessions/$sessionId/sets") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"exerciseId": "10", "exerciseName": "벤치", "muscleGroup": "CHEST", "setNo": 1, "reps": 10, "weightKg": 70.0}"""
        }
        mockMvc.post("/api/v1/sessions/$sessionId/complete") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }

        mockMvc.put("/api/v1/sessions/$sessionId/sets/1/10") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"reps": 12}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("SESSION_ALREADY_COMPLETED") }
        }
    }

    // ─────────────────── TC-12: 타인 세션 접근 불가 ───────────────────

    @Test
    @DisplayName("TC-12: 타인 세션에 세트 기록 시도 → 401 (소유권 노출 방지)")
    fun `TC-12 타인 세션 세트 기록 - 401`() {
        // userId(1)의 세션 생성
        val startResult =
            mockMvc.post("/api/v1/sessions") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andReturn()
        val sessionId =
            com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(startResult.response.contentAsString)["data"]["sessionId"].asText()

        // otherUserId(2)로 userId(1)의 세션에 접근
        mockMvc.post("/api/v1/sessions/$sessionId/sets") {
            header("X-User-Id", otherUserId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"exerciseId":"10","exerciseName":"벤치프레스","muscleGroup":"CHEST","setNo":1,"reps":10,"weightKg":70.0}"""
        }.andExpect {
            status { isUnauthorized() }
        }

        // DB에 세트가 추가되지 않았는지 확인
        val session = sessionRepository.findById(sessionId).orElseThrow()
        assertThat(session.exercises).isEmpty()
    }

    @Test
    @DisplayName("TC-12: 타인 세션 완료 시도 → 401")
    fun `TC-12 타인 세션 완료 - 401`() {
        val startResult =
            mockMvc.post("/api/v1/sessions") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andReturn()
        val sessionId =
            com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(startResult.response.contentAsString)["data"]["sessionId"].asText()

        // otherUserId(2)로 완료 시도
        mockMvc.post("/api/v1/sessions/$sessionId/complete") {
            header("X-User-Id", otherUserId)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isUnauthorized() }
        }

        // 세션이 여전히 IN_PROGRESS 상태인지 확인
        val session = sessionRepository.findById(sessionId).orElseThrow()
        assertThat(session.completedAt).isNull()
    }

    @Test
    @DisplayName("세트 삭제 DELETE — 완료 세션이면 409")
    fun `DELETE sets - 완료 세션`() {
        val startResult =
            mockMvc.post("/api/v1/sessions") {
                header("X-User-Id", userId)
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andReturn()
        val sessionId =
            com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(startResult.response.contentAsString)["data"]["sessionId"].asText()

        mockMvc.post("/api/v1/sessions/$sessionId/sets") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = """{"exerciseId": "10", "exerciseName": "벤치", "muscleGroup": "CHEST", "setNo": 1, "reps": 10, "weightKg": 70.0}"""
        }
        mockMvc.post("/api/v1/sessions/$sessionId/complete") {
            header("X-User-Id", userId)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }

        mockMvc.delete("/api/v1/sessions/$sessionId/sets/1/10") {
            header("X-User-Id", userId)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("SESSION_ALREADY_COMPLETED") }
        }
    }
}

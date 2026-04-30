package com.gymplan.e2e

import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * 시나리오 B — JWT → api-gateway → exercise-catalog 검색
 *              (JWT 검증 + X-User-Id 전달 확인)
 *
 * 검증 항목:
 *   E2E-B-01: 인증 후 운동 종목 목록 조회 → 200, 페이지 응답 구조
 *   E2E-B-02: 인증 후 키워드 검색 → 200 (검색 자체가 응답하는지 확인)
 *   E2E-B-03: Authorization 헤더 없음 → 401 AUTH_INVALID_TOKEN
 *   E2E-B-04: X-User-Id 헤더 직접 주입 (헤더 스푸핑) → 401 AUTH_INVALID_TOKEN
 *
 * 주의: E2E 환경에서 exercise-catalog 는 seeder 없이 기동하므로
 *       목록/검색 결과가 비어 있을 수 있다 (API 응답 구조만 검증).
 */
@Tag("e2e")
@DisplayName("[E2E-B] JWT → api-gateway → exercise-catalog 시나리오")
class ScenarioBTest : AbstractE2ETest() {
    @Nested
    @DisplayName("E2E-B-01: 인증 후 운동 종목 목록 조회 → 200")
    inner class ExerciseListAuthenticated {
        @Test
        fun `유효한 JWT로 운동 종목 목록을 조회하면 200과 페이지 응답 구조가 반환된다`() {
            val user = registerUser("exUserA")

            val response =
                given()
                    .header("Authorization", "Bearer ${user.accessToken}")
                    .get("/api/v1/exercises")
                    .then()
                    .statusCode(200)
                    .extract().response()

            assertThat(response.jsonPath().getBoolean("success")).isTrue()
            // 페이지 응답 구조 확인 (content, totalElements, size 필드)
            assertThat(response.jsonPath().getInt("data.size")).isNotNull()
            assertThat(response.jsonPath().getInt("data.totalElements")).isNotNegative()
            val content: List<*> = response.jsonPath().getList<Any>("data.content")
            assertThat(content).isNotNull()
        }
    }

    @Nested
    @DisplayName("E2E-B-02: 인증 후 키워드 검색 → 200")
    inner class ExerciseSearchAuthenticated {
        @Test
        fun `유효한 JWT로 키워드 검색하면 200이 반환된다`() {
            val user = registerUser("exUserB")

            val response =
                given()
                    .header("Authorization", "Bearer ${user.accessToken}")
                    .queryParam("q", "bench")
                    .get("/api/v1/exercises")
                    .then()
                    .statusCode(200)
                    .extract().response()

            assertThat(response.jsonPath().getBoolean("success")).isTrue()
            // 검색 결과가 없어도 API 자체는 200 을 반환해야 한다
            assertThat(response.jsonPath().getInt("data.totalElements")).isNotNegative()
        }

        @Test
        fun `muscle 파라미터로 필터링하면 200이 반환된다`() {
            val user = registerUser("exUserC")

            val response =
                given()
                    .header("Authorization", "Bearer ${user.accessToken}")
                    .queryParam("muscle", "CHEST")
                    .get("/api/v1/exercises")
                    .then()
                    .statusCode(200)
                    .extract().response()

            assertThat(response.jsonPath().getBoolean("success")).isTrue()
            assertThat(response.jsonPath().getInt("data.totalElements")).isNotNegative()
        }
    }

    @Nested
    @DisplayName("E2E-B-03: Authorization 헤더 없음 → 401")
    inner class NoAuthHeader {
        @Test
        fun `Authorization 헤더 없이 운동 종목을 조회하면 401 AUTH_INVALID_TOKEN이 반환된다`() {
            val response =
                given()
                    .get("/api/v1/exercises")
                    .then()
                    .statusCode(401)
                    .extract().response()

            assertThat(response.jsonPath().getBoolean("success")).isFalse()
            assertThat(response.jsonPath().getString("error.code"))
                .isEqualTo("AUTH_INVALID_TOKEN")
        }
    }

    @Nested
    @DisplayName("E2E-B-04: X-User-Id 직접 주입 (헤더 스푸핑) → 401")
    inner class HeaderSpoofing {
        @Test
        fun `X-User-Id 헤더를 직접 주입하면 JWT 없이도 401 AUTH_INVALID_TOKEN이 반환된다`() {
            val response =
                given()
                    .header("X-User-Id", "1")
                    .get("/api/v1/exercises")
                    .then()
                    .statusCode(401)
                    .extract().response()

            assertThat(response.jsonPath().getBoolean("success")).isFalse()
            assertThat(response.jsonPath().getString("error.code"))
                .isEqualTo("AUTH_INVALID_TOKEN")
        }

        @Test
        fun `유효한 JWT와 함께 X-User-Id 헤더를 주입해도 401 AUTH_INVALID_TOKEN이 반환된다`() {
            val user = registerUser("exUserD")

            val response =
                given()
                    .header("Authorization", "Bearer ${user.accessToken}")
                    .header("X-User-Id", "1") // 스푸핑 시도 — 유효 JWT여도 차단
                    .get("/api/v1/exercises")
                    .then()
                    .statusCode(401)
                    .extract().response()

            assertThat(response.jsonPath().getBoolean("success")).isFalse()
            assertThat(response.jsonPath().getString("error.code"))
                .isEqualTo("AUTH_INVALID_TOKEN")
        }
    }
}

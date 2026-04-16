package com.gymplan.e2e

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.MethodOrderer

/**
 * 시나리오 A — user-service 로그인 → JWT 발급 → api-gateway 통과 → plan-service 루틴 조회
 *
 * 검증 항목:
 *   E2E-A-01: 회원가입 → 빈 루틴 목록 조회 (인증 플로우 전체 확인)
 *   E2E-A-02: 루틴 생성 후 상세 조회 (api-gateway X-User-Id 주입 → plan-service 라우팅)
 *   E2E-A-03: Authorization 헤더 없음 → 401 AUTH_INVALID_TOKEN
 *   E2E-A-04: X-User-Id 헤더 직접 주입 (헤더 스푸핑) → 401 AUTH_INVALID_TOKEN
 */
@Tag("e2e")
@DisplayName("[E2E-A] user-service → api-gateway → plan-service 시나리오")
class ScenarioATest : AbstractE2ETest() {

    @Nested
    @DisplayName("E2E-A-01: 회원가입 → JWT → 루틴 목록 조회 (빈 목록)")
    inner class EmptyPlanList {
        @Test
        fun `회원가입 후 루틴 목록은 빈 배열이다`() {
            val user = registerUser("planUserA")

            val response = given()
                .header("Authorization", "Bearer ${user.accessToken}")
                .get("/api/v1/plans")
                .then()
                .statusCode(200)
                .extract().response()

            assertThat(response.jsonPath().getBoolean("success")).isTrue()
            val plans: List<*> = response.jsonPath().getList<Any>("data")
            assertThat(plans).isEmpty()
        }
    }

    @Nested
    @DisplayName("E2E-A-02: 루틴 생성 → 상세 조회 (X-User-Id 주입 검증)")
    inner class CreateAndGetPlan {
        @Test
        fun `루틴을 생성하면 상세 조회에서 같은 planId와 이름이 반환된다`() {
            val user = registerUser("planUserB")

            // 루틴 생성
            val createBody = mapOf(
                "name"        to "E2E 가슴 루틴",
                "description" to "벤치프레스 위주 루틴",
                "dayOfWeek"   to 1,     // 화요일
            )
            val createResponse = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer ${user.accessToken}")
                .body(createBody)
                .post("/api/v1/plans")
                .then()
                .statusCode(201)
                .extract().response()

            assertThat(createResponse.jsonPath().getBoolean("success")).isTrue()
            val planId = createResponse.jsonPath().getLong("data.planId")
            assertThat(planId).isPositive()

            // 상세 조회
            val getResponse = given()
                .header("Authorization", "Bearer ${user.accessToken}")
                .get("/api/v1/plans/$planId")
                .then()
                .statusCode(200)
                .extract().response()

            assertThat(getResponse.jsonPath().getBoolean("success")).isTrue()
            assertThat(getResponse.jsonPath().getLong("data.planId")).isEqualTo(planId)
            assertThat(getResponse.jsonPath().getString("data.name")).isEqualTo("E2E 가슴 루틴")
        }
    }

    @Nested
    @DisplayName("E2E-A-03: Authorization 헤더 없음 → 401")
    inner class NoAuthHeader {
        @Test
        fun `Authorization 헤더 없이 루틴 목록 조회하면 401 AUTH_INVALID_TOKEN이 반환된다`() {
            val response = given()
                .get("/api/v1/plans")
                .then()
                .statusCode(401)
                .extract().response()

            assertThat(response.jsonPath().getBoolean("success")).isFalse()
            assertThat(response.jsonPath().getString("error.code"))
                .isEqualTo("AUTH_INVALID_TOKEN")
        }
    }

    @Nested
    @DisplayName("E2E-A-04: X-User-Id 직접 주입 (헤더 스푸핑) → 401")
    inner class HeaderSpoofing {
        @Test
        fun `X-User-Id 헤더를 직접 주입하면 JWT 없이도 401 AUTH_INVALID_TOKEN이 반환된다`() {
            val response = given()
                .header("X-User-Id", "999")
                .get("/api/v1/plans")
                .then()
                .statusCode(401)
                .extract().response()

            assertThat(response.jsonPath().getBoolean("success")).isFalse()
            assertThat(response.jsonPath().getString("error.code"))
                .isEqualTo("AUTH_INVALID_TOKEN")
        }

        @Test
        fun `유효한 JWT와 함께 X-User-Id 헤더를 주입해도 401 AUTH_INVALID_TOKEN이 반환된다`() {
            val user = registerUser("planUserC")

            val response = given()
                .header("Authorization", "Bearer ${user.accessToken}")
                .header("X-User-Id", "999")     // 스푸핑 시도 — 유효 JWT여도 차단
                .get("/api/v1/plans")
                .then()
                .statusCode(401)
                .extract().response()

            assertThat(response.jsonPath().getBoolean("success")).isFalse()
            assertThat(response.jsonPath().getString("error.code"))
                .isEqualTo("AUTH_INVALID_TOKEN")
        }
    }
}

package com.gymplan.e2e

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * 시나리오 A — user-service 로그인 → JWT 발급 → api-gateway 통과 → plan-service 루틴 조회
 *
 * 검증 항목:
 *   E2E-A-01: 회원가입 → 빈 루틴 목록 조회 (인증 플로우 전체 확인)
 *   E2E-A-02: 루틴 생성 후 상세 조회 (api-gateway X-User-Id 주입 → plan-service 라우팅)
 *   E2E-A-03: Authorization 헤더 없음 → 401 AUTH_INVALID_TOKEN
 *   E2E-A-04: X-User-Id 헤더 직접 주입 (헤더 스푸핑) → 401 AUTH_INVALID_TOKEN
 *   E2E-A-05: 운동 종목 검색 결과 매칭 (POST 후 q=벤치 검색 → 동일 종목 포함)
 *   E2E-A-06: /plans/today 캐시 응답시간 비교 (cache miss → cache hit 가속 확인)
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

            val response =
                given()
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
            val createBody =
                mapOf(
                    "name" to "E2E 가슴 루틴",
                    "description" to "벤치프레스 위주 루틴",
                    // 화요일
                    "dayOfWeek" to 1,
                )
            val createResponse =
                given()
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
            val getResponse =
                given()
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
            val response =
                given()
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
            val response =
                given()
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

            val response =
                given()
                    .header("Authorization", "Bearer ${user.accessToken}")
                    .header("X-User-Id", "999") // 스푸핑 시도 — 유효 JWT여도 차단
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
    @DisplayName("E2E-A-05: 운동 종목 검색 결과 매칭")
    inner class ExerciseSearchMatching {
        // ES refresh interval (기본 1초) 때문에 POST 직후 GET 결과에 즉시 반영되지
        // 않을 수 있다 → polling.
        private fun pollUntilFound(
            accessToken: String,
            query: String,
            expectedName: String,
            timeoutMs: Long = 10_000L,
            intervalMs: Long = 250L,
        ): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val response =
                    given()
                        .header("Authorization", "Bearer $accessToken")
                        .queryParam("q", query)
                        .get("/api/v1/exercises")
                val names: List<String> =
                    response.jsonPath().getList("data.content.name")
                if (names.contains(expectedName)) return true
                Thread.sleep(intervalMs)
            }
            return false
        }

        @Test
        fun `POST로 등록한 종목은 q=벤치 검색 결과에 포함된다`() {
            val user = registerUser("searchUser")

            // 유니크한 이름으로 시드 (다른 테스트 간섭 방지)
            val seq = uniqueSeq.incrementAndGet()
            val name = "벤치프레스E2E$seq"
            val exerciseId = createCustomExercise(user.accessToken, name = name)
            assertThat(exerciseId).isPositive()

            // ES 인덱싱 대기 후 q=벤치 검색
            val matched = pollUntilFound(user.accessToken, query = "벤치", expectedName = name)
            assertThat(matched)
                .`as`("POST 후 ES에 색인된 종목($name)이 q=벤치 검색 결과에 포함되어야 함")
                .isTrue()
        }
    }

    @Nested
    @DisplayName("E2E-A-06: /plans/today 캐시 응답시간 비교")
    inner class TodayPlanCacheTiming {
        @Test
        fun `두 번째 호출 (캐시 히트) 은 첫 호출 (캐시 미스) 보다 빠르고 SLA 200ms 이내`() {
            val user = registerUser("cacheUser")

            // 오늘 요일에 배정된 루틴 생성 (운동 항목 1개 포함)
            val planId = createPlanForToday(user.accessToken, name = "E2E 오늘 루틴")
            addBenchExercise(user.accessToken, planId)

            // 워밍업 — TCP/HTTP 핸드셰이크 비용을 측정에서 분리
            given().header("Authorization", "Bearer ${user.accessToken}")
                .get("/actuator/health").then().statusCode(200)

            // 첫 호출: cache miss → DB 조회 → Redis SET
            val (firstResp, firstMs) =
                measure {
                    given().header("Authorization", "Bearer ${user.accessToken}")
                        .get("/api/v1/plans/today")
                }
            firstResp.then().statusCode(200)
            val firstPlanId = firstResp.jsonPath().getLong("data.planId")
            assertThat(firstPlanId).isEqualTo(planId)

            // 두 번째 호출: cache hit → Redis GET 만으로 응답
            val (secondResp, secondMs) =
                measure {
                    given().header("Authorization", "Bearer ${user.accessToken}")
                        .get("/api/v1/plans/today")
                }
            secondResp.then().statusCode(200)
            val secondPlanId = secondResp.jsonPath().getLong("data.planId")
            assertThat(secondPlanId).isEqualTo(planId)

            // 캐시 히트가 미스보다 빨라야 함 — 측정값을 메시지에 포함해 진단 용이
            assertThat(secondMs)
                .`as`("캐시 히트(${secondMs}ms)는 캐시 미스(${firstMs}ms)보다 빨라야 함")
                .isLessThanOrEqualTo(firstMs)

            // 캐시 히트는 P95 200ms SLA (CLAUDE.md 성능 목표) 이내여야 함
            assertThat(secondMs)
                .`as`("캐시 히트는 SLA 200ms 이내여야 함 — 실측 ${secondMs}ms")
                .isLessThan(200L)

            // 진단 로그 (테스트 실패 시에도 출력)
            println("[E2E-A-06] cache miss=${firstMs}ms, cache hit=${secondMs}ms")
        }

        private fun <T> measure(block: () -> T): Pair<T, Long> {
            val start = System.nanoTime()
            val result = block()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            return result to elapsed
        }
    }

    companion object {
        // 종목 이름 충돌 방지용 시퀀스 (같은 suite 내에서 유니크)
        private val uniqueSeq = AtomicLong(System.currentTimeMillis())
    }
}

package com.gymplan.e2e

import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.response.Response
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.testcontainers.containers.DockerComposeContainer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

/**
 * E2E 테스트 기반 클래스.
 *
 * Docker Compose 스택을 한 번만 기동하고, 모든 시나리오 클래스에서 공유한다.
 * ComposeContainer 초기화는 Kotlin object (JVM 싱글턴) 로 보장 — 테스트 클래스가
 * 여러 개여도 compose up 은 한 번만 실행된다.
 *
 * RSA 키 쌍은 프로그래밍 방식으로 생성하여 withEnv() 로 주입한다.
 * single-line PEM (개행 없음) 을 사용하는 이유:
 *   JwtProvider.pemToDer() 이 `"\\s".toRegex()` 로 공백을 전부 제거하므로
 *   개행이 없어도 올바르게 파싱된다.
 *
 * docker-compose.e2e.yml 은 api-gateway 를 8080:8080 고정 포트로 노출하므로
 * withExposedService 의 Ambassador 메커니즘을 사용하지 않고 localhost:8080 을
 * 직접 폴링하여 헬스체크를 수행한다.
 */
@Tag("e2e")
abstract class AbstractE2ETest {

    companion object {
        /** E2E 환경 초기화 (compose start) 는 최초 1회만. */
        @JvmStatic
        @BeforeAll
        fun configureRestAssured() {
            RestAssured.baseURI = "http://${E2EStack.gatewayHost}"
            RestAssured.port   = E2EStack.gatewayPort
            RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
        }

        // ── 테스트 헬퍼 ──────────────────────────────────────────────────

        private val userSeq = AtomicLong(System.currentTimeMillis())

        /** 유니크한 이메일을 가진 사용자를 등록하고 accessToken 을 반환한다. */
        fun registerUser(nickname: String = "e2eUser"): RegisteredUser {
            val seq = userSeq.incrementAndGet()
            val email = "e2e_${seq}@gymplan.test"
            val password = "E2ePass1!"

            val body = mapOf(
                "email"    to email,
                "password" to password,
                "nickname" to nickname,
            )

            val response: Response = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/auth/register")
                .then()
                .statusCode(201)
                .extract().response()

            val accessToken = response.jsonPath().getString("data.accessToken")
            val userId      = response.jsonPath().getLong("data.userId")
            return RegisteredUser(userId, email, password, accessToken)
        }

        data class RegisteredUser(
            val userId: Long,
            val email: String,
            val password: String,
            val accessToken: String,
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Kotlin object — JVM 클래스 로딩 시 딱 한 번 초기화되어 compose up 이
// 여러 테스트 클래스에서 중복 실행되지 않음을 보장한다.
// ────────────────────────────────────────────────────────────────────────────
internal object E2EStack {

    // RSA 2048 키 쌍 생성
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA")
        .also { it.initialize(2048) }
        .generateKeyPair()

    /** PKCS#8 DER → single-line PEM (헤더/푸터 포함, 개행 없음) */
    private val privateKeyPem: String =
        "-----BEGIN PRIVATE KEY-----" +
        Base64.getEncoder().encodeToString(keyPair.private.encoded) +
        "-----END PRIVATE KEY-----"

    /** SubjectPublicKeyInfo DER → single-line PEM */
    val publicKeyPem: String =
        "-----BEGIN PUBLIC KEY-----" +
        Base64.getEncoder().encodeToString(keyPair.public.encoded) +
        "-----END PUBLIC KEY-----"

    // ── Docker Compose 컨테이너 ───────────────────────────────────────────

    private val composeFile: File =
        File("../infra/docker-compose/docker-compose.e2e.yml").also {
            check(it.exists()) {
                "E2E compose 파일을 찾을 수 없습니다: ${it.absolutePath}" +
                " — e2e 모듈 루트에서 실행 중인지 확인하세요."
            }
        }

    private val compose: DockerComposeContainer<*> =
        DockerComposeContainer<Nothing>(composeFile).apply {
            withLocalCompose(true)
            // 테스트용 RSA 키 주입 (single-line PEM)
            withEnv("JWT_PRIVATE_KEY", privateKeyPem)
            withEnv("JWT_PUBLIC_KEY",  publicKeyPem)
            // 인프라 크레덴셜 (테스트 전용 고정값)
            withEnv("MYSQL_ROOT_PASSWORD", "e2eroot")
            withEnv("MYSQL_DATABASE",      "gymplan_user")
            withEnv("MYSQL_USER",          "gymplan")
            withEnv("MYSQL_PASSWORD",      "e2epw")
            withEnv("REDIS_PASSWORD",      "e2eredis")
        }

    // docker-compose.e2e.yml 이 8080:8080 고정 포트로 노출하므로 localhost:8080 직접 사용
    val gatewayHost: String = "localhost"
    val gatewayPort: Int = 8080

    // exercise-catalog 의 actuator health 포트 (docker-compose.e2e.yml 에서 호스트로 노출)
    private val exerciseCatalogPort: Int = 8083

    init {
        compose.start()
        // api-gateway 가 최대 5분 내에 HTTP 200 을 반환할 때까지 폴링
        waitForGateway(timeoutMs = 300_000L)
        // exercise-catalog 는 api-gateway 보다 늦게 뜰 수 있으므로 별도 폴링
        // port 8083 은 docker-compose.e2e.yml 에서 호스트로 직접 노출됨
        waitForDownstream(
            url          = "http://localhost:$exerciseCatalogPort/actuator/health",
            expectedCode = 200,
            timeoutMs    = 120_000L,
            label        = "exercise-catalog",
        )
    }

    /**
     * GET http://localhost:8080/actuator/health 가 200 을 반환할 때까지 폴링한다.
     * withExposedService Ambassador 를 사용하지 않으므로 "not running" 오류를 회피한다.
     */
    private fun waitForGateway(timeoutMs: Long) {
        val url = "http://localhost:$gatewayPort/actuator/health"
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastException: Exception? = null

        while (System.currentTimeMillis() < deadline) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 2_000
                conn.readTimeout    = 2_000
                conn.requestMethod  = "GET"
                val code = conn.responseCode
                conn.disconnect()
                if (code == 200) return
            } catch (e: Exception) {
                lastException = e
            }
            Thread.sleep(3_000)
        }
        throw IllegalStateException(
            "api-gateway 헬스체크 타임아웃 (${timeoutMs}ms): $url — ${lastException?.message}",
        )
    }

    /**
     * 다운스트림 서비스가 준비될 때까지 게이트웨이를 통해 폴링한다.
     * [expectedCode] 가 반환되면 해당 서비스가 정상 응답 중임을 의미한다.
     * 500/502/503 은 서비스 기동 중으로 간주하고 재시도한다.
     */
    private fun waitForDownstream(
        url: String,
        expectedCode: Int,
        timeoutMs: Long,
        label: String,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastCode = -1

        while (System.currentTimeMillis() < deadline) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 2_000
                conn.readTimeout    = 2_000
                conn.requestMethod  = "GET"
                lastCode = conn.responseCode
                conn.disconnect()
                if (lastCode == expectedCode) return
            } catch (_: Exception) { }
            Thread.sleep(3_000)
        }
        throw IllegalStateException(
            "$label 준비 타임아웃 (${timeoutMs}ms): $url — 마지막 응답 코드: $lastCode",
        )
    }
}

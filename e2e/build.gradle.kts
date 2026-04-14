// ─────────────────────────────────────────────────────────────
// E2E 테스트 모듈
//
// 실행 방법:
//   1. ./scripts/gen-e2e-keys.sh          → .env.e2e 생성
//   2. ./gradlew :services:*:bootJar      → JAR 빌드
//   3. ./gradlew :e2e:test                → 테스트 (ComposeContainer 자동 기동)
//
// @Tag("e2e") 로 단독 실행 가능:
//   ./gradlew :e2e:test -Dgroups=e2e
// ─────────────────────────────────────────────────────────────

plugins {
    kotlin("jvm")
}

dependencies {
    // Testcontainers — Docker Compose 기반 E2E 환경 관리
    testImplementation("org.testcontainers:testcontainers:1.20.4")

    // HTTP 클라이언트 — REST API 검증
    testImplementation("io.rest-assured:rest-assured:5.5.0")
    testImplementation("io.rest-assured:kotlin-extensions:5.5.0")

    // JSON 파싱
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")

    // 로깅
    testImplementation("org.slf4j:slf4j-api")
    testRuntimeOnly("ch.qos.logback:logback-classic")
}

tasks.test {
    useJUnitPlatform()

    // E2E 테스트는 서비스 컨테이너 기동이 필요해 시간이 걸림
    timeout.set(java.time.Duration.ofMinutes(15))

    // 컨테이너 로그를 테스트 출력에서 보이게 하려면 아래 주석 해제
    // testLogging { events("passed", "skipped", "failed") }
}

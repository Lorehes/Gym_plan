// ───── api-gateway: 라우팅, JWT 검증 필터, Rate Limiting ─────
// Spring Cloud Gateway (Reactive 기반).
// JWT 검증 → X-User-Id 헤더 주입은 common-security 의 JwtAuthenticationWebFilter 가 담당.
plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common:common-dto"))
    implementation(project(":common:common-exception"))
    implementation(project(":common:common-security"))

    // Spring Cloud Gateway (reactive)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")

    // Reactive Redis (Rate Limit 백엔드)
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Actuator (헬스체크, 메트릭)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:junit-jupiter")
}

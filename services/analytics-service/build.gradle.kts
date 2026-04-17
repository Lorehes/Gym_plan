// ───── analytics-service: Kafka Consumer + ES 색인 + 통계 집계 (Java) ─────
// CLAUDE.md: 이 서비스만 Java 로 작성합니다.
plugins {
    java
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common:common-dto"))
    implementation(project(":common:common-exception"))
    implementation(project(":common:common-security"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:elasticsearch")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:junit-jupiter")
}

// Testcontainers BOM override: Spring Boot 3.3.5 고정값(1.19.8)이 macOS Docker Desktop 4.68+ 와
// API 버전 협상 실패("client version 1.32 too old")하므로 1.20.4로 오버라이드
dependencyManagement {
    imports { mavenBom("org.testcontainers:testcontainers-bom:1.20.4") }
}

tasks.withType<Test>().configureEach {
    systemProperty("api.version", "1.43")
    environment("DOCKER_API_VERSION", "1.43")
}

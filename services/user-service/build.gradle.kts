// ───── user-service: 회원가입, 로그인, JWT 발급/갱신 ─────
plugins {
    kotlin("plugin.jpa")
    id("org.springframework.boot")
}

// Spring Boot 3.3.5 BOM 은 Testcontainers 1.19.8 을 고정한다.
// 해당 버전의 docker-java 3.3.6 는 macOS Docker Desktop 4.68+ 와 API 협상 이슈가 있어 /info 요청이
// 400 으로 떨어지므로, 더 새로운 1.20.4 (docker-java 3.4.x) 로 BOM 을 오버라이드한다.
dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

tasks.withType<Test>().configureEach {
    // docker-java 의 기본 API 버전(1.32) 은 Docker Desktop 최신 빌드가 거부하므로 명시적으로 고정한다.
    // Testcontainers 는 api.version 시스템 프로퍼티로 읽는다.
    systemProperty("api.version", "1.43")
    environment("DOCKER_API_VERSION", "1.43")
}

dependencies {
    implementation(project(":common:common-dto"))
    implementation(project(":common:common-exception"))
    implementation(project(":common:common-security"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // JWT (RS256) — common-security 에서 사용하는 것과 같은 버전
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // DB
    runtimeOnly("com.mysql:mysql-connector-j")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:junit-jupiter")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

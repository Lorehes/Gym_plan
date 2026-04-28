// ───── plan-service: 루틴 계획 CRUD, 오늘의 루틴 (Redis Cache-Aside) ─────
plugins {
    kotlin("plugin.jpa")
    id("org.springframework.boot")
}

// Spring Boot 3.3.5 BOM 은 Testcontainers 1.19.8 을 고정한다.
// docker-java 3.3.6(1.19.8) 은 macOS Docker Desktop 4.68+ 와 API 협상 이슈가 있어
// /info 요청이 400 으로 떨어지므로 1.20.4 (docker-java 3.4.x) 로 BOM 을 오버라이드한다.
dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

tasks.withType<Test>().configureEach {
    // docker-java 의 기본 API 버전(1.32) 은 Docker Desktop 최신 빌드가 거부하므로 명시적으로 고정한다.
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
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // DB
    runtimeOnly("com.mysql:mysql-connector-j")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:junit-jupiter")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

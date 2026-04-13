// ───── exercise-catalog: 운동 종목 CRUD + Elasticsearch 검색 (nori) ─────
plugins {
    kotlin("plugin.jpa")
    id("org.springframework.boot")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

tasks.withType<Test>().configureEach {
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
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

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
    testImplementation("org.testcontainers:elasticsearch")
    testImplementation("org.testcontainers:junit-jupiter")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

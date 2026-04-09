// ───── api-gateway: 라우팅, JWT 검증 필터, Rate Limiting ─────
// Spring Cloud Gateway (Reactive 기반).
// JWT 검증 → X-User-Id 헤더 주입은 common-security 의 JwtAuthenticationWebFilter 가 담당.
plugins {
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
    systemProperty("api.version", "1.43")
    environment("DOCKER_API_VERSION", "1.43")
}

dependencies {
    implementation(project(":common:common-dto"))
    implementation(project(":common:common-exception"))
    implementation(project(":common:common-security")) {
        // common-security 가 spring-boot-starter-web 을 가져오면
        // Spring Cloud Gateway (WebFlux) 와 충돌한다 (MvcFoundOnClasspathException).
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
        // Gateway 는 자체 JWT 필터(GlobalFilter)로 인증을 처리하므로
        // Spring Security filter chain 이 필요 없다.
        // Security 가 classpath 에 있으면 모든 요청을 차단하므로 제외한다.
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-security")
    }

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

// ───── common-security: JWT (RS256) Provider, Spring Security 공통 설정 ─────
plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":common:common-exception"))

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // WebFilter (Gateway 용) 는 Reactive 환경. 라이브러리 자체에 강제 의존하지 않도록 compileOnly.
    compileOnly("org.springframework.boot:spring-boot-starter-webflux")

    // JWT (RS256)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

// ───── common-dto: API 공통 응답/요청 DTO 라이브러리 ─────
plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("jakarta.validation:jakarta.validation-api")

    // Spring Data Page → PageResponse 변환 헬퍼용 (다운스트림 서비스가 spring-data 를 이미 가져옴)
    compileOnly("org.springframework.data:spring-data-commons")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.data:spring-data-commons")
}

// 라이브러리 모듈: 실행 가능 jar 비활성화, 일반 jar 활성화
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

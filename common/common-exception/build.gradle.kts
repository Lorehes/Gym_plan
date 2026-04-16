// ───── common-exception: 공통 예외 계층 + GlobalExceptionHandler ─────
plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":common:common-dto"))

    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

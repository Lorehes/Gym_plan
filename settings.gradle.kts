rootProject.name = "gymplan"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// ───── Common modules ─────
include("common:common-dto")
include("common:common-exception")
include("common:common-security")

// ───── Service modules ─────
include("services:api-gateway")
include("services:user-service")
include("services:plan-service")
include("services:exercise-catalog")
include("services:workout-service")
include("services:analytics-service")
include("services:notification-service")

// ───── E2E 테스트 ─────
include("e2e")

package com.gymplan.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * api-gateway 엔트리 포인트.
 *
 * 포트: 8080 (외부 진입점)
 *
 * 책임 (docs/architecture/services.md §api-gateway):
 *   - 각 서비스로의 라우팅 (Spring Cloud Gateway / Reactive)
 *   - JWT(RS256) 검증 및 X-User-Id / X-User-Email 헤더 주입
 *   - Redis 기반 Rate Limiting (IP / User)
 *   - 에러 응답을 프로젝트 표준 ApiResponse 봉투로 변환
 *
 * 보안 가이드 (docs/context/security-guide.md):
 *   - 외부에서 X-User-* 헤더를 직접 주입하면 즉시 차단
 *   - 키/시크릿은 Vault 환경변수로만 주입 (application.yml 하드코딩 금지)
 *
 * 컴포넌트 스캔 범위 — common-exception 의 핸들러는 WebMvc 전용이라
 * 의도적으로 포함하지 않는다. Reactive 환경에서는 GatewayErrorHandler 가 대체한다.
 */
@SpringBootApplication
class ApiGatewayApplication

fun main(args: Array<String>) {
    runApplication<ApiGatewayApplication>(*args)
}

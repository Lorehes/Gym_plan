# 시스템 아키텍처

## 전체 구성도

```
┌─────────────────────────────────────────────────────────────┐
│                      Client Apps                             │
│            Mobile (React Native) / Web (React)               │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTPS
┌───────────────────────────▼─────────────────────────────────┐
│          Istio Ingress Gateway (TLS 종료)                     │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│         API Gateway (Spring Cloud Gateway :8080)             │
│    JWT 검증 → X-User-Id 헤더 주입 / Rate Limiting           │
└──┬──────────┬──────────┬──────────┬──────────┬──────────────┘
   │          │          │          │          │
:8081      :8082      :8083      :8084      :8085/:8086
user       plan     exercise   workout  analytics/notification
service   service   catalog    service      services
   │          │          │          │          │
MySQL      MySQL    MySQL+ES   MongoDB      ES+Redis
+Redis     +Redis
                              │
                         ┌────▼────┐
                         │  Kafka  │
                         └────┬────┘
                              │ (이벤트 소비)
                    analytics-service
                    notification-service
```

## 서비스 간 통신

### 동기 (REST over HTTP/2)
- 모든 서비스 간 통신은 Istio mTLS로 암호화
- Gateway → 하위 서비스: X-User-Id 헤더로 인증 정보 전달
- 서비스 간 직접 호출 최소화 (Kafka 이벤트 우선)

### 비동기 (Kafka)
| 토픽 | 발행 | 소비 |
|------|------|------|
| `workout.session.completed` | workout-service | analytics, notification |
| `workout.set.logged` | workout-service | analytics |
| `user.registered` | user-service | notification |
| `plan.shared` | plan-service | analytics |

## Kubernetes 네임스페이스

| 네임스페이스 | 구성 요소 |
|-------------|----------|
| `gymplan-prod` | 6개 서비스 Deployment + HPA |
| `gymplan-infra` | MySQL, MongoDB, Redis, ES, Kafka StatefulSet |
| `istio-system` | Istio Control Plane, Ingress Gateway |
| `monitoring` | Prometheus, Thanos, Grafana |
| `argocd` | ArgoCD |

## 각 서비스 패키지 구조 (공통)

```
{service}/
├── src/main/kotlin/com/gymplan/{service}/
│   ├── domain/              ← 엔티티, 도메인 모델, 리포지토리 인터페이스
│   ├── application/         ← 유스케이스, 서비스, DTO
│   ├── infrastructure/      ← JPA 구현체, 외부 시스템 연동
│   └── presentation/        ← Controller, Request/Response
├── src/main/resources/
│   ├── application.yml
│   └── application-local.yml
└── src/test/
    ├── unit/
    └── integration/
```

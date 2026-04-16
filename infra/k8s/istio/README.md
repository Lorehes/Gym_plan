# Istio 설정 — GymPlan

GymPlan 서비스 메시의 mTLS, 라우팅, 트래픽 정책 설정 파일 모음.

## 파일 구성

| 파일 | 리소스 | 역할 |
|------|--------|------|
| `peer-authentication.yaml` | PeerAuthentication | 네임스페이스 전체 mTLS STRICT 강제 |
| `gateway.yaml` | Gateway + VirtualService | 외부 HTTPS → api-gateway 진입점 |
| `destination-rules.yaml` | DestinationRule × 7 | 서비스별 ISTIO_MUTUAL + 서킷 브레이커 |
| `virtual-services.yaml` | VirtualService × 7 | 내부 라우팅, 타임아웃, 재시도 정책 |

## 전제조건

```bash
# 1. Istio 설치 (1.19+)
istioctl install --set profile=default -y

# 2. gymplan-prod 네임스페이스 사이드카 인젝션 활성화
kubectl create namespace gymplan-prod
kubectl label namespace gymplan-prod istio-injection=enabled

# 3. gymplan-infra 네임스페이스 (DB, Redis, Kafka)
kubectl create namespace gymplan-infra

# 4. TLS 인증서 생성 (cert-manager 사용 권장)
#    수동 생성 시:
kubectl create secret tls gymplan-tls-cert \
  --cert=fullchain.pem \
  --key=privkey.pem \
  -n istio-system
```

## 적용 순서

mTLS를 먼저 적용한 후 트래픽 정책을 순서대로 적용해야 합니다.

```bash
# 1. PeerAuthentication — mTLS 강제 (가장 먼저)
kubectl apply -f peer-authentication.yaml

# 2. DestinationRule — 클라이언트 측 ISTIO_MUTUAL 설정
#    PeerAuthentication과 DestinationRule이 모두 있어야 mTLS가 완성됨
kubectl apply -f destination-rules.yaml

# 3. Gateway — 외부 진입점
kubectl apply -f gateway.yaml

# 4. VirtualService — 라우팅/타임아웃/재시도
kubectl apply -f virtual-services.yaml
```

한 번에 적용:
```bash
kubectl apply -f infra/k8s/istio/
```

## mTLS 동작 원리

```
[외부 클라이언트]
      │ HTTPS (TLS SIMPLE, cert: gymplan-tls-cert)
      ▼
[Istio Ingress Gateway]  ← Gateway 리소스가 TLS 종료 담당
      │ mTLS (ISTIO_MUTUAL)  ← PeerAuthentication STRICT
      ▼                         DestinationRule ISTIO_MUTUAL
[api-gateway sidecar] ──────────────────────────────────────
      │ plain HTTP (같은 Pod 내부)
      ▼
[api-gateway app :8080]
      │ mTLS (내부 서비스 간)
      ▼
[plan-service sidecar] → [plan-service app :8082]
```

### PeerAuthentication vs DestinationRule

| 리소스 | 관점 | 동작 |
|--------|------|------|
| PeerAuthentication | **수신자** 관점 | "나한테 올 때는 mTLS만 받겠다" |
| DestinationRule | **발신자** 관점 | "저 서비스에 보낼 때는 mTLS를 쓴다" |

둘 다 설정해야 양방향 mTLS가 완성됩니다.

## 서킷 브레이커 동작

`destination-rules.yaml`의 `outlierDetection` 설정:

```
연속 5회 5xx 응답 → 해당 Pod를 30초간 로드밸런서에서 제외
10초 주기로 에러율 집계
최대 50%의 Pod만 동시에 제외 가능 (나머지 50%는 항상 서비스)
```

제외된 Pod는 30초 후 자동으로 복구 시도됩니다.

## 타임아웃 설정 근거

`docs/context/performance-goals.md` SLA 기반:

| 서비스 | 엔드포인트 | P95 목표 | VirtualService timeout |
|--------|-----------|---------|----------------------|
| plan-service | GET /today | < 200ms | 2s |
| workout-service | POST /sets | < 300ms | 3s |
| exercise-catalog | GET /search | < 500ms | 5s |
| 기타 | - | - | 5s |

## 재시도 정책

쓰기 요청(POST/PUT/DELETE)과 읽기 요청(GET)을 다르게 처리합니다:

```yaml
# 읽기 (GET) — 5xx에도 재시도 안전
retries:
  attempts: 3
  retryOn: connect-failure,reset,5xx

# 쓰기 (POST/PUT/DELETE) — 멱등성 없는 요청, 연결 실패만 재시도
retries:
  attempts: 2
  retryOn: connect-failure,reset
```

## 카나리 배포 (향후)

현재 모든 DestinationRule에 `subset: v1`만 정의되어 있습니다.
카나리 배포 시 다음과 같이 확장합니다:

```yaml
# destination-rules.yaml에 v2 subset 추가
subsets:
  - name: v1
    labels:
      version: v1
  - name: v2
    labels:
      version: v2

# virtual-services.yaml에서 가중치 분할
route:
  - destination:
      host: plan-service
      subset: v1
    weight: 90
  - destination:
      host: plan-service
      subset: v2
    weight: 10   # 10% 카나리
```

## 검증

```bash
# mTLS 적용 확인
istioctl authn tls-check <pod-name>.<namespace> plan-service.gymplan-prod.svc.cluster.local

# Kiali에서 서비스 메시 시각화 (Istio addon)
istioctl dashboard kiali

# VirtualService 적용 확인
kubectl get virtualservice -n gymplan-prod
kubectl get destinationrule -n gymplan-prod
kubectl get peerauthentication -n gymplan-prod
```

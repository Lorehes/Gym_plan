# scripts/

## seed-dummy-data.py

운영 화면 검증용 더미 데이터를 생성하는 스크립트.

### 전제 조건

- 모든 서비스 실행 중 (`api-gateway:8080`, `user-service:8081`, `plan-service:8082`, `exercise-catalog:8083`, `workout-service:8084`, `analytics-service:8085`)
- Docker 컨테이너 실행 중 (`gymplan-mongodb`, `gymplan-kafka`, `gymplan-elasticsearch`)
- `python3`과 `requests` 패키지 설치: `pip3 install requests`

### 실행

```bash
# 첫 실행
python3 scripts/seed-dummy-data.py --i-know-this-is-dev

# 재실행 (기존 데이터 초기화 후 재생성)
python3 scripts/seed-dummy-data.py --reset --i-know-this-is-dev
```

`--i-know-this-is-dev` 플래그 없이는 `localhost` 환경에서만 실행됩니다 (운영 환경 실수 방지).

### 생성 데이터

| 항목 | 내용 |
|------|------|
| 계정 | `demo@gymplan.test` / `DemoUser2026!` |
| 루틴 | 4개 (월/화/목/금) |
| 루틴 운동 | 21개 (루틴당 5~6종목) |
| 세션 | 14~20개 (30일 × 75% 출석률) |
| 세트 | 세션당 14~20세트 |
| 기간 | 지난 30일 (역사적 타임스탬프 패치) |

#### 루틴 구성

| 요일 | 루틴 | 주요 종목 |
|------|------|-----------|
| 월 (0) | 가슴/삼두 | 바벨 벤치프레스, 인클라인, 덤벨 플라이, 스컬 크러셔, 케이블 푸시다운 |
| 화 (1) | 등/이두 | 바벨 데드리프트, 바벨 로우, 풀업, 시티드 케이블 로우, 바벨 컬 |
| 목 (3) | 어깨/하체 | 바벨 백스쿼트, 레그 프레스, 덤벨 숄더 프레스, 레터럴 레이즈, 레그 컬 |
| 금 (4) | 전신 | 바벨 스쿼트, 벤치프레스, 데드리프트, 풀업, 숄더 프레스, 플랭크 |

### 리셋 (`--reset`) 시 삭제 범위

1. MySQL `workout_plans` — 데모 사용자의 모든 루틴
2. MongoDB `workout_sessions` — 데모 사용자의 모든 세션
3. Elasticsearch `gymplan-sessions-*`, `gymplan-sets-*`, `gymplan-personal-records` — 데모 사용자 문서

### 알려진 제약

- **세션 duration**: `startedAt`/`completedAt`을 MongoDB에 직접 패치하지만, Kafka 이벤트의 `durationSec`은 실제 API 호출 시간(수 초)으로 기록됩니다. Analytics 화면의 평균 운동 시간은 실제 값을 반영하지 않습니다.
- **맨몸 운동 무게**: 풀업/플랭크 등 맨몸 운동은 체중 기본값 70kg으로 기록됩니다.

### 서비스별 local 프로파일 설정 변경 사항

시드 스크립트 실행을 위해 아래 `application-local.yml` 파일이 수정됐습니다:

| 파일 | 변경 내용 |
|------|-----------|
| `services/api-gateway/.../application-local.yml` | `gymplan.gateway.rate-limit.enabled: false` |
| `services/workout-service/.../application-local.yml` | `spring.kafka.bootstrap-servers: localhost:9094` |
| `services/analytics-service/.../application-local.yml` | `spring.kafka.bootstrap-servers: localhost:9094` |

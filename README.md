# GymPlan

운동 계획 & 실행 앱. 체육관 이용자가 루틴을 미리 설계하고, 현장에서 모바일로 실시간 실행·기록합니다.

## 빠른 시작

### 사전 요구사항
- Java 21+
- Docker & Docker Compose
- Node.js 20+
- Kotlin 1.9+

### 로컬 환경 실행

```bash
# 1. 인프라 실행 (MySQL, MongoDB, Redis, Elasticsearch, Kafka)
cd infra/docker-compose
docker compose -f docker-compose.local.yml up -d

# 2. 특정 서비스 실행
cd services/user-service
./gradlew bootRun

# 3. 전체 서비스 실행
./gradlew bootRun --parallel
```

### 서비스 엔드포인트 (로컬)

| 서비스 | URL |
|--------|-----|
| API Gateway | http://localhost:8080 |
| User Service | http://localhost:8081 |
| Plan Service | http://localhost:8082 |
| Exercise Catalog | http://localhost:8083 |
| Workout Service | http://localhost:8084 |
| Analytics Service | http://localhost:8085 |
| Notification Service | http://localhost:8086 |
| Kafka UI | http://localhost:9000 |
| Elasticsearch | http://localhost:9200 |

## 프로젝트 구조

```
gymplan/
├── CLAUDE.md                 ← Claude Code 컨텍스트 (최우선)
├── README.md                 ← 이 파일
├── services/                 ← 마이크로서비스
├── common/                   ← 공통 모듈
├── infra/                    ← 인프라 설정
├── clients/                  ← 클라이언트 앱
└── docs/                     ← 프로젝트 문서
```

## 상세 문서

- **전체 컨텍스트**: [CLAUDE.md](./CLAUDE.md)
- **아키텍처**: [docs/architecture/](./docs/architecture/)
- **API 명세**: [docs/api/](./docs/api/)
- **DB 스키마**: [docs/database/](./docs/database/)
- **Phase 계획**: [docs/planning/phase-plan.md](./docs/planning/phase-plan.md)
- **스킬 가이드**: [docs/context/skill-guide.md](./docs/context/skill-guide.md)

## 개발 가이드

### 새 기능 구현
```bash
git worktree add ../gymplan-{기능명} -b feature/{기능명}
cd ../gymplan-{기능명}
claude
# > spec-writer 스킬로 명세 작성해줘
# > feature-lead 스킬로 구현해줘
```

### 커밋 컨벤션
```
feat:     새로운 기능
fix:      버그 수정
refactor: 코드 리팩토링
perf:     성능 개선
test:     테스트
docs:     문서
chore:    빌드/설정
```

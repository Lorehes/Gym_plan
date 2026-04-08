# GymPlan — CLAUDE.md

> **모든 스킬과 에이전트는 작업 시작 전 이 파일을 반드시 먼저 읽으세요.**
> 이 파일이 프로젝트의 모든 규칙과 컨텍스트의 최우선 기준입니다.

---

## 프로젝트 개요

| 항목 | 내용 |
|------|------|
| **프로젝트명** | GymPlan |
| **목적** | 체육관 운동을 사전에 계획하고, 현장에서 실시간 실행·기록하는 앱 |
| **개발 방식** | Vibe Coding + Harness Engineering |
| **팀 구성** | 1인 개발 |
| **총 기간** | 약 11주 (Phase 0~4) |

### 핵심 사용자 흐름
1. 집/사무실에서 **웹 앱**으로 이번 주 루틴을 미리 계획
2. 체육관 입장 후 **모바일 앱**에서 오늘의 루틴을 1초 내 조회
3. 운동 중 세트/무게/횟수를 한 손으로 체크
4. 세트 완료 시 휴식 타이머 자동 실행
5. 운동 완료 후 통계 자동 저장

---

## 스킬 위치

모든 작업 시작 전 해당 스킬 파일을 읽고 역할에 맞게 행동하세요.

```
planner:                  .claude/skills/planner.md
spec-writer:              .claude/skills/spec-writer.md
plan:                     .claude/skills/plan.md
feature-lead:             .claude/skills/feature-lead.md
backend-developer:        .claude/skills/backend-developer.md
frontend-developer:       .claude/skills/frontend-developer.md
designer:                 .claude/skills/designer.md
performance-engineer:     .claude/skills/performance-engineer.md
qa-lead:                  .claude/skills/qa-lead.md
security-lead:            .claude/skills/security-lead.md
code-improvement-advisor: .claude/skills/code-improvement-advisor.md
tech-lead:                .claude/skills/tech-lead.md
project-manager:          .claude/skills/project-manager.md
```

### 스킬 호출 워크플로우
```
새 기능 구현
  git worktree add → spec-writer → feature-lead → tech-lead → project-manager

단순 수정/버그픽스
  backend-developer 또는 frontend-developer 직접 호출

성능 이슈
  performance-engineer 직접 호출

보안 감사
  security-lead 직접 호출

코드 품질 리뷰
  code-improvement-advisor 직접 호출
```

---

## 기술 스택

### 백엔드
```
언어:        Kotlin (주), Java (analytics-service)
프레임워크:  Spring Boot 3.x
게이트웨이:  Spring Cloud Gateway
ORM:         Spring Data JPA + Hibernate
보안:        Spring Security + JWT (RS256)
배치:        Spring Batch
빌드:        Gradle (Kotlin DSL, 멀티모듈)
```

### 데이터베이스 & 메시징
```
MySQL 8.x          → 사용자, 루틴, 운동 종목 (영구 저장)
Redis 7.x          → 세션/토큰, 캐시, Rate Limit, pub-sub
MongoDB 7.x        → 운동 세션 기록 (유연한 세트 구조)
Elasticsearch 8.x  → 운동 종목 검색, 통계 집계
Apache Kafka       → 서비스 간 비동기 이벤트
```

### 인프라 & DevOps
```
Kubernetes + Istio  → 오케스트레이션, 서비스 메시, mTLS
Podman              → 컨테이너 이미지 빌드
Harness CI          → 빌드, 테스트, 이미지 빌드 자동화
GoCD                → 파이프라인 오케스트레이션, E2E 게이트
ArgoCD              → GitOps 기반 K8s 배포
Vault               → 시크릿 런타임 주입
Prometheus + Thanos → 메트릭 수집 및 장기 보존
Grafana             → 대시보드 및 알람
```

### 클라이언트
```
모바일: React Native (Expo) — 체육관 실행 모드
웹:     React + Vite + TypeScript — 계획 수립 모드
```

---

## 프로젝트 구조

```
gymplan/
├── CLAUDE.md                     ← 이 파일 (최우선 참조)
├── README.md
├── build.gradle.kts              ← Root Gradle
├── settings.gradle.kts
├── services/
│   ├── user-service/
│   ├── plan-service/
│   ├── exercise-catalog/
│   ├── workout-service/
│   ├── analytics-service/
│   └── notification-service/
├── common/
│   ├── common-dto/
│   ├── common-exception/
│   └── common-security/
├── infra/
│   ├── k8s/
│   ├── helm/
│   └── docker-compose/
├── clients/
│   ├── mobile/                   ← React Native
│   └── web/                      ← React + Vite
└── docs/
    ├── architecture/             ← 아키텍처 문서
    ├── api/                      ← API 명세
    ├── database/                 ← DB 스키마
    ├── specs/                    ← 기능 명세 (spec-writer 산출물)
    ├── planning/                 ← 기획서 (planner 산출물)
    └── context/                  ← 프로젝트 배경
```

---

## 서비스 구성

| 서비스 | 역할 | 포트 | DB |
|--------|------|------|----|
| api-gateway | 라우팅, 인증 필터, Rate Limit | 8080 | - |
| user-service | 회원가입, 로그인, JWT | 8081 | MySQL, Redis |
| plan-service | 루틴 계획 CRUD, 오늘의 루틴 | 8082 | MySQL, Redis |
| exercise-catalog | 운동 종목 관리 및 검색 | 8083 | MySQL, Elasticsearch |
| workout-service | 세션 실행, 세트 기록 | 8084 | MongoDB |
| analytics-service | 통계 집계 | 8085 | Elasticsearch |
| notification-service | 타이머, 푸시 알림 | 8086 | Redis |

---

## 코딩 컨벤션

### 공통
- 커밋: Conventional Commits (`feat/fix/refactor/perf/test/docs/chore`)
- 브랜치: `feature/*`, `fix/*`, `refactor/*`, `perf/*`
- **feature-lead는 반드시 Git Worktree 환경에서만 실행**

### 백엔드 (Kotlin/Java)
- 코드 스타일: ktlint
- 패키지 구조: `domain / application / infrastructure / presentation`
- API 공통 응답: `{ success, data, error, timestamp }`
- 예외: 공통 예외 계층 사용 (`common-exception` 모듈)
- JWT: RS256 알고리즘, Access 30분 / Refresh 7일

### 프론트엔드
- 상태 관리: React Query (서버 상태) + Zustand (클라이언트 상태)
- 스타일: 모바일은 StyleSheet API, 웹은 Tailwind CSS
- 터치 타겟: 최소 48dp (모바일)

---

## 문서 위치 (상세)

| 문서 | 경로 |
|------|------|
| 전체 기술 명세 요약 | `docs/context/project-spec.md` |
| 프로젝트 배경/의사결정 | `docs/context/project-context.md` |
| 시스템 아키텍처 | `docs/architecture/system-architecture.md` |
| 서비스별 아키텍처 상세 | `docs/architecture/services.md` |
| Kafka 이벤트 명세 | `docs/architecture/kafka-events.md` |
| MySQL 스키마 | `docs/database/mysql-schema.md` |
| MongoDB 스키마 | `docs/database/mongodb-schema.md` |
| Redis 키 설계 | `docs/database/redis-keys.md` |
| API 공통 규칙 | `docs/api/common.md` |
| User Service API | `docs/api/user-service.md` |
| Plan Service API | `docs/api/plan-service.md` |
| Exercise Catalog API | `docs/api/exercise-catalog.md` |
| Workout Service API | `docs/api/workout-service.md` |
| Analytics API | `docs/api/analytics-service.md` |
| Notification Service API | `docs/api/notification-service.md` |
| 구현 Phase 계획 | `docs/planning/phase-plan.md` |
| 기능 명세 (spec-writer 산출물) | `docs/specs/` |
| 스킬 사용 가이드 | `docs/context/skill-guide.md` |
| 보안 가이드 | `docs/context/security-guide.md` |
| 성능 목표 | `docs/context/performance-goals.md` |

---

## 성능 목표 (SLA)

| 항목 | 목표 |
|------|------|
| 오늘의 루틴 조회 (P95) | < 200ms (Redis 캐시 히트) |
| 세트 기록 API (P95) | < 300ms |
| 서비스 가용성 | > 99.9% |
| Elasticsearch 검색 (P95) | < 500ms |
| 모바일 첫 화면 로딩 | < 1초 |

---

## 보안 필수 사항

- Vault를 통한 시크릿 런타임 주입 (환경변수 하드코딩 절대 금지)
- Istio mTLS 서비스 간 통신 암호화
- 모든 입력값 Spring Validation 검증
- SQL Injection 방지: JPA Parameterized Query만 사용
- Rate Limiting: Redis 기반 (IP당 100 req/min)
- `.env`, credentials, API 키를 git에 커밋 절대 금지

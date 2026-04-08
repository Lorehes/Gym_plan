# Claude Code 스킬 사용 가이드

> Claude Code에서 스킬을 호출하는 방법과 GymPlan 프로젝트에서의 구체적인 사용 예시

---

## 스킬 호출 방법

스킬은 Claude Code 프롬프트에서 아래 방식으로 호출해요.

### 방법 A — 스킬 이름으로 직접 호출 (권장)
```
> feature-lead 스킬로 user-service JWT 인증 기능을 구현해줘
> spec-writer 스킬로 workout-service 세션 API 명세를 작성해줘
> security-lead 스킬로 PR #3 보안 검토해줘
```

### 방법 B — 파일 경로 직접 지정
```
> /mnt/skills/user/backend-developer/SKILL.md 를 읽고
  plan-service의 오늘의 루틴 조회 API를 Redis 캐시와 함께 구현해줘
```

### 방법 C — CLAUDE.md 자동 인식
CLAUDE.md에 스킬 위치가 명시되어 있으므로 컨텍스트에 맞는 스킬이 자동 참조됩니다.

---

## 워크플로우 — 새 기능 구현 (전체 흐름)

```bash
# 1. Worktree 생성 (반드시 먼저!)
git worktree add ../gymplan-{기능명} -b feature/{기능명}
cd ../gymplan-{기능명}
claude   # Claude Code 실행

# 2. Claude Code 안에서 순서대로
> spec-writer 스킬로 {기능명} 명세를 작성해줘
> feature-lead 스킬로 명세 기반으로 {기능명}을 구현해줘
  # feature-lead 내부에서 자동 수행:
  # → backend-developer: 구현
  # → security-lead: 보안 검토
  # → qa-lead: 테스트 작성
  # → PR 생성

# 3. 별도 세션에서 검토
> tech-lead 스킬로 PR #{번호} 기술 검토해줘
> project-manager 스킬로 PR #{번호} 최종 승인해줘

# 4. 완료 후 Worktree 정리
cd ../gymplan
git worktree remove ../gymplan-{기능명}
```

---

## Phase별 실제 명령어

### Phase 0 — 셋업
```
> planner 스킬로 Gradle 멀티모듈 구조를 설계하고
  settings.gradle.kts와 루트 build.gradle.kts를 생성해줘

> backend-developer 스킬로 docker-compose.local.yml을 작성해줘
  MySQL, MongoDB, Redis, Elasticsearch, Kafka, Zookeeper 포함

> backend-developer 스킬로 common-dto, common-exception,
  common-security 모듈의 기본 구조를 생성해줘
```

### Phase 1 — user-service
```bash
git worktree add ../gymplan-user -b feature/user-service
cd ../gymplan-user && claude

> spec-writer 스킬로 user-service 회원가입/로그인/JWT 갱신
  API 명세를 docs/specs/user-service.md로 작성해줘

> feature-lead 스킬로 docs/specs/user-service.md 기반으로
  user-service를 구현해줘
  (Kotlin + Spring Boot 3, MySQL + Redis, JWT RS256)
```

### Phase 1 — exercise-catalog
```bash
git worktree add ../gymplan-exercise -b feature/exercise-catalog
cd ../gymplan-exercise && claude

> feature-lead 스킬로 exercise-catalog를 구현해줘
  MySQL CRUD + Elasticsearch 검색 연동 포함
  초기 데이터 시딩도 포함해줘 (주요 운동 100여개)
```

### Phase 1 — plan-service
```bash
git worktree add ../gymplan-plan -b feature/plan-service
cd ../gymplan-plan && claude

> spec-writer 스킬로 plan-service API 명세를 작성해줘
  오늘의 루틴 조회 Redis 캐시 전략 포함

> feature-lead 스킬로 plan-service를 구현해줘
  오늘의 루틴 조회 목표 응답시간: P95 < 200ms
```

### Phase 2 — workout-service
```bash
git worktree add ../gymplan-workout -b feature/workout-service
cd ../gymplan-workout && claude

> spec-writer 스킬로 workout-service 세션 API 명세를 작성해줘
  MongoDB 스키마와 Kafka 이벤트 페이로드 포함

> feature-lead 스킬로 workout-service를 구현해줘
  세션 완료 시 Kafka workout.session.completed 발행 포함
```

### Phase 3 — 성능 최적화
```
> performance-engineer 스킬로 plan-service 오늘의 루틴 조회 API
  성능을 분석하고 Redis 캐싱을 최적화해줘
  목표: P95 < 200ms

> code-improvement-advisor 스킬로 services/ 전체를
  코드 품질 관점에서 리뷰해줘

> security-lead 스킬로 전체 서비스 보안 감사를 수행해줘
  OWASP Top 10 기준으로
```

### Phase 4 — 클라이언트
```bash
git worktree add ../gymplan-mobile -b feature/mobile-app
cd ../gymplan-mobile && claude

> designer 스킬로 GymPlan React Native 디자인 시스템을 구축해줘
  체육관 환경: 큰 버튼(48dp+), 고대비, 다크모드 필수

> feature-lead 스킬로 운동 실행 화면을 구현해줘
  세트 체크인, 휴식 타이머, 오프라인 캐시 포함
  디자이너가 만든 디자인 시스템 반드시 참조
```

---

## 스킬별 역할 요약

| 스킬 | 언제 쓰는가 |
|------|------------|
| `planner` | 새 서비스/기능 시작 전 설계 |
| `spec-writer` | API 명세, 인수 기준 정의 (feature-lead 전 선행) |
| `plan` | 코드베이스 분석, 구현 계획 수립 |
| `feature-lead` | 모든 기능 구현의 진입점 (Worktree 필수) |
| `backend-developer` | 단순 수정, 버그픽스 (Worktree 없이 가능) |
| `frontend-developer` | 단순 UI 수정 (Worktree 없이 가능) |
| `designer` | Phase 4 시작 전 디자인 시스템 |
| `performance-engineer` | Redis, ES 쿼리, 모바일 성능 분석 |
| `qa-lead` | spec-writer 명세 기반 테스트 작성 |
| `security-lead` | JWT, Vault, OWASP 보안 검토 |
| `code-improvement-advisor` | PR 머지 전 코드 품질 |
| `tech-lead` | PR 기술 검토 (feature-lead 이후) |
| `project-manager` | 최종 승인 및 프로덕션 머지 |

---

## 주의사항

**feature-lead는 Worktree 필수**
```bash
# ❌ 잘못된 방법 — main/develop에서 직접 실행
> feature-lead 스킬로 구현해줘  # 거부됨

# ✅ 올바른 방법
git worktree add ../gymplan-feature -b feature/기능명
cd ../gymplan-feature && claude
> feature-lead 스킬로 구현해줘
```

**qa-lead는 spec-writer 명세 선행 필수**
```bash
# ❌ 명세 없이 qa-lead 호출 → 얕은 테스트
> qa-lead 스킬로 테스트 작성해줘

# ✅ 명세 후 qa-lead 호출
> spec-writer 스킬로 명세 작성해줘
> qa-lead 스킬로 docs/specs/{명세파일} 기반 테스트 작성해줘
```

**3-Tier 검토 순서 준수**
```
feature-lead (PR 생성)
    → tech-lead (기술 검토)
        → project-manager (최종 승인 & 머지)
```

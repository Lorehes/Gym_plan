# 구현 Phase 계획

총 11주. Phase 0부터 순차적으로 진행.

---

## Phase 0 — 프로젝트 셋업 (1주)

**목표**: 모든 서비스가 공유하는 기반 구조 완성

```
[ ] Gradle 멀티모듈 모노레포 구성
    settings.gradle.kts, 루트 build.gradle.kts
    서비스별 build.gradle.kts

[ ] 공통 모듈 기본 구조
    common-dto:       ApiResponse<T>, PageResponse<T>
    common-exception: GymPlanException, ErrorCode enum
    common-security:  JwtProvider, JwtFilter (Gateway용)

[ ] docker-compose.local.yml
    MySQL 8, MongoDB 7, Redis 7,
    Elasticsearch 8, Kafka + Zookeeper,
    Kafka UI (포트 9000)

[ ] CLAUDE.md, README.md 작성 (이미 완료)

[ ] GitHub 저장소 브랜치 전략 설정
    main (프로덕션), develop (통합), feature/* (개발)

[ ] Harness CI 기본 파이프라인
    Gradle 빌드 + 단위 테스트

[ ] Vault dev 모드 로컬 설정
```

---

## Phase 1 — 핵심 서비스 MVP (3주)

**목표**: 루틴 계획 수립까지 동작하는 백엔드

### 1주차 — 인증 기반
```
[ ] user-service 구현
    회원가입, 로그인, JWT RS256 발급
    Refresh Token Rotation (Redis)
    Spring Security 설정

[ ] api-gateway 구현
    라우팅 설정 (각 서비스별)
    JWT 검증 필터 → X-User-Id 헤더 주입
    Rate Limiting (Redis 기반)
```

### 2주차 — 운동 종목 & 루틴
```
[ ] exercise-catalog 구현
    MySQL CRUD
    Elasticsearch 인덱싱 및 검색 API
    초기 데이터 시딩 (주요 운동 100여개)

[ ] plan-service 구현
    루틴 CRUD (workout_plans, plan_exercises)
    exercise-catalog와 exerciseId 참조
```

### 3주차 — 완성 및 인프라
```
[ ] plan-service: 오늘의 루틴 조회 + Redis 캐싱

[ ] Istio 기본 설정
    mTLS, VirtualService, DestinationRule

[ ] 통합 테스트
    user → gateway → plan → exercise-catalog 흐름 검증
```

---

## Phase 2 — 운동 실행 & 기록 (2주)

**목표**: 체육관에서 실제 운동 실행 가능

### 1주차
```
[ ] workout-service 구현
    세션 시작/세트 기록/완료 API (MongoDB)
    Kafka 이벤트 발행

[ ] notification-service 구현
    Kafka 소비 → Redis pub-sub 휴식 타이머
    (클라이언트는 WebSocket 또는 SSE로 수신)
```

### 2주차
```
[ ] analytics-service 구현
    Kafka 소비 → Elasticsearch 색인
    통계 집계 API (summary, volume, frequency, PR)

[ ] Phase 2 통합 테스트
    운동 완료 → Kafka → analytics/notification 흐름
```

---

## Phase 3 — 완성도 & 인프라 (2주)

**목표**: 운영 가능한 인프라 완성

### 1주차
```
[ ] Prometheus + Thanos 설치 및 메트릭 수집
    JVM, HTTP, Kafka, DB 커넥션 메트릭

[ ] Grafana 대시보드
    SLA (P95/P99 응답시간, 에러율)
    비즈니스 메트릭 (DAU, 운동 완료율)
    인프라 메트릭 (CPU, Memory, Pod 상태)

[ ] HPA 설정
    CPU 70% 이상 → 자동 스케일
```

### 2주차
```
[ ] GoCD 파이프라인
    Stage 배포, E2E 테스트, 수동 승인 게이트

[ ] ArgoCD GitOps 설정
    K8s 매니페스트 Git 저장소 동기화

[ ] Vault 운영 연동
    DB 패스워드, Kafka 인증 정보 → Vault KV

[ ] code-improvement-advisor로 전체 코드 품질 리뷰
[ ] security-lead로 전체 보안 감사
```

---

## Phase 4 — 클라이언트 앱 (3주)

**목표**: 실제 체육관에서 사용 가능한 앱

### 1주차 — 모바일 기반
```
[ ] React Native (Expo) 프로젝트 초기화
[ ] designer 스킬로 디자인 시스템 구축
    컬러, 타이포그래피, 공통 컴포넌트
    체육관 환경: 큰 버튼(48dp+), 고대비, 다크모드

[ ] 인증 화면 (로그인/회원가입)
[ ] 홈 화면 + 오늘의 루틴 조회
```

### 2주차 — 모바일 핵심 기능
```
[ ] 운동 실행 화면 (세트 체크인)
    한 손 조작 UX
    세트 완료 → 타이머 자동 시작

[ ] 휴식 타이머 화면 (SSE/WebSocket)
[ ] 완료 요약 화면

[ ] 오프라인 지원
    AsyncStorage로 오늘의 루틴 캐시
    세트 기록 큐잉 → 재연결 시 자동 동기화

[ ] expo-keep-awake (운동 중 화면 꺼짐 방지)
```

### 3주차 — 웹앱
```
[ ] React + Vite + TypeScript 프로젝트 초기화
[ ] 루틴 계획 화면 (CRUD + 드래그 순서 변경)
[ ] 운동 종목 검색 화면
[ ] 통계 대시보드 (차트)
[ ] 운동 히스토리 캘린더 뷰
[ ] E2E 테스트 (Playwright)
```

---

## 체크포인트 기준

각 Phase 완료 조건:

| Phase | 완료 기준 |
|-------|----------|
| 0 | `docker-compose up` 후 모든 인프라 정상 기동 |
| 1 | 루틴 생성 → 오늘의 루틴 조회 E2E 성공 |
| 2 | 운동 세션 완료 → Kafka → analytics 집계 E2E 성공 |
| 3 | Grafana에서 전 서비스 메트릭 확인, ArgoCD 배포 성공 |
| 4 | 모바일에서 운동 완료 → 웹에서 통계 확인 가능 |

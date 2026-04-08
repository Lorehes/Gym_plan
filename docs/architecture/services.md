# 서비스별 아키텍처 상세

## 공통 패키지 구조

모든 서비스는 아래 레이어 구조를 따릅니다.

```
com.gymplan.{service}/
├── domain/
│   ├── entity/          ← JPA 엔티티 또는 MongoDB Document
│   ├── repository/      ← 리포지토리 인터페이스
│   └── vo/              ← Value Object
├── application/
│   ├── service/         ← 유스케이스 (비즈니스 로직)
│   ├── dto/             ← Request / Response DTO
│   └── event/           ← Kafka 이벤트 객체
├── infrastructure/
│   ├── persistence/     ← JPA/MongoDB 리포지토리 구현체
│   ├── cache/           ← Redis 연동
│   ├── messaging/       ← Kafka Producer/Consumer
│   └── external/        ← 외부 서비스 연동
└── presentation/
    ├── controller/      ← REST Controller
    └── advice/          ← @ExceptionHandler
```

---

## user-service

```
역할:   회원가입, 로그인, JWT 발급/갱신/무효화, 프로필 관리
DB:     MySQL (gymplan_user), Redis
포트:   8081

핵심 컴포넌트:
  - AuthService: 회원가입, 로그인, 토큰 발급
  - TokenService: JWT 생성/검증, Redis 세션 관리
  - UserService: 프로필 CRUD
  - JwtProvider (common-security): RS256 서명/검증

의존성:
  - common-security: JwtProvider 공유
  - Redis: 세션, Refresh Token 저장
```

---

## plan-service

```
역할:   루틴 계획 CRUD, 오늘의 루틴 조회 (Redis 캐시)
DB:     MySQL (gymplan_plan), Redis
포트:   8082

핵심 컴포넌트:
  - PlanService: 루틴 CRUD, 캐시 무효화 관리
  - TodayPlanService: 요일 기반 오늘의 루틴 조회 (Cache-Aside)
  - PlanCacheManager: Redis 캐시 관리 (plan:today:{userId}, plan:cache:{planId})

캐시 전략:
  - 읽기: Redis 먼저 → 미스 시 DB 조회 후 캐시 저장
  - 쓰기: DB 저장 → Redis 관련 키 즉시 DEL
  - TTL: 10분 (EX 600)

의존성:
  - exercise-catalog: exerciseId 참조 (직접 호출 없이 ID만 저장)
```

---

## exercise-catalog

```
역할:   운동 종목 CRUD, Elasticsearch 기반 검색
DB:     MySQL (gymplan_exercise), Elasticsearch
포트:   8083

핵심 컴포넌트:
  - ExerciseService: 종목 CRUD, ES 동기화
  - ExerciseSearchService: Elasticsearch 검색 (nori 형태소 분석)
  - ExerciseIndexer: MySQL → ES 동기화

검색 전략:
  - 한국어: nori analyzer, match_phrase_prefix
  - 영어: standard analyzer, multi_match
  - 필터: muscle_group, equipment (term query)
```

---

## workout-service

```
역할:   운동 세션 실행, 세트 기록, Kafka 이벤트 발행
DB:     MongoDB (gymplan_workout)
포트:   8084

핵심 컴포넌트:
  - SessionService: 세션 시작/완료, 상태 관리
  - SetRecordService: 세트 기록 추가/수정/삭제 ($push 연산)
  - WorkoutEventPublisher: Kafka 이벤트 발행 (비동기)

중요 원칙:
  - API 응답 후 Kafka 발행 (응답 시간에 포함 금지)
  - MongoDB $push로 배열 append (전체 문서 교체 금지)
  - 진행 중 세션: completedAt=null로 구분
```

---

## analytics-service

```
역할:   Kafka 이벤트 소비, Elasticsearch 색인, 통계 API
DB:     Elasticsearch
포트:   8085

핵심 컴포넌트:
  - WorkoutEventConsumer: Kafka Consumer (workout.session.completed, workout.set.logged)
  - WorkoutIndexer: Elasticsearch 색인
  - AnalyticsQueryService: 통계 집계 쿼리 (Aggregation)
  - PersonalRecordService: 1RM 추정 (Epley 공식)

1RM 공식: estimated1RM = weight × (1 + reps / 30)
```

---

## notification-service

```
역할:   Kafka 이벤트 소비, Redis pub-sub 기반 휴식 타이머, 푸시 알림
DB:     Redis
포트:   8086

핵심 컴포넌트:
  - WorkoutEventConsumer: Kafka Consumer (workout.session.completed)
  - RestTimerService: Redis PUBLISH timer:{sessionId} {restSeconds}
  - PushNotificationService: 운동 완료 알림 (FCM)

클라이언트 연동:
  - 모바일 앱이 SSE 또는 WebSocket으로 timer:{sessionId} 구독
  - 서버 → Redis PUBLISH → 클라이언트 타이머 시작
```

---

## api-gateway

```
역할:   라우팅, JWT 검증 필터, Rate Limiting, CORS
포트:   8080 (외부 진입점)

핵심 컴포넌트:
  - JwtAuthenticationFilter: JWT 검증, X-User-Id 헤더 주입
  - RateLimitFilter: Redis 기반 Rate Limiting
  - 라우팅 설정: application.yml에 각 서비스별 경로 정의

보안 원칙:
  - 하위 서비스는 X-User-Id 헤더만 신뢰 (JWT 직접 검증 금지)
  - 외부에서 X-User-Id 직접 주입 시 Gateway에서 차단
```

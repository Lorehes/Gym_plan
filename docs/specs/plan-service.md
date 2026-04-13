# Plan Service 명세서

**작성자:** Spec Writer Agent
**작성일:** 2026-04-13
**버전:** 1.0
**상태:** Draft

---

## 목차
1. [개요](#개요)
2. [사용자 스토리](#사용자-스토리)
3. [인수 기준](#인수-기준)
4. [API 명세](#api-명세)
5. [데이터 모델](#데이터-모델)
6. [외부 서비스 의존성 (exercise-catalog)](#외부-서비스-의존성-exercise-catalog)
7. [Redis 캐시 전략](#redis-캐시-전략)
8. [테스트 케이스](#테스트-케이스)
9. [비기능 요구사항](#비기능-요구사항)

---

## 개요

- **목적:** 사용자가 요일별 운동 루틴을 미리 계획하고, 체육관에서 오늘의 루틴을 1초 이내 즉시 조회할 수 있게 한다.
- **사용자:** 인증된 일반 사용자 (JWT Bearer Token 보유)
- **우선순위:** High — 핵심 사용자 흐름의 첫 단계
- **핵심 성능 목표:** 오늘의 루틴 조회 P95 < 200ms (Redis 캐시 히트)

### 서비스 전제 조건

| 조건 | 내용 |
|------|------|
| **user-service 완료** | JWT 발급·검증 동작. plan-service는 JWT 직접 검증 안 함 — X-User-Id 헤더만 신뢰 |
| **api-gateway 완료** | 모든 인바운드 요청에 `X-User-Id` 헤더 주입. 미주입 요청은 Gateway에서 차단 |
| **단독 테스트 시** | Gateway 없이 plan-service 직접 호출. `X-User-Id` 헤더를 요청에 수동 포함 (Mock 처리 — 아래 참조) |

---

## 사용자 스토리

### US-01: 루틴 생성
**As a** 헬스장 회원
**I want to** 요일별 운동 루틴을 미리 만들고 싶다
**So that** 체육관에 도착하면 고민 없이 바로 운동을 시작할 수 있다

### US-02: 오늘의 루틴 조회 ⭐
**As a** 체육관에 입장한 사용자
**I want to** 오늘 요일에 배정된 루틴을 1초 내에 볼 수 있다
**So that** 운동 종목, 세트 수, 목표 중량을 즉시 확인하고 바로 실행할 수 있다

### US-03: 루틴에 운동 추가/수정
**As a** 루틴 계획 중인 사용자
**I want to** 루틴에 운동 종목을 추가하고 세트·무게·휴식 시간을 설정하고 싶다
**So that** 나에게 맞는 맞춤 루틴을 구성할 수 있다

### US-04: 운동 순서 변경
**As a** 루틴을 정리하는 사용자
**I want to** 루틴 내 운동 순서를 드래그로 바꾸고 싶다
**So that** 효율적인 순서로 운동 계획을 최적화할 수 있다

---

## 인수 기준

### ✅ 루틴 관리

#### MUST
- [ ] 루틴 생성 시 이름(필수, 1~100자)과 요일(0=월~6=일 또는 null)을 받는다
- [ ] 사용자는 자신의 루틴만 조회·수정·삭제할 수 있다 (타인 루틴 접근 → 403)
- [ ] 루틴 삭제는 soft delete (`is_active = false`)로 처리한다
- [ ] 루틴 수정·삭제 시 관련 Redis 캐시(`plan:today:{userId}`, `plan:cache:{planId}`)를 즉시 무효화한다
- [ ] 요일별 중복 루틴 허용 (사용자가 여러 루틴을 같은 요일에 배정 가능 — 오늘의 루틴은 가장 최근 생성된 것 반환)

#### SHOULD
- [ ] 루틴에 설명(description) 선택 입력 가능
- [ ] 템플릿 루틴(`is_template = true`) 구분 지원

### ✅ 오늘의 루틴 조회

#### MUST
- [ ] 오늘의 요일(서버 기준)에 배정된 루틴과 포함된 모든 운동 항목을 반환한다
- [ ] Redis 캐시 히트 시 DB 조회 없이 응답한다 (P95 < 200ms)
- [ ] 캐시 미스 시 DB 조회 후 Redis에 저장하고 응답한다 (TTL 10분)
- [ ] 오늘 요일에 배정된 루틴이 없으면 `data: null`을 반환한다 (404 아님)
- [ ] 운동 항목은 `order_index` 오름차순으로 정렬되어 반환된다

### ✅ 루틴 내 운동 관리

#### MUST
- [ ] 운동 추가 시 `exerciseId`(필수), `targetSets`(기본 3), `targetReps`(기본 10), `restSeconds`(기본 90)을 받는다
- [ ] `orderIndex`가 생략되면 현재 마지막 순서 다음으로 자동 배정된다
- [ ] 운동 순서 변경 시 `orderedIds` 배열로 전체 순서를 한 번에 업데이트한다
- [ ] 운동 추가·수정·삭제 후 연관 캐시를 즉시 무효화한다

---

## API 명세

### 공통

**Base URL:** `/api/v1/plans`
**인증:** 모든 엔드포인트에 Bearer Token 필수. Gateway가 검증 후 `X-User-Id` 헤더를 내부 서비스에 전달.
**공통 응답 형식:**
```json
{
  "success": true | false,
  "data": { ... } | null,
  "error": null | { "code": "...", "message": "..." },
  "timestamp": "2026-04-13T10:00:00Z"
}
```

---

### GET /api/v1/plans/today ⭐ 핵심 API

오늘 요일에 배정된 루틴과 운동 목록 반환.

**헤더:**
```
X-User-Id: {userId}   ← Gateway 주입
```

**캐시 흐름:**
```
Redis GET plan:today:{userId}
  └─ HIT  → 즉시 반환 (P95 < 200ms 목표)
  └─ MISS → DB 조회 → Redis SET EX 600 → 반환
```

**Response 200 — 루틴 있음:**
```json
{
  "success": true,
  "data": {
    "planId":    12,
    "name":      "가슴/삼두 루틴",
    "dayOfWeek": 1,
    "exercises": [
      {
        "id":             1,
        "exerciseId":     10,
        "exerciseName":   "벤치프레스",
        "muscleGroup":    "CHEST",
        "orderIndex":     0,
        "targetSets":     4,
        "targetReps":     10,
        "targetWeightKg": 70.0,
        "restSeconds":    90
      }
    ]
  },
  "error": null,
  "timestamp": "2026-04-13T10:00:00Z"
}
```

**Response 200 — 오늘 루틴 없음:**
```json
{
  "success": true,
  "data": null,
  "error": null,
  "timestamp": "2026-04-13T10:00:00Z"
}
```

**에러 코드:**

| 코드 | HTTP 상태 | 설명 |
|------|-----------|------|
| `UNAUTHORIZED` | 401 | 인증 토큰 없음 또는 만료 |
| `SERVER_ERROR` | 500 | 서버 내부 오류 |

---

### GET /api/v1/plans

내 루틴 목록 조회.

**Response 200:**
```json
{
  "success": true,
  "data": [
    {
      "planId":        12,
      "name":          "가슴/삼두 루틴",
      "dayOfWeek":     1,
      "exerciseCount": 5,
      "isTemplate":    false
    }
  ],
  "error": null,
  "timestamp": "2026-04-13T10:00:00Z"
}
```

---

### POST /api/v1/plans

루틴 생성.

**Request Body:**
```typescript
{
  name:        string;  // 필수, 1~100자
  description?: string; // 선택, 최대 500자
  dayOfWeek?:  number;  // 선택, 0~6 (0=월, 6=일), null=무요일
}
```

**Response 201:**
```json
{
  "success": true,
  "data": {
    "planId":      13,
    "name":        "등/이두 루틴",
    "description": "화요일 루틴",
    "dayOfWeek":   1,
    "isTemplate":  false
  },
  "error": null,
  "timestamp": "2026-04-13T10:00:00Z"
}
```

**에러 코드:**

| 코드 | HTTP 상태 | 설명 |
|------|-----------|------|
| `VALIDATION_ERROR` | 400 | 입력값 검증 실패 (이름 누락, 길이 초과 등) |
| `UNAUTHORIZED` | 401 | 인증 실패 |

---

### GET /api/v1/plans/{planId}

루틴 상세 조회. 캐시 키: `plan:cache:{planId}` (TTL 10분)

**Response 200:** 루틴 정보 + 운동 항목 전체 (today API 응답과 동일 구조)

**에러 코드:**

| 코드 | HTTP 상태 | 설명 |
|------|-----------|------|
| `PLAN_NOT_FOUND` | 404 | 루틴 없음 또는 삭제됨 |
| `FORBIDDEN` | 403 | 타인 루틴 접근 시도 |

---

### PUT /api/v1/plans/{planId}

루틴 수정. 수정 완료 후 `plan:today:{userId}`, `plan:cache:{planId}` 즉시 DEL.

**Request Body:** POST와 동일 구조 (수정할 필드만 포함 가능)

**Response 200:** 수정된 루틴 정보 반환

---

### DELETE /api/v1/plans/{planId}

루틴 삭제 (soft delete). 삭제 후 관련 캐시 즉시 DEL.

**Response 204:** No Content

---

### POST /api/v1/plans/{planId}/exercises

루틴에 운동 추가. 완료 후 `plan:today:{userId}`, `plan:cache:{planId}` DEL.

> **설계 원칙:** plan-service는 exercise-catalog를 HTTP로 직접 호출하지 않는다.
> 클라이언트가 exercise-catalog에서 운동을 선택할 때 이미 `exerciseName`·`muscleGroup`을 알고 있으므로,
> 해당 값을 요청 본문에 포함시켜 전송한다. plan-service는 수신한 값을 `plan_exercises`에 그대로 저장한다 (비정규화).

**Request Body:**
```typescript
{
  exerciseId:      number;  // 필수 — exercise-catalog의 exercise ID
  exerciseName:    string;  // 필수 — 클라이언트가 catalog에서 가져온 종목명 (비정규화 저장)
  muscleGroup:     string;  // 필수 — CHEST | BACK | SHOULDERS | ARMS | LEGS | CORE | CARDIO
  orderIndex?:     number;  // 선택, 생략 시 마지막 순서로 자동 배정
  targetSets?:     number;  // 선택, 기본 3
  targetReps?:     number;  // 선택, 기본 10
  targetWeightKg?: number;  // 선택, 소수점 2자리 (kg)
  restSeconds?:    number;  // 선택, 기본 90
  notes?:          string;  // 선택, 최대 255자
}
```

**Response 201:**
```json
{
  "success": true,
  "data": {
    "id":             5,
    "exerciseId":     10,
    "exerciseName":   "벤치프레스",
    "muscleGroup":    "CHEST",
    "orderIndex":     2,
    "targetSets":     4,
    "targetReps":     10,
    "targetWeightKg": 70.0,
    "restSeconds":    90,
    "notes":          "가슴 수축 집중"
  },
  "error": null,
  "timestamp": "2026-04-13T10:00:00Z"
}
```

**에러 코드:**

| 코드 | HTTP 상태 | 설명 |
|------|-----------|------|
| `VALIDATION_ERROR` | 400 | exerciseName 또는 muscleGroup 누락, muscleGroup 허용값 외 |
| `PLAN_NOT_FOUND` | 404 | planId가 존재하지 않음 |
| `FORBIDDEN` | 403 | 타인 루틴에 운동 추가 시도 |

> **`EXERCISE_NOT_FOUND` 에러는 존재하지 않는다.**
> plan-service는 exerciseId의 실존 여부를 검증하지 않는다. exerciseId는 외부 참조 키로만 저장되며,
> 클라이언트는 exercise-catalog에서 직접 선택한 값만 전송해야 한다.

---

### PUT /api/v1/plans/{planId}/exercises/{exerciseItemId}

운동 설정 수정 (세트 수, 무게, 휴식 시간 등). 완료 후 캐시 DEL.

**Request Body:** POST /exercises와 동일 (수정할 필드만)

**Response 200:** 수정된 운동 항목 반환

---

### DELETE /api/v1/plans/{planId}/exercises/{exerciseItemId}

운동 항목 제거. 완료 후 캐시 DEL.

**Response 204:** No Content

---

### PUT /api/v1/plans/{planId}/exercises/reorder

루틴 내 운동 순서 변경. 완료 후 캐시 DEL.

**Request Body:**
```typescript
{
  orderedIds: number[];  // 전체 exerciseItemId 배열 (새 순서)
}
```

**예시:**
```json
{ "orderedIds": [3, 1, 2, 4] }
```

**Response 200:**
```json
{
  "success": true,
  "data": { "reordered": true },
  "error": null,
  "timestamp": "2026-04-13T10:00:00Z"
}
```

**에러 코드:**

| 코드 | HTTP 상태 | 설명 |
|------|-----------|------|
| `VALIDATION_ERROR` | 400 | orderedIds의 ID 수가 루틴의 운동 수와 불일치 |
| `FORBIDDEN` | 403 | 타인 루틴 접근 |

---

## 데이터 모델

### workout_plans 테이블

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 루틴 고유 ID |
| `user_id` | BIGINT | NOT NULL | 소유 사용자 ID |
| `name` | VARCHAR(100) | NOT NULL | 루틴 이름 |
| `description` | TEXT | NULL | 루틴 설명 |
| `day_of_week` | TINYINT | NULL | 0=월~6=일, NULL=무요일 |
| `is_template` | BOOLEAN | DEFAULT FALSE | 템플릿 여부 |
| `is_active` | BOOLEAN | DEFAULT TRUE | soft delete 플래그 |
| `created_at` | DATETIME | DEFAULT NOW() | 생성 시각 |
| `updated_at` | DATETIME | DEFAULT NOW() ON UPDATE NOW() | 수정 시각 |

**인덱스:**
- `idx_user_day (user_id, day_of_week)` — 오늘의 루틴 조회 최적화

---

### plan_exercises 테이블

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 운동 항목 ID |
| `plan_id` | BIGINT | NOT NULL | 소속 루틴 ID (FK) |
| `exercise_id` | BIGINT | NOT NULL | exercise-catalog ID (DB FK 없음, 서비스 간 경계) |
| `exercise_name` | VARCHAR(100) | NOT NULL | 종목명 — 비정규화, 클라이언트가 제공 |
| `muscle_group` | VARCHAR(50) | NOT NULL | 근육 부위 — 비정규화, 클라이언트가 제공 |
| `order_index` | INT | NOT NULL | 루틴 내 순서 (0-based) |
| `target_sets` | INT | DEFAULT 3 | 목표 세트 수 |
| `target_reps` | INT | DEFAULT 10 | 목표 반복 횟수 |
| `target_weight` | DECIMAL(5,2) | NULL | 목표 중량 (kg) |
| `rest_seconds` | INT | DEFAULT 90 | 세트 간 휴식 (초) |
| `notes` | VARCHAR(255) | NULL | 메모 |

> **`exercise_name` / `muscle_group` 비정규화 근거:**
> plan-service는 exercise-catalog에 런타임 HTTP 의존성을 갖지 않는다. 오늘의 루틴 조회 시
> DB와 Redis만으로 완결되어야 하므로, 종목명과 근육군은 운동 추가 시점에 클라이언트로부터 수신해 저장한다.
> exercise-catalog에서 종목명이 변경되어도 plan_exercises에는 반영되지 않는다 (허용된 Stale).

**인덱스:**
- `idx_plan (plan_id)` — 루틴 상세 조회

---

## 외부 서비스 의존성 (exercise-catalog)

### 설계 원칙: No Direct HTTP Call

plan-service는 exercise-catalog를 HTTP로 직접 호출하지 않는다. (`services.md`: `exerciseId 참조 (직접 호출 없이 ID만 저장)`)

**이유:**
- 오늘의 루틴 조회(핵심 경로)가 exercise-catalog 가용성에 종속되면 안 됨
- exercise-catalog 장애 시에도 체육관에서 루틴 조회·실행은 반드시 가능해야 함

### 의존성 처리 방식

```
[클라이언트 흐름]

1. 사용자가 운동 검색
   └─ GET /api/v1/exercises?q=벤치프레스  (exercise-catalog, 8083)
      └─ 응답: { exerciseId: 10, exerciseName: "벤치프레스", muscleGroup: "CHEST", ... }

2. 사용자가 루틴에 추가
   └─ POST /api/v1/plans/12/exercises  (plan-service, 8082)
      요청 본문에 exerciseId + exerciseName + muscleGroup 포함
      └─ plan-service는 exercise-catalog를 호출하지 않고 그대로 plan_exercises에 저장

3. 오늘의 루틴 조회
   └─ GET /api/v1/plans/today  (plan-service, 8082)
      └─ Redis 또는 DB에서 plan_exercises 직접 조회 (exercise-catalog 미관여)
```

### 허용된 데이터 일관성 Stale

| 상황 | 동작 |
|------|------|
| exercise-catalog에서 종목명이 수정됨 | plan_exercises의 exercise_name은 추가 시점 값 유지 (갱신 없음) |
| exercise-catalog에서 종목이 삭제됨 | plan_exercises 행은 그대로 유지, exerciseId만 孤立 |
| 클라이언트가 잘못된 exerciseName 전송 | plan-service는 검증하지 않음 — 클라이언트 책임 |

이 Stale은 개인 헬스 앱 특성상 허용 가능한 수준이다.
종목 변경 빈도가 낮고, 루틴 내 운동명은 사용자에게 참고 표시용으로만 쓰이기 때문이다.

### 테스트 전략

exercise-catalog를 직접 호출하지 않으므로 **별도의 WireMock 설정은 불필요**하다.

| 테스트 유형 | 방법 | 검증 대상 |
|-------------|------|-----------|
| 단위 테스트 | PlanExerciseService 단독 | exerciseName/muscleGroup이 DB에 저장되는지 |
| 통합 테스트 | `@SpringBootTest` + TestContainers (MySQL, Redis) | 실제 저장·조회·캐시 무효화 |
| 계약 테스트 | — | plan-service는 해당 없음 (exercise-catalog를 호출하지 않으므로) |

#### 테스트 픽스처 예시

```kotlin
// 운동 추가 요청 — exercise-catalog 불필요, 픽스처로 직접 구성
val request = AddExerciseRequest(
    exerciseId    = 10L,
    exerciseName  = "벤치프레스",   // 테스트에서 임의 값 사용 가능
    muscleGroup   = "CHEST",
    targetSets    = 4,
    targetReps    = 10,
    targetWeightKg = 70.0,
    restSeconds   = 90
)
```

---

## Redis 캐시 전략

### 캐시 키 구조

| 키 | 타입 | TTL | 설명 |
|----|------|-----|------|
| `plan:today:{userId}` | String (JSON) | 600초 (10분) | 오늘의 루틴 전체 (운동 목록 포함) |
| `plan:cache:{planId}` | String (JSON) | 600초 (10분) | 루틴 상세 (운동 목록 포함) |

### Read-Aside 패턴 (오늘의 루틴)

```
Client → GET /api/v1/plans/today
           │
           ├─ Redis GET plan:today:{userId}
           │     ├─ HIT  ──────────────────── Response (< 200ms P95)
           │     └─ MISS
           │           │
           │           ├─ DB 쿼리
           │           │   SELECT wp.*, pe.*
           │           │   FROM workout_plans wp
           │           │   JOIN plan_exercises pe ON wp.id = pe.plan_id
           │           │   WHERE wp.user_id = ? AND wp.day_of_week = ?
           │           │     AND wp.is_active = TRUE
           │           │   ORDER BY wp.created_at DESC, pe.order_index ASC
           │           │   LIMIT 1 (루틴 1개)
           │           │
           │           └─ Redis SET plan:today:{userId} {json} EX 600
           │                 └─ Response
```

### Write-Invalidation 패턴 (캐시 무효화)

루틴 또는 운동 항목이 변경될 때 캐시를 즉시 삭제(Invalidation) 한다.
TTL 만료를 기다리지 않음 — 체육관에서 최신 루틴을 보장해야 하기 때문.

```
루틴 수정 / 삭제         → DEL plan:today:{userId}
                         → DEL plan:cache:{planId}

운동 항목 추가 / 수정 / 삭제 / 순서변경
                         → DEL plan:today:{userId}
                         → DEL plan:cache:{planId}
```

### TTL 10분 선택 근거

- 체육관 세션 내(~60분) 루틴이 거의 변경되지 않는 패턴에 적합
- 루틴 변경 즉시 Invalidation 처리하므로 Stale 데이터 노출 없음
- Redis 메모리: 루틴 JSON ~2KB × 동시 사용자 수 수준으로 부담 없음

### Redis 명령 예시

```bash
# 오늘의 루틴 캐시 저장
SET plan:today:42 '{...루틴JSON...}' EX 600

# 루틴 상세 캐시 저장
SET plan:cache:12 '{...루틴JSON...}' EX 600

# 루틴 수정 후 무효화
DEL plan:today:42
DEL plan:cache:12
```

---

## 테스트 케이스

### 단독 테스트 환경 설정 (X-User-Id Mock)

api-gateway 없이 plan-service를 단독으로 테스트할 때는 `X-User-Id` 헤더를 직접 주입한다.
Gateway의 JWT 검증 필터를 우회하는 것이므로, **테스트 환경에서만** 사용한다.

#### 통합 테스트 (`@SpringBootTest`)

```kotlin
// SecurityConfig에서 테스트 프로파일일 때 JWT 검증 비활성화
// application-test.yml: security.jwt.enabled=false

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class PlanControllerTest {

    @Test
    fun `오늘의 루틴 조회`() {
        mockMvc.get("/api/v1/plans/today") {
            header("X-User-Id", "42")  // Gateway가 주입했다고 가정
        }.andExpect {
            status { isOk() }
        }
    }
}
```

#### 수동 테스트 (HTTP 클라이언트 / curl)

```bash
# X-User-Id를 직접 헤더에 포함 (Gateway 없이 직접 호출)
curl -H "X-User-Id: 42" http://localhost:8082/api/v1/plans/today

# Authorization 헤더는 plan-service에서 검증하지 않으므로 생략 가능
```

> **주의:** 프로덕션에서는 외부에서 `X-User-Id` 헤더를 직접 전송해도 Gateway가 차단한다.
> plan-service 자체는 이 헤더 값을 신뢰하는 구조이므로, 단독 노출은 절대 금지.

---

### TC-01: 오늘의 루틴 조회 — 캐시 히트

**전제 조건:**
- 사용자 ID 42, 월요일(dayOfWeek=0) 루틴 존재
- Redis에 `plan:today:42` 이미 저장됨

**테스트 단계:**
1. `GET /api/v1/plans/today` (월요일)

**예상 결과:**
- ✅ 200 OK, `data.planId` 존재, `data.exercises` 배열 반환
- ✅ Redis 조회만 발생, DB 쿼리 없음 (Redis Mock 검증)
- ✅ 응답 시간 < 200ms

---

### TC-02: 오늘의 루틴 조회 — 캐시 미스

**전제 조건:**
- 사용자 ID 42, 월요일 루틴 DB에 존재
- Redis에 `plan:today:42` 없음

**테스트 단계:**
1. `GET /api/v1/plans/today` (월요일)

**예상 결과:**
- ✅ 200 OK, 올바른 루틴 반환
- ✅ DB 쿼리 발생
- ✅ Redis에 `plan:today:42`가 TTL 600으로 저장됨

---

### TC-03: 오늘의 루틴 없음

**전제 조건:**
- 사용자 ID 99, 오늘 요일에 배정된 루틴 없음

**테스트 단계:**
1. `GET /api/v1/plans/today`

**예상 결과:**
- ✅ 200 OK (404 아님)
- ✅ `data: null`

---

### TC-04: 루틴 수정 후 캐시 무효화

**전제 조건:**
- 사용자 ID 42, planId=12 존재
- Redis에 `plan:today:42`, `plan:cache:12` 존재

**테스트 단계:**
1. `PUT /api/v1/plans/12` (이름 변경)

**예상 결과:**
- ✅ 200 OK, 수정된 루틴 반환
- ✅ Redis에서 `plan:today:42` 삭제됨
- ✅ Redis에서 `plan:cache:12` 삭제됨
- ✅ 이후 `GET /api/v1/plans/today` 시 DB 재조회 발생

---

### TC-05: 타인 루틴 접근 차단

**전제 조건:**
- 사용자 A (userId=1)가 사용자 B (userId=2)의 planId=99에 접근

**테스트 단계:**
1. `GET /api/v1/plans/99` (X-User-Id: 1)

**예상 결과:**
- ✅ 403 Forbidden
- ✅ `error.code: "FORBIDDEN"`

---

### TC-06: 운동 추가 — 정상 (exercise-catalog 호출 없음)

**전제 조건:**
- 사용자 ID 42, planId=12, Redis 캐시 존재
- exercise-catalog 서비스 기동 여부와 무관

**테스트 단계:**
1. `POST /api/v1/plans/12/exercises`
   ```json
   {
     "exerciseId": 20,
     "exerciseName": "랫풀다운",
     "muscleGroup": "BACK",
     "targetSets": 4,
     "targetReps": 12
   }
   ```

**예상 결과:**
- ✅ 201 Created
- ✅ 응답에 `exerciseName: "랫풀다운"`, `muscleGroup: "BACK"` 포함
- ✅ DB `plan_exercises.exercise_name = "랫풀다운"`, `muscle_group = "BACK"` 저장 확인
- ✅ `plan:today:42`, `plan:cache:12` DEL 확인
- ✅ exercise-catalog HTTP 호출 발생하지 않음 (outbound call 모니터링)

---

### TC-07: 운동 순서 변경 — orderedIds 불일치

**전제 조건:**
- planId=12에 운동 4개 (id: 1,2,3,4)

**테스트 단계:**
1. `PUT /api/v1/plans/12/exercises/reorder`
   ```json
   { "orderedIds": [1, 2, 3] }
   ```
   (4개여야 하는데 3개)

**예상 결과:**
- ✅ 400 Bad Request
- ✅ `error.code: "VALIDATION_ERROR"`
- ✅ Redis 캐시 변경 없음

---

### TC-09: 운동 추가 — exerciseName 누락 (필수 필드 검증)

**전제 조건:**
- 사용자 ID 42, planId=12 존재

**테스트 단계:**
1. `POST /api/v1/plans/12/exercises`
   ```json
   {
     "exerciseId": 20,
     "muscleGroup": "BACK",
     "targetSets": 4
   }
   ```
   (`exerciseName` 필드 생략)

**예상 결과:**
- ✅ 400 Bad Request
- ✅ `error.code: "VALIDATION_ERROR"`
- ✅ DB 저장 없음, 캐시 변경 없음

---

### TC-10: 운동 추가 — muscleGroup 허용값 외

**전제 조건:**
- 사용자 ID 42, planId=12 존재

**테스트 단계:**
1. `POST /api/v1/plans/12/exercises`
   ```json
   {
     "exerciseId": 20,
     "exerciseName": "랫풀다운",
     "muscleGroup": "INVALID_GROUP"
   }
   ```

**예상 결과:**
- ✅ 400 Bad Request
- ✅ `error.code: "VALIDATION_ERROR"`

---

### TC-11: 오늘의 루틴 조회 — exercise-catalog 장애 상황에서도 정상 동작

**전제 조건:**
- 사용자 ID 42, planId=12, plan_exercises에 `exercise_name = "벤치프레스"` 저장됨
- exercise-catalog 서비스가 완전히 다운된 상태

**테스트 단계:**
1. `GET /api/v1/plans/today`

**예상 결과:**
- ✅ 200 OK, 정상 루틴 반환
- ✅ `exercises[0].exerciseName: "벤치프레스"` — plan_exercises에서 직접 조회됨
- ✅ exercise-catalog 호출 없음 (서비스 다운 상태이므로 호출 시 실패했을 것)

---

### TC-08: 루틴 삭제 (soft delete)

**전제 조건:**
- planId=12 존재, `is_active = true`

**테스트 단계:**
1. `DELETE /api/v1/plans/12`

**예상 결과:**
- ✅ 204 No Content
- ✅ DB에서 `is_active = false` 업데이트
- ✅ 실제 row 삭제 없음
- ✅ 이후 `GET /api/v1/plans/12` → 404 Not Found

---

## 비기능 요구사항

### 성능
- 오늘의 루틴 조회 P95 < **200ms** (Redis 캐시 히트 기준)
- 오늘의 루틴 조회 P95 < **500ms** (캐시 미스 + DB 조회 기준)
- 루틴 목록 조회 P95 < **300ms**
- 루틴 생성/수정 P95 < **400ms**

### 보안
- 모든 엔드포인트 JWT 인증 필수 (Gateway에서 검증)
- 사용자는 자신의 루틴만 접근 가능 (service layer에서 userId 검증)
- Spring Validation으로 모든 입력값 검증
- JPA Parameterized Query로 SQL Injection 방지
- Vault를 통한 DB 자격증명 주입 (환경변수 하드코딩 금지)

### 가용성
- 서비스 가용성 > 99.9%
- Redis 장애 시 fallback: DB 직접 조회 (성능 저하 허용, 서비스 중단 불가)

### 테스트
- 단위 테스트 커버리지 > 80%
- 오늘의 루틴 조회 캐시 히트/미스 통합 테스트 필수
- 캐시 무효화 시나리오 통합 테스트 필수
- 타인 루틴 접근 차단 테스트 필수

---

## 참고사항

- `exercise_id`는 exercise-catalog 서비스의 ID를 참조하지만 DB FK 제약조건 없음 (서비스 간 경계)
- `exerciseName`, `muscleGroup`은 운동 추가 시 클라이언트가 전송한 값을 `plan_exercises`에 비정규화 저장 → 조회 시 exercise-catalog 호출 불필요
- exercise-catalog에서 종목 정보가 수정되더라도 plan_exercises는 갱신되지 않음 (허용된 Stale)
- 요일 기준: 서버 타임존 (KST, UTC+9) 기준 현재 날짜의 요일 사용
- `dayOfWeek` 값: 0=월, 1=화, 2=수, 3=목, 4=금, 5=토, 6=일 (`java.time.DayOfWeek` 기준이 아닌 커스텀 매핑 — 구현 시 명시적 변환 필요)
- 관련 문서: `docs/api/plan-service.md`, `docs/database/mysql-schema.md`, `docs/database/redis-keys.md`

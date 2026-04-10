# Exercise Catalog 명세서

**작성자:** Spec Writer Agent
**작성일:** 2026-04-10
**버전:** 1.0
**상태:** Draft

## 목차
1. [개요](#개요)
2. [사용자 스토리](#사용자-스토리)
3. [인수 기준](#인수-기준)
4. [API 명세](#api-명세)
5. [데이터 모델](#데이터-모델)
6. [테스트 케이스](#테스트-케이스)
7. [비기능 요구사항](#비기능-요구사항)

---

## 개요

- **목적:** 사용자가 운동 루틴을 계획할 때 운동 종목을 검색·조회하고, 필요 시 커스텀 종목을 직접 등록할 수 있도록 한다
- **사용자:** 일반 회원 (인증된 사용자), 비인증 사용자 (부위 목록 조회만 가능)
- **우선순위:** High — plan-service가 exercise-catalog에 의존하므로 루틴 계획의 핵심 기반
- **서비스:** exercise-catalog (포트 8083)
- **데이터베이스:** MySQL (`gymplan_exercise`) + Elasticsearch

---

## 사용자 스토리

### US-EC-01: 운동 종목 검색

**As a** 운동을 계획 중인 사용자
**I want to** 이름, 부위, 장비로 운동 종목을 검색하고 싶다
**So that** 내 루틴에 추가할 적절한 운동을 빠르게 찾을 수 있다

#### 배경 (Background)
- 사용자는 웹/모바일에서 루틴을 구성할 때 운동 종목을 검색하여 추가한다
- 시스템에 약 240개 이상의 기본 종목이 등록되어 있으며, Elasticsearch를 통해 빠른 검색을 지원한다
- 한글/영문 이름 모두 검색 가능해야 한다

#### 시나리오 1: 키워드로 검색 (정상)
**Given** 사용자가 인증된 상태이고
**When** 검색어 "벤치"를 입력하면
**Then** "벤치프레스", "인클라인 벤치프레스" 등 이름에 "벤치"가 포함된 종목 목록이 반환된다
**And** 각 종목에 exerciseId, name, nameEn, muscleGroup, equipment, difficulty가 포함된다
**And** 페이징 정보(totalElements)가 포함된다

#### 시나리오 2: 부위 필터 검색
**Given** 사용자가 인증된 상태이고
**When** muscle=CHEST 필터로 검색하면
**Then** muscleGroup이 CHEST인 종목만 반환된다

#### 시나리오 3: 복합 필터 검색
**Given** 사용자가 인증된 상태이고
**When** muscle=CHEST, equipment=BARBELL로 검색하면
**Then** 가슴 부위 + 바벨 장비 조건을 모두 만족하는 종목만 반환된다

#### 시나리오 4: 검색 결과 없음
**Given** 사용자가 인증된 상태이고
**When** 존재하지 않는 키워드 "zzzxxx"로 검색하면
**Then** 빈 배열(content: [])이 반환된다
**And** totalElements: 0이 반환된다

#### 시나리오 5: 영문 검색
**Given** 사용자가 인증된 상태이고
**When** 검색어 "bench"를 입력하면
**Then** nameEn에 "bench"가 포함된 종목이 반환된다

---

### US-EC-02: 운동 종목 상세 조회

**As a** 운동 종목의 세부 정보가 궁금한 사용자
**I want to** 특정 종목의 상세 정보(설명, 동영상)를 볼 수 있다
**So that** 올바른 자세와 방법을 확인하고 루틴에 추가할지 판단할 수 있다

#### 시나리오 1: 상세 조회 성공
**Given** exerciseId=10인 종목이 존재하고
**When** GET /api/v1/exercises/10을 요청하면
**Then** name, nameEn, muscleGroup, equipment, difficulty, description, videoUrl이 포함된 상세 정보가 반환된다

#### 시나리오 2: 존재하지 않는 종목
**Given** exerciseId=99999인 종목이 존재하지 않고
**When** GET /api/v1/exercises/99999를 요청하면
**Then** 404 응답과 함께 에러 코드 EXERCISE_NOT_FOUND가 반환된다
**And** 에러 메시지 "운동 종목을 찾을 수 없습니다"가 반환된다

---

### US-EC-03: 커스텀 종목 생성

**As a** 기본 목록에 없는 운동을 추가하고 싶은 사용자
**I want to** 나만의 커스텀 운동 종목을 등록할 수 있다
**So that** 내 루틴에 개인화된 종목을 추가할 수 있다

#### 시나리오 1: 커스텀 종목 생성 성공
**Given** 사용자가 인증된 상태이고
**When** name, muscleGroup, equipment, difficulty를 입력하여 종목 생성을 요청하면
**Then** 201 응답과 함께 생성된 종목 정보가 반환된다
**And** is_custom=true, created_by={userId}로 저장된다

#### 시나리오 2: 필수 필드 누락
**Given** 사용자가 인증된 상태이고
**When** name 없이 종목 생성을 요청하면
**Then** 400 응답과 함께 VALIDATION_FAILED가 반환된다
**And** 누락된 필드에 대한 상세 메시지가 포함된다

#### 시나리오 3: 인증되지 않은 사용자
**Given** 사용자가 인증되지 않은 상태이고
**When** 종목 생성을 요청하면
**Then** 401 응답과 함께 AUTH_INVALID_TOKEN이 반환된다

---

### US-EC-04: 부위 목록 조회

**As a** 운동 종목을 필터링하려는 사용자 (비인증 포함)
**I want to** 사용 가능한 근육 부위 목록을 조회할 수 있다
**So that** 올바른 필터 값을 선택할 수 있다

#### 시나리오 1: 부위 목록 조회
**Given** 인증 여부와 관계없이
**When** GET /api/v1/exercises/muscle-groups를 요청하면
**Then** CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE, CARDIO 목록이 반환된다

---

## 인수 기준 (Acceptance Criteria)

### ✅ 기능 요구사항

#### 필수 (MUST)
- [ ] Elasticsearch 기반 종목 검색 (이름 한글/영문)
- [ ] muscleGroup, equipment 필터 지원
- [ ] 페이징 지원 (page, size 파라미터)
- [ ] 종목 상세 조회 (description, videoUrl 포함)
- [ ] 커스텀 종목 생성 (is_custom=true, created_by={userId})
- [ ] 부위 목록 조회 (인증 불필요)
- [ ] 존재하지 않는 종목 조회 시 404 + EXERCISE_NOT_FOUND 반환
- [ ] 모든 응답이 공통 응답 형식 준수 `{ success, data, error, timestamp }`

#### 선택 (SHOULD)
- [ ] difficulty 필터 지원
- [ ] 종목 이름 자동완성 (Elasticsearch suggest)
- [ ] 커스텀 종목 수정/삭제 (본인 생성 종목만)

#### 미래 (COULD)
- [ ] 인기 종목 추천 (analytics-service 연동)
- [ ] 종목별 평균 무게/횟수 통계 표시
- [ ] 운동 동영상 스트리밍 지원

### ✅ 보안 요구사항
- [ ] 종목 생성 API: 인증 필수 (JWT)
- [ ] 종목 검색/조회/부위 목록: 인증 필수 (부위 목록 제외)
- [ ] Gateway에서 전달된 X-User-Id를 커스텀 종목의 created_by에 사용
- [ ] 입력값 Spring Validation 검증 (name 길이, muscleGroup/equipment enum 값 등)
- [ ] SQL Injection 방지: JPA Parameterized Query만 사용

### ✅ 성능 요구사항
- [ ] Elasticsearch 검색 응답 P95 < 500ms
- [ ] 종목 상세 조회 P95 < 200ms
- [ ] 페이지당 최대 size=50 제한

### ✅ 테스트 요구사항
- [ ] 단위 테스트: Service 레이어 커버리지 > 80%
- [ ] 통합 테스트: 각 API 엔드포인트별 정상/에러 케이스
- [ ] Elasticsearch 검색 테스트 (한글, 영문, 복합 필터)

---

## API 명세

### GET /api/v1/exercises

운동 종목 검색 (Elasticsearch 기반)

**인증:** 필요 (Bearer Token)

**Headers:**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 | 제약조건 |
|----------|------|------|------|----------|
| `q` | String | N | 검색어 (이름 한글/영문) | 최대 100자 |
| `muscle` | String | N | 근육 부위 필터 | CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE, CARDIO |
| `equipment` | String | N | 장비 필터 | BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, BAND |
| `page` | Int | N | 페이지 번호 (기본 0) | >= 0 |
| `size` | Int | N | 페이지 크기 (기본 20) | 1~50 |

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "exerciseId": 10,
        "name": "벤치프레스",
        "nameEn": "Bench Press",
        "muscleGroup": "CHEST",
        "equipment": "BARBELL",
        "difficulty": "INTERMEDIATE"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 240,
    "totalPages": 12,
    "last": false
  },
  "error": null,
  "timestamp": "2026-04-10T09:00:00Z"
}
```

**Response 400 Bad Request:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "유효하지 않은 muscle 값입니다: INVALID_VALUE"
  },
  "timestamp": "2026-04-10T09:00:00Z"
}
```

---

### GET /api/v1/exercises/{exerciseId}

종목 상세 조회

**인증:** 필요 (Bearer Token)

**Path Parameters:**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `exerciseId` | Long | 종목 ID |

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "exerciseId": 10,
    "name": "벤치프레스",
    "nameEn": "Bench Press",
    "muscleGroup": "CHEST",
    "equipment": "BARBELL",
    "difficulty": "INTERMEDIATE",
    "description": "바벨을 이용한 가슴 운동의 기본이 되는 종목입니다. 평평한 벤치에 누워 바벨을 가슴까지 내렸다가 올립니다.",
    "videoUrl": "https://cdn.gymplan.io/videos/bench-press.mp4",
    "isCustom": false,
    "createdBy": null
  },
  "error": null,
  "timestamp": "2026-04-10T09:00:00Z"
}
```

**Response 404 Not Found:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "EXERCISE_NOT_FOUND",
    "message": "운동 종목을 찾을 수 없습니다"
  },
  "timestamp": "2026-04-10T09:00:00Z"
}
```

---

### POST /api/v1/exercises

커스텀 종목 생성

**인증:** 필요 (Bearer Token)

**Headers:**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "하프 스쿼트",
  "nameEn": "Half Squat",
  "muscleGroup": "LEGS",
  "equipment": "BARBELL",
  "difficulty": "BEGINNER",
  "description": "일반 스쿼트의 절반 깊이까지 내려가는 변형 동작",
  "videoUrl": null
}
```

| 필드 | 타입 | 필수 | 설명 | 제약조건 |
|------|------|------|------|----------|
| `name` | String | Y | 종목 이름 (한글) | 1~100자 |
| `nameEn` | String | N | 종목 이름 (영문) | 최대 100자 |
| `muscleGroup` | String | Y | 근육 부위 | CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE, CARDIO |
| `equipment` | String | Y | 장비 | BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, BAND |
| `difficulty` | String | Y | 난이도 | BEGINNER, INTERMEDIATE, ADVANCED |
| `description` | String | N | 종목 설명 | 최대 2000자 |
| `videoUrl` | String | N | 동영상 URL | 최대 500자, URL 형식 |

**Response 201 Created:**
```json
{
  "success": true,
  "data": {
    "exerciseId": 301,
    "name": "하프 스쿼트",
    "nameEn": "Half Squat",
    "muscleGroup": "LEGS",
    "equipment": "BARBELL",
    "difficulty": "BEGINNER",
    "description": "일반 스쿼트의 절반 깊이까지 내려가는 변형 동작",
    "videoUrl": null,
    "isCustom": true,
    "createdBy": 42
  },
  "error": null,
  "timestamp": "2026-04-10T09:00:00Z"
}
```

**Response 400 Bad Request:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "입력값 검증에 실패했습니다",
    "details": {
      "name": "종목 이름은 필수입니다",
      "muscleGroup": "유효하지 않은 근육 부위입니다"
    }
  },
  "timestamp": "2026-04-10T09:00:00Z"
}
```

**Response 401 Unauthorized:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH_INVALID_TOKEN",
    "message": "인증이 필요합니다"
  },
  "timestamp": "2026-04-10T09:00:00Z"
}
```

---

### GET /api/v1/exercises/muscle-groups

부위 목록 반환

**인증:** 불필요

**Response 200 OK:**
```json
{
  "success": true,
  "data": [
    "CHEST",
    "BACK",
    "SHOULDERS",
    "ARMS",
    "LEGS",
    "CORE",
    "CARDIO"
  ],
  "error": null,
  "timestamp": "2026-04-10T09:00:00Z"
}
```

---

### 에러 코드 요약

| 에러 코드 | HTTP 상태 | 설명 |
|----------|-----------|------|
| `EXERCISE_NOT_FOUND` | 404 | 요청한 종목이 존재하지 않음 |
| `VALIDATION_FAILED` | 400 | 입력값 검증 실패 (파라미터 오류, 필수값 누락) |
| `AUTH_INVALID_TOKEN` | 401 | 유효하지 않은 인증 토큰 |
| `AUTH_EXPIRED_TOKEN` | 401 | 만료된 인증 토큰 |
| `RATE_LIMIT_EXCEEDED` | 429 | API 호출 한도 초과 |
| `SERVER_ERROR` | 500 | 서버 내부 오류 |

---

## 데이터 모델

### exercises 테이블 (MySQL: `gymplan_exercise`)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 종목 고유 ID |
| `name` | VARCHAR(100) | NOT NULL | 종목 이름 (한글) |
| `name_en` | VARCHAR(100) | NULL | 종목 이름 (영문) |
| `muscle_group` | ENUM | NOT NULL | CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE, CARDIO |
| `equipment` | ENUM | NOT NULL | BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, BAND |
| `difficulty` | ENUM | NOT NULL | BEGINNER, INTERMEDIATE, ADVANCED |
| `description` | TEXT | NULL | 종목 설명 |
| `video_url` | VARCHAR(500) | NULL | 동영상 URL |
| `is_custom` | BOOLEAN | DEFAULT FALSE | 커스텀 종목 여부 |
| `created_by` | BIGINT | NULL | 커스텀 종목 생성자 userId |
| `created_at` | DATETIME | DEFAULT NOW() | 생성 시각 |

**인덱스:**
- `FULLTEXT INDEX ft_name (name, name_en)` — MySQL 풀텍스트 검색 (Elasticsearch 보조)

**Elasticsearch 인덱스 매핑:**
```json
{
  "exercises": {
    "mappings": {
      "properties": {
        "exerciseId": { "type": "long" },
        "name": { "type": "text", "analyzer": "nori" },
        "nameEn": { "type": "text", "analyzer": "standard" },
        "muscleGroup": { "type": "keyword" },
        "equipment": { "type": "keyword" },
        "difficulty": { "type": "keyword" }
      }
    }
  }
}
```

**동기화:**
- 종목 생성/수정 시 MySQL 저장 후 Elasticsearch에 비동기 색인
- MySQL이 원본(Source of Truth), Elasticsearch는 검색용 보조 저장소

---

## 테스트 케이스

### TC-EC-001: 키워드 검색 — 한글

**전제 조건:** "벤치프레스" 종목이 Elasticsearch에 색인되어 있음

**테스트 단계:**
1. GET /api/v1/exercises?q=벤치 요청

**예상 결과:**
- ✅ 200 OK
- ✅ content에 name이 "벤치"를 포함하는 종목들이 반환
- ✅ 각 종목에 exerciseId, name, nameEn, muscleGroup, equipment, difficulty 포함
- ✅ totalElements >= 1

---

### TC-EC-002: 키워드 검색 — 영문

**전제 조건:** "Bench Press" 종목이 Elasticsearch에 색인되어 있음

**테스트 단계:**
1. GET /api/v1/exercises?q=bench 요청

**예상 결과:**
- ✅ 200 OK
- ✅ content에 nameEn이 "bench"를 포함하는 종목들이 반환

---

### TC-EC-003: 부위 필터 검색

**전제 조건:** muscleGroup이 CHEST인 종목이 존재

**테스트 단계:**
1. GET /api/v1/exercises?muscle=CHEST 요청

**예상 결과:**
- ✅ 200 OK
- ✅ 모든 반환 종목의 muscleGroup이 CHEST

---

### TC-EC-004: 복합 필터 검색

**전제 조건:** muscleGroup=CHEST, equipment=BARBELL인 종목이 존재

**테스트 단계:**
1. GET /api/v1/exercises?muscle=CHEST&equipment=BARBELL 요청

**예상 결과:**
- ✅ 200 OK
- ✅ 모든 반환 종목의 muscleGroup=CHEST AND equipment=BARBELL

---

### TC-EC-005: 검색 결과 없음

**테스트 단계:**
1. GET /api/v1/exercises?q=zzzxxx123 요청

**예상 결과:**
- ✅ 200 OK
- ✅ content: []
- ✅ totalElements: 0

---

### TC-EC-006: 페이징

**전제 조건:** 종목이 25개 이상 존재

**테스트 단계:**
1. GET /api/v1/exercises?page=0&size=10 요청
2. GET /api/v1/exercises?page=1&size=10 요청

**예상 결과:**
- ✅ 1번 요청: content 배열 크기 = 10, page=0
- ✅ 2번 요청: content 배열 크기 = 10, page=1
- ✅ 1번과 2번의 종목이 중복되지 않음
- ✅ totalElements, totalPages가 정확히 계산됨

---

### TC-EC-007: 유효하지 않은 필터 값

**테스트 단계:**
1. GET /api/v1/exercises?muscle=INVALID_VALUE 요청

**예상 결과:**
- ✅ 400 Bad Request
- ✅ error.code: VALIDATION_FAILED

---

### TC-EC-008: 종목 상세 조회 — 성공

**전제 조건:** exerciseId=10인 종목이 존재

**테스트 단계:**
1. GET /api/v1/exercises/10 요청

**예상 결과:**
- ✅ 200 OK
- ✅ description, videoUrl 필드가 포함됨
- ✅ exerciseId=10

---

### TC-EC-009: 종목 상세 조회 — 존재하지 않는 종목

**테스트 단계:**
1. GET /api/v1/exercises/99999 요청

**예상 결과:**
- ✅ 404 Not Found
- ✅ error.code: EXERCISE_NOT_FOUND
- ✅ error.message: "운동 종목을 찾을 수 없습니다"

---

### TC-EC-010: 커스텀 종목 생성 — 성공

**전제 조건:** 사용자 인증됨 (userId=42)

**테스트 단계:**
1. POST /api/v1/exercises 요청
```json
{
  "name": "하프 스쿼트",
  "muscleGroup": "LEGS",
  "equipment": "BARBELL",
  "difficulty": "BEGINNER"
}
```

**예상 결과:**
- ✅ 201 Created
- ✅ isCustom: true
- ✅ createdBy: 42
- ✅ MySQL에 레코드 저장 확인
- ✅ Elasticsearch에 비동기 색인 확인

---

### TC-EC-011: 커스텀 종목 생성 — 필수 필드 누락

**전제 조건:** 사용자 인증됨

**테스트 단계:**
1. POST /api/v1/exercises 요청 (name 필드 없이)

**예상 결과:**
- ✅ 400 Bad Request
- ✅ error.code: VALIDATION_FAILED
- ✅ error.details에 누락 필드 정보 포함

---

### TC-EC-012: 커스텀 종목 생성 — 미인증

**전제 조건:** Authorization 헤더 없음

**테스트 단계:**
1. POST /api/v1/exercises 요청 (토큰 없이)

**예상 결과:**
- ✅ 401 Unauthorized
- ✅ error.code: AUTH_INVALID_TOKEN

---

### TC-EC-013: 부위 목록 조회

**테스트 단계:**
1. GET /api/v1/exercises/muscle-groups 요청 (토큰 없이)

**예상 결과:**
- ✅ 200 OK
- ✅ data 배열에 7개 항목: CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE, CARDIO

---

### TC-EC-014: size 제한 초과

**테스트 단계:**
1. GET /api/v1/exercises?size=100 요청

**예상 결과:**
- ✅ 400 Bad Request 또는 size가 최대값(50)으로 제한됨

---

## 비기능 요구사항

### 성능
- Elasticsearch 검색 응답 P95 < 500ms
- 종목 상세 조회 P95 < 200ms
- 페이지 크기 최대 50으로 제한하여 과도한 데이터 반환 방지

### 보안
- 종목 생성 API (POST)는 인증 필수
- Gateway에서 전달하는 X-User-Id 헤더를 커스텀 종목의 created_by에 사용
- 모든 입력값 Spring Validation으로 검증
- SQL Injection 방지: JPA Parameterized Query 사용

### 데이터 정합성
- MySQL이 Source of Truth, Elasticsearch는 검색 전용 보조
- 종목 생성 시 MySQL 저장 → Elasticsearch 비동기 색인
- Elasticsearch 색인 실패 시 재시도 처리 (MySQL 데이터 유지)

### 가용성
- 서비스 가용성 목표: > 99.9%
- Elasticsearch 장애 시 MySQL FULLTEXT 검색으로 폴백 가능

---

## 참고사항

- **관련 서비스:** plan-service가 exercise_id를 참조하므로, exercise-catalog 데이터가 plan-service의 루틴 구성에 필수
- **데이터 초기화:** 약 240개의 기본 운동 종목 시드 데이터 필요
- **Elasticsearch 분석기:** 한글 검색을 위해 nori 분석기 사용
- **API 명세 문서:** `docs/api/exercise-catalog.md`
- **DB 스키마 문서:** `docs/database/mysql-schema.md`

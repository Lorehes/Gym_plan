# Workout Service API

**Base**: `/api/v1/sessions`
**Port**: 8084
**DB**: MongoDB (`gymplan_workout`)
**이벤트**: Kafka 발행 (`workout.session.completed`, `workout.set.logged`)

---

## POST /api/v1/sessions

운동 세션 시작

**Request**
```json
{
  "planId":   12,
  "planName": "가슴/삼두 루틴"
}
```
`planId` 없이 보내면 자유 운동 세션

**Response 201**
```json
{
  "success": true,
  "data": {
    "sessionId": "665f1a2b3c4d5e6f7a8b9c0d",
    "startedAt": "2026-04-08T09:00:00Z",
    "status":    "IN_PROGRESS"
  }
}
```

**에러**: `SESSION_ALREADY_ACTIVE` (409) — 이미 진행 중인 세션 있을 때

---

## GET /api/v1/sessions/active

진행 중인 세션 조회

**Response 200** — 세션 전체 문서 반환. 없으면 `data: null`

---

## POST /api/v1/sessions/{sessionId}/sets

세트 기록 추가

**Request**
```json
{
  "exerciseId":   "10",
  "exerciseName": "벤치프레스",
  "muscleGroup":  "CHEST",
  "setNo":        1,
  "reps":         10,
  "weightKg":     70.0,
  "isSuccess":    true
}
```

**Response 201**

**사이드 이펙트**: `workout.set.logged` Kafka 이벤트 발행

---

## PUT /api/v1/sessions/{sessionId}/sets/{setNo}/{exerciseId}

세트 기록 수정 (운동 중 실수 정정)

---

## DELETE /api/v1/sessions/{sessionId}/sets/{setNo}/{exerciseId}

세트 기록 삭제

---

## POST /api/v1/sessions/{sessionId}/complete

운동 완료 처리

**Request**
```json
{ "notes": "오늘 컨디션 좋음" }
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "sessionId":   "665f...",
    "durationSec": 4200,
    "totalVolume": 3840.0,
    "totalSets":   16
  }
}
```

**사이드 이펙트**: `workout.session.completed` Kafka 이벤트 발행

---

## GET /api/v1/sessions/history

운동 히스토리 (페이징)

**Query Params**: `page`, `size`, `sort=startedAt,desc`

---

## GET /api/v1/sessions/{sessionId}

세션 상세 조회

---

## Kafka 이벤트 페이로드

### workout.session.completed
```json
{
  "sessionId":   "665f...",
  "userId":      "1",
  "startedAt":   "2026-04-08T09:00:00Z",
  "completedAt": "2026-04-08T10:10:00Z",
  "totalVolume": 3840.0,
  "totalSets":   16,
  "exercises":   ["CHEST", "TRICEPS"]
}
```

### workout.set.logged
```json
{
  "sessionId":   "665f...",
  "userId":      "1",
  "exerciseId":  "10",
  "muscleGroup": "CHEST",
  "weightKg":    70.0,
  "reps":        10,
  "volume":      700.0
}
```

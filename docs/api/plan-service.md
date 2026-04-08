# Plan Service API

**Base**: `/api/v1/plans`
**Port**: 8082
**DB**: MySQL (`gymplan_plan`), Redis (캐시)

---

## GET /api/v1/plans/today ⭐ 핵심 API

오늘의 루틴 조회. **Redis 캐시 우선** (목표: P95 < 200ms)

**인증**: Bearer Token 필요 (`X-User-Id` 헤더로 수신)

**캐시 키**: `plan:today:{userId}` TTL 10분

**Response 200**
```json
{
  "success": true,
  "data": {
    "planId":     12,
    "name":       "가슴/삼두 루틴",
    "dayOfWeek":  1,
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
  }
}
```

오늘 요일에 배정된 루틴이 없으면 `data: null`

---

## GET /api/v1/plans

내 루틴 목록 조회

**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "planId":     12,
      "name":       "가슴/삼두 루틴",
      "dayOfWeek":  1,
      "exerciseCount": 5,
      "isTemplate": false
    }
  ]
}
```

---

## POST /api/v1/plans

루틴 생성

**Request**
```json
{
  "name":        "가슴/삼두 루틴",
  "description": "월요일 루틴",
  "dayOfWeek":   1
}
```

**Response 201** — 생성된 루틴 반환

---

## GET /api/v1/plans/{planId}

루틴 상세 조회 (캐시 키: `plan:cache:{planId}`)

---

## PUT /api/v1/plans/{planId}

루틴 수정. **캐시 무효화**: `plan:today:{userId}`, `plan:cache:{planId}` DEL

---

## DELETE /api/v1/plans/{planId}

루틴 삭제 (soft delete: `is_active = false`)

---

## POST /api/v1/plans/{planId}/exercises

루틴에 운동 추가

**Request**
```json
{
  "exerciseId":     10,
  "orderIndex":     0,
  "targetSets":     4,
  "targetReps":     10,
  "targetWeightKg": 70.0,
  "restSeconds":    90,
  "notes":          "가슴 수축 집중"
}
```

---

## PUT /api/v1/plans/{planId}/exercises/{exerciseItemId}

운동 설정 수정 (세트 수, 무게, 휴식 시간 등)

---

## DELETE /api/v1/plans/{planId}/exercises/{exerciseItemId}

운동 제거

---

## PUT /api/v1/plans/{planId}/exercises/reorder

운동 순서 변경

**Request**
```json
{
  "orderedIds": [3, 1, 2, 4]
}
```

---

## 캐시 전략 요약

```
읽기:  Redis 조회 → 없으면 DB 조회 후 Redis 저장
쓰기:  DB 저장 후 관련 Redis 키 즉시 DEL (Cache Invalidation)
TTL:   10분 (체육관에서 충분한 유효 시간)
```

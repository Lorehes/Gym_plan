# Analytics Service API

**Base**: `/api/v1/analytics`
**Port**: 8085
**DB**: Elasticsearch
**입력**: Kafka 소비 (`workout.session.completed`, `workout.set.logged`)

---

## GET /api/v1/analytics/summary

운동 요약 통계

**Query Params**: `period=WEEK|MONTH` (기본 WEEK)

**Response 200**
```json
{
  "success": true,
  "data": {
    "period":        "WEEK",
    "totalSessions": 4,
    "totalVolume":   15200.0,
    "totalDurationSec": 14400,
    "avgDurationSec": 3600,
    "mostTrainedMuscle": "CHEST"
  }
}
```

---

## GET /api/v1/analytics/volume

부위별 볼륨 추이

**Query Params**: `period=WEEK|MONTH`, `muscle` (선택)

**Response 200**
```json
{
  "success": true,
  "data": [
    { "date": "2026-04-01", "muscle": "CHEST", "volume": 3840.0 },
    { "date": "2026-04-03", "muscle": "BACK",  "volume": 4200.0 }
  ]
}
```

---

## GET /api/v1/analytics/frequency

운동 빈도 캘린더

**Query Params**: `year`, `month`

**Response 200**
```json
{
  "success": true,
  "data": {
    "2026-04-01": { "sessionCount": 1, "totalVolume": 3840.0 },
    "2026-04-03": { "sessionCount": 1, "totalVolume": 4200.0 }
  }
}
```

---

## GET /api/v1/analytics/personal-records

종목별 최고 기록 (1RM 추정: Epley 공식 = weight × (1 + reps/30))

**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "exerciseId":    "10",
      "exerciseName":  "벤치프레스",
      "maxWeightKg":   80.0,
      "maxReps":       8,
      "estimated1RM":  106.7,
      "achievedAt":    "2026-04-01T09:30:00Z"
    }
  ]
}
```

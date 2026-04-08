# MongoDB 스키마

## DB: `gymplan_workout`

### Collection: `workout_sessions`

```json
{
  "_id":         "ObjectId",
  "userId":      "String",        // user-service의 userId (FK 역할)
  "planId":      "String",        // plan-service planId (nullable — 자유 운동)
  "planName":    "String",        // 비정규화 스냅샷 (삭제 후에도 기록 유지)
  "startedAt":   "ISODate",
  "completedAt": "ISODate",       // null = 아직 진행 중
  "totalVolume": "Number",        // 총 볼륨(kg) = sum(weight * reps)
  "totalSets":   "Number",
  "durationSec": "Number",
  "notes":       "String",
  "exercises": [
    {
      "exerciseId":   "String",
      "exerciseName": "String",   // 비정규화 스냅샷
      "muscleGroup":  "String",
      "sets": [
        {
          "setNo":       "Number",
          "reps":        "Number",
          "weightKg":    "Number",
          "isSuccess":   "Boolean",  // 목표 달성 여부
          "completedAt": "ISODate"
        }
      ]
    }
  ]
}
```

### 인덱스

```javascript
// 사용자별 히스토리 조회 (페이징)
db.workout_sessions.createIndex({ userId: 1, startedAt: -1 });

// 진행 중 세션 조회
db.workout_sessions.createIndex({ userId: 1, completedAt: 1 });

// Analytics 집계용
db.workout_sessions.createIndex({ userId: 1, "exercises.muscleGroup": 1 });
```

### 설계 원칙

- **비정규화 스냅샷**: `planName`, `exerciseName`을 문서에 포함 → 원본 삭제 후에도 기록 보존
- **단일 문서 조회**: 세션 전체를 하나의 문서로 → 운동 중 빠른 읽기/쓰기
- **유연한 세트 구조**: 종목마다 세트 수가 다르므로 배열로 관리

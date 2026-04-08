# Kafka 이벤트 명세

## 설정

```
브로커:       kafka:9092 (로컬: localhost:9092)
직렬화:       JSON (Spring Kafka JsonSerializer)
Consumer Group: gymplan-{service-name}
```

---

## 토픽 목록

| 토픽 | 발행 서비스 | 소비 서비스 | 발행 시점 |
|------|-----------|-----------|----------|
| `workout.session.completed` | workout-service | analytics-service, notification-service | 운동 완료 시 |
| `workout.set.logged` | workout-service | analytics-service | 세트 기록 시 |
| `user.registered` | user-service | notification-service | 회원가입 완료 시 |
| `plan.shared` | plan-service | analytics-service | 루틴 공유 시 |

---

## 이벤트 페이로드 상세

### workout.session.completed

```json
{
  "eventType":   "WORKOUT_SESSION_COMPLETED",
  "sessionId":   "665f1a2b3c4d5e6f7a8b9c0d",
  "userId":      "1",
  "planId":      "12",
  "planName":    "가슴/삼두 루틴",
  "startedAt":   "2026-04-08T09:00:00Z",
  "completedAt": "2026-04-08T10:10:00Z",
  "durationSec": 4200,
  "totalVolume": 3840.0,
  "totalSets":   16,
  "muscleGroups": ["CHEST", "ARMS"],
  "occurredAt":  "2026-04-08T10:10:05Z"
}
```

소비자 처리:
- `analytics-service` → Elasticsearch 색인, 통계 업데이트
- `notification-service` → 운동 완료 푸시 알림 (FCM)

---

### workout.set.logged

```json
{
  "eventType":   "WORKOUT_SET_LOGGED",
  "sessionId":   "665f1a2b3c4d5e6f7a8b9c0d",
  "userId":      "1",
  "exerciseId":  "10",
  "exerciseName":"벤치프레스",
  "muscleGroup": "CHEST",
  "setNo":       1,
  "reps":        10,
  "weightKg":    70.0,
  "volume":      700.0,
  "isSuccess":   true,
  "occurredAt":  "2026-04-08T09:15:00Z"
}
```

소비자 처리:
- `analytics-service` → 실시간 볼륨 누적, 개인 기록 갱신 체크

---

### user.registered

```json
{
  "eventType":  "USER_REGISTERED",
  "userId":     "1",
  "email":      "user@example.com",
  "nickname":   "철수",
  "occurredAt": "2026-04-08T08:00:00Z"
}
```

소비자 처리:
- `notification-service` → 가입 환영 푸시 알림

---

### plan.shared

```json
{
  "eventType":  "PLAN_SHARED",
  "planId":     "12",
  "userId":     "1",
  "planName":   "가슴/삼두 루틴",
  "occurredAt": "2026-04-08T07:00:00Z"
}
```

소비자 처리:
- `analytics-service` → 공유 통계 집계

---

## 공통 원칙

- **API 응답 후 비동기 발행**: 이벤트 발행은 API 응답 시간에 포함하지 않음
- **At-Least-Once**: 중복 처리 가능하도록 Consumer 멱등성 보장
- **DLQ**: 처리 실패 이벤트는 `{topic}.dlq` 토픽으로 이동
- **occurredAt**: 이벤트 발생 시각 (UTC ISO 8601)

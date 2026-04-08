# Notification Service API

**Base**: `/api/v1/notifications`
**Port**: 8086
**DB**: Redis (pub-sub)
**입력**: Kafka 소비 (`workout.session.completed`, `user.registered`)

---

## 휴식 타이머 (SSE)

세트 완료 시 클라이언트가 SSE로 타이머 이벤트를 수신합니다.

### GET /api/v1/notifications/timer/stream

휴식 타이머 SSE 스트림 구독

**인증**: Bearer Token 필요
**Content-Type**: `text/event-stream`

**응답 이벤트**
```
event: timer-start
data: {"sessionId":"665f...","restSeconds":90,"exerciseName":"벤치프레스"}

event: timer-end
data: {"sessionId":"665f...","message":"휴식 완료! 다음 세트를 시작하세요."}
```

**동작 흐름**
```
클라이언트 세트 완료
  → POST /sessions/{id}/sets (workout-service)
  → workout-service: Redis PUBLISH timer:{sessionId} {restSeconds}
  → notification-service: Redis SUBSCRIBE timer:{sessionId}
  → SSE 이벤트 전송 → 클라이언트 타이머 시작
```

---

## 알림 설정

### GET /api/v1/notifications/settings

알림 설정 조회

**Response 200**
```json
{
  "success": true,
  "data": {
    "restTimerEnabled":    true,
    "workoutCompleteAlert": true,
    "pushEnabled":         true
  }
}
```

### PUT /api/v1/notifications/settings

알림 설정 수정

**Request**
```json
{
  "restTimerEnabled":    true,
  "workoutCompleteAlert": false,
  "pushEnabled":         true
}
```

---

## 푸시 알림 (FCM)

직접 호출하는 API 없음. Kafka 이벤트 소비 후 자동 발송.

| Kafka 토픽 | 발송 조건 | 알림 내용 |
|-----------|---------|---------|
| `user.registered` | 회원가입 완료 | "GymPlan에 오신 것을 환영해요! 첫 루틴을 만들어보세요." |
| `workout.session.completed` | 운동 완료 | "오늘 운동 완료! 총 볼륨 {volume}kg, {duration}분 수고했어요 💪" |

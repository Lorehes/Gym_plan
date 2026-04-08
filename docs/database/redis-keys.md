# Redis 키 설계

## 네이밍 규칙

```
{서비스}:{도메인}:{식별자}
```

## 키 목록

| 키 패턴 | 값 타입 | 용도 | TTL |
|---------|---------|------|-----|
| `user:session:{userId}` | String (JWT) | 액세스 토큰 세션 | 30분 |
| `user:refresh:{tokenHash}` | String (userId) | Refresh Token | 7일 |
| `plan:today:{userId}` | JSON (루틴 전체) | 오늘의 루틴 캐시 | 10분 (EX 600) |
| `plan:cache:{planId}` | JSON (루틴) | 루틴 상세 캐시 | 10분 (EX 600) |
| `rate:{userId}:{endpoint}` | Counter | API Rate Limiting | 1분 |
| `rate:ip:{ip}` | Counter | IP Rate Limiting | 1분 |
| `timer:{sessionId}:{exerciseIdx}` | pub-sub channel | 휴식 타이머 | 일시적 |

## 서비스별 용도

### user-service
```
SET user:session:{userId} {accessToken} EX 1800
SET user:refresh:{tokenHash} {userId} EX 604800
DEL user:session:{userId}   # 로그아웃 시
```

### plan-service
```
# 오늘의 루틴 조회 캐시 (체육관에서 빠른 로딩 핵심)
GET plan:today:{userId}
SET plan:today:{userId} {planJson} EX 600   # 10분

# 루틴 수정 시 캐시 무효화
DEL plan:today:{userId}
DEL plan:cache:{planId}
```

### notification-service (휴식 타이머)
```
# 세트 완료 시 pub-sub 발행
PUBLISH timer:{sessionId} {restSeconds}

# 클라이언트가 구독
SUBSCRIBE timer:{sessionId}
```

## Rate Limiting 전략

```
# 유저당 300 req/min
INCR rate:{userId}:{minute}
EXPIRE rate:{userId}:{minute} 60

# IP당 100 req/min (Gateway에서 처리)
INCR rate:ip:{ip}:{minute}
EXPIRE rate:ip:{ip}:{minute} 60
```

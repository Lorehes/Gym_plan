# API 공통 규칙

## 기본 정보

- **Base URL**: `http://localhost:8080/api/v1` (로컬), `https://api.gymplan.io/api/v1` (운영)
- **인증**: Bearer Token (JWT Access Token)
- **Content-Type**: `application/json`
- **인코딩**: UTF-8

## 공통 응답 형식

### 성공
```json
{
  "success": true,
  "data": { },
  "error": null,
  "timestamp": "2026-04-08T09:00:00Z"
}
```

### 실패
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "사용자를 찾을 수 없습니다.",
    "details": {}
  },
  "timestamp": "2026-04-08T09:00:00Z"
}
```

## HTTP 상태 코드

| 코드 | 의미 |
|------|------|
| 200 | 성공 (조회, 수정) |
| 201 | 생성 성공 |
| 204 | 성공 (삭제, 응답 바디 없음) |
| 400 | 잘못된 요청 (입력값 오류) |
| 401 | 인증 필요 (토큰 없음/만료) |
| 403 | 권한 없음 |
| 404 | 리소스 없음 |
| 409 | 충돌 (중복 이메일 등) |
| 429 | Rate Limit 초과 |
| 500 | 서버 내부 오류 |

## 에러 코드

| 에러 코드 | 서비스 | 설명 |
|----------|--------|------|
| `AUTH_INVALID_TOKEN` | user | 유효하지 않은 토큰 |
| `AUTH_EXPIRED_TOKEN` | user | 만료된 토큰 |
| `AUTH_DUPLICATE_EMAIL` | user | 중복 이메일 |
| `PLAN_NOT_FOUND` | plan | 루틴 없음 |
| `PLAN_ACCESS_DENIED` | plan | 다른 사용자 루틴 접근 |
| `EXERCISE_NOT_FOUND` | exercise | 종목 없음 |
| `SESSION_NOT_FOUND` | workout | 세션 없음 |
| `SESSION_ALREADY_ACTIVE` | workout | 이미 진행 중인 세션 |
| `RATE_LIMIT_EXCEEDED` | gateway | Rate Limit 초과 |

## 페이징

```json
// Request
GET /api/v1/sessions/history?page=0&size=20&sort=startedAt,desc

// Response (페이징 포함)
{
  "success": true,
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 145,
    "totalPages": 8,
    "last": false
  }
}
```

## 인증 헤더

```
Authorization: Bearer {accessToken}
```

Gateway가 JWT 검증 후 하위 서비스에 아래 헤더를 추가 전달:
```
X-User-Id: {userId}
X-User-Email: {email}
```

하위 서비스는 `X-User-Id`를 신뢰하고 사용. JWT를 직접 검증하지 않음.

# User Service API

**Base**: `/api/v1/auth`, `/api/v1/users`
**Port**: 8081
**DB**: MySQL (`gymplan_user`), Redis

---

## POST /api/v1/auth/register

회원가입

**인증**: 불필요

**Request**
```json
{
  "email":    "user@example.com",
  "password": "P@ssw0rd123!",
  "nickname": "철수"
}
```

**Response 201**
```json
{
  "success": true,
  "data": {
    "userId":       1,
    "email":        "user@example.com",
    "nickname":     "철수",
    "accessToken":  "eyJ...",
    "refreshToken": "eyJ..."
  }
}
```

**에러**: `AUTH_DUPLICATE_EMAIL` (409)

---

## POST /api/v1/auth/login

로그인 및 JWT 발급

**Request**
```json
{
  "email":    "user@example.com",
  "password": "P@ssw0rd123!"
}
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "userId":       1,
    "nickname":     "철수",
    "accessToken":  "eyJ...",
    "refreshToken": "eyJ..."
  }
}
```

---

## POST /api/v1/auth/refresh

Access Token 갱신

**Request**
```json
{ "refreshToken": "eyJ..." }
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "accessToken":  "eyJ...(새 토큰)",
    "refreshToken": "eyJ...(rotation된 새 토큰)"
  }
}
```

---

## POST /api/v1/auth/logout

**인증**: Bearer Token 필요

**Response 204** (바디 없음)

---

## GET /api/v1/users/me

내 프로필 조회

**인증**: Bearer Token 필요

**Response 200**
```json
{
  "success": true,
  "data": {
    "userId":     1,
    "email":      "user@example.com",
    "nickname":   "철수",
    "profileImg": null,
    "createdAt":  "2026-04-08T09:00:00Z"
  }
}
```

---

## PUT /api/v1/users/me

프로필 수정

**Request**
```json
{
  "nickname":   "영희",
  "profileImg": "https://..."
}
```

**Response 200** — 수정된 프로필 반환

---

## JWT 전략

| 항목 | 값 |
|------|----|
| 알고리즘 | RS256 |
| Access Token 만료 | 30분 |
| Refresh Token 만료 | 7일 |
| Refresh Token Rotation | 사용 시마다 재발급 |
| 저장소 | Redis (`user:session:{userId}`, `user:refresh:{tokenHash}`) |

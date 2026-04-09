# User Service 기능 명세서

**작성자:** Spec Writer
**작성일:** 2026-04-09
**버전:** 1.0
**상태:** Draft
**관련 문서:** `docs/api/user-service.md`, `docs/context/security-guide.md`, `docs/database/mysql-schema.md`

---

## 목차
1. [개요](#1-개요)
2. [사용자 스토리](#2-사용자-스토리)
3. [기능 범위](#3-기능-범위)
4. [인수 기준](#4-인수-기준-acceptance-criteria)
5. [API 명세 요약](#5-api-명세-요약)
6. [데이터 모델](#6-데이터-모델)
7. [Given-When-Then 테스트 케이스](#7-given-when-then-테스트-케이스)
8. [비기능 요구사항](#8-비기능-요구사항)
9. [에러 코드 카탈로그](#9-에러-코드-카탈로그)

---

## 1. 개요

| 항목 | 내용 |
|------|------|
| **서비스명** | user-service |
| **목적** | GymPlan의 인증/인가 핵심 게이트로, 회원가입·로그인·토큰 발급·프로필 관리를 책임진다. |
| **주 사용자** | 모바일 앱(React Native), 웹 앱(React), 내부 서비스(JWT 검증된 X-User-Id 헤더 기반) |
| **포트** | 8081 |
| **DB** | MySQL `gymplan_user`, Redis (세션·Refresh Token 저장) |
| **우선순위** | High (Phase 1 필수) |
| **상위 의존성** | api-gateway (JWT 검증), Vault (RS256 키), Redis |

### 비즈니스 가치
- 모든 GymPlan 서비스에 대한 단일 인증 진입점 제공
- JWT 기반 무상태 인증으로 마이크로서비스 간 신뢰 체인 구축
- Refresh Token Rotation으로 탈취 토큰의 피해 범위 최소화

---

## 2. 사용자 스토리

### US-1: 회원가입
**As a** 신규 사용자
**I want to** 이메일·비밀번호·닉네임으로 계정을 만들기
**So that** GymPlan에서 나만의 운동 루틴을 계획·기록할 수 있다.

### US-2: 로그인
**As a** 등록된 사용자
**I want to** 이메일과 비밀번호로 로그인하고 JWT를 발급받기
**So that** 모바일/웹 클라이언트에서 인증된 요청을 보낼 수 있다.

### US-3: 토큰 갱신
**As a** 로그인 상태의 사용자
**I want to** Access Token이 만료되어도 Refresh Token으로 재발급받기
**So that** 30분마다 다시 로그인하지 않아도 된다.

### US-4: 로그아웃
**As a** 로그인 상태의 사용자
**I want to** 명시적으로 로그아웃하기
**So that** 공용 기기나 분실 상황에서 토큰을 무효화할 수 있다.

### US-5: 내 프로필 조회
**As a** 로그인 상태의 사용자
**I want to** 내 계정 정보(이메일, 닉네임, 가입일)를 조회하기
**So that** 마이페이지에서 내 정보를 확인할 수 있다.

### US-6: 프로필 수정
**As a** 로그인 상태의 사용자
**I want to** 닉네임과 프로필 이미지를 변경하기
**So that** 내 정체성을 자유롭게 표현할 수 있다.

---

## 3. 기능 범위

### In Scope
- 이메일/비밀번호 회원가입 및 로그인
- BCrypt 비밀번호 해싱 (cost ≥ 10)
- RS256 JWT 발급 (Access 30분 / Refresh 7일)
- Refresh Token Rotation
- Redis 기반 세션 및 Refresh Token 저장
- 프로필 조회/수정
- 입력 검증 (이메일 형식, 비밀번호·닉네임 길이)

### Out of Scope (Phase 2 이후)
- 소셜 로그인 (Google, Apple, Kakao)
- 이메일 인증 (Verification)
- 비밀번호 재설정 (Password Reset)
- 2FA / 생체 인증
- 계정 삭제 (Soft Delete)
- 관리자 권한 (RBAC)

---

## 4. 인수 기준 (Acceptance Criteria)

### 4.1 기능 요구사항 (MUST)

#### 회원가입
- [ ] 이메일은 RFC 5322 형식을 만족해야 한다 (`@field:Email` 검증).
- [ ] 비밀번호는 8자 이상 20자 이하여야 한다.
- [ ] 닉네임은 2자 이상 20자 이하여야 한다.
- [ ] 동일 이메일이 이미 존재하면 `AUTH_DUPLICATE_EMAIL` (409)를 반환한다.
- [ ] 비밀번호는 BCrypt(cost ≥ 10)로 해싱되어 저장된다 (평문 저장 금지).
- [ ] 회원가입 성공 시 즉시 Access Token과 Refresh Token을 함께 발급한다.
- [ ] `users.status`는 기본값 `ACTIVE`로 생성된다.

#### 로그인
- [ ] 이메일이 존재하지 않거나 비밀번호가 틀리면 동일한 응답(`AUTH_INVALID_CREDENTIALS`, 401)을 반환한다 (사용자 존재 여부 노출 방지).
- [ ] `status = INACTIVE | BANNED`인 계정은 로그인할 수 없다 (`AUTH_ACCOUNT_DISABLED`, 403).
- [ ] 로그인 성공 시 새로운 Access/Refresh Token을 발급하고 Redis에 세션을 저장한다.
- [ ] 같은 사용자의 이전 Refresh Token은 무효화하지 않는다 (다중 디바이스 지원).

#### 토큰 갱신
- [ ] Refresh Token이 Redis에 존재하지 않거나 만료되었으면 `AUTH_INVALID_REFRESH_TOKEN` (401)을 반환한다.
- [ ] 갱신 성공 시 새로운 Access Token과 새로운 Refresh Token을 함께 반환한다 (Rotation).
- [ ] 사용된 Refresh Token은 즉시 Redis에서 삭제되어 재사용 불가하다.
- [ ] 이미 사용된(=Rotation된) Refresh Token으로 재요청 시 `AUTH_REFRESH_TOKEN_REUSED` (401) 반환 + 해당 사용자의 모든 세션을 무효화한다 (탈취 의심 대응).

#### 로그아웃
- [ ] 유효한 Access Token이 없으면 401을 반환한다.
- [ ] 성공 시 해당 사용자의 현재 세션과 Refresh Token을 Redis에서 삭제한다.
- [ ] 응답은 204 No Content (바디 없음).

#### 프로필 조회
- [ ] 인증되지 않은 요청은 401을 반환한다.
- [ ] 응답에는 `password` 필드가 절대 포함되지 않는다.
- [ ] `X-User-Id` 헤더로 식별된 사용자의 정보만 조회된다 (타인 조회 차단).

#### 프로필 수정
- [ ] 닉네임만 또는 프로필 이미지 URL만 부분 수정이 가능하다.
- [ ] 닉네임은 2~20자 제약을 만족해야 한다.
- [ ] 프로필 이미지는 `https://`로 시작하는 URL만 허용한다 (SSRF 방지).
- [ ] 이메일과 비밀번호는 이 엔드포인트로 변경할 수 없다.
- [ ] `updated_at`이 자동 갱신된다.

### 4.2 보안 요구사항 (MUST)

- [ ] JWT 알고리즘은 RS256 고정. 비밀키는 Vault에서 런타임 주입된다.
- [ ] 비밀번호 검증 실패 메시지는 "이메일 또는 비밀번호가 올바르지 않습니다"로 통일한다 (계정 존재 여부 노출 금지).
- [ ] 모든 로그에서 비밀번호·토큰·이메일은 마스킹되어 출력된다.
- [ ] Gateway 통해 들어오지 않은 `X-User-Id` 헤더 직접 주입은 차단된다.
- [ ] Rate Limiting: IP당 100 req/min, 사용자당 300 req/min (Gateway 책임).
- [ ] 로그인 엔드포인트는 동일 이메일 기준 1분당 5회 실패 시 5분간 잠금(브루트포스 방어).
- [ ] CORS 화이트리스트만 허용 (`http://localhost:3000`, `https://gymplan.io`, `capacitor://localhost`).
- [ ] DB 비밀번호·JWT 키는 환경변수(`${...}`) 참조만 사용한다.

### 4.3 성능 요구사항 (MUST)

- [ ] 로그인 API P95 응답 시간 < 500ms.
- [ ] 토큰 갱신 API P95 응답 시간 < 200ms.
- [ ] 프로필 조회 API P95 응답 시간 < 200ms.
- [ ] BCrypt cost는 10으로 고정 (성능과 보안 균형).

### 4.4 테스트 요구사항 (MUST)

- [ ] 단위 테스트 라인 커버리지 ≥ 80%.
- [ ] 모든 엔드포인트에 대한 통합 테스트(@SpringBootTest) 존재.
- [ ] Testcontainers로 실제 MySQL/Redis 사용 (모킹 금지).
- [ ] 에러 시나리오(중복 이메일, 잘못된 비밀번호, 토큰 만료, Rotation 재사용) 모두 커버.

---

## 5. API 명세 요약

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/v1/auth/register` | ❌ | 회원가입 + 토큰 발급 |
| POST | `/api/v1/auth/login` | ❌ | 로그인 + 토큰 발급 |
| POST | `/api/v1/auth/refresh` | ❌ (Refresh Token) | Access Token 재발급 (Rotation) |
| POST | `/api/v1/auth/logout` | ✅ Bearer | 세션·Refresh Token 폐기 |
| GET | `/api/v1/users/me` | ✅ Bearer | 내 프로필 조회 |
| PUT | `/api/v1/users/me` | ✅ Bearer | 프로필 부분 수정 |

> 각 엔드포인트의 상세 Request/Response 스키마는 `docs/api/user-service.md` 참조.

### 공통 응답 포맷
```json
{
  "success": true | false,
  "data":    { ... } | null,
  "error":   { "code": "...", "message": "..." } | null,
  "timestamp": "2026-04-09T12:34:56Z"
}
```

---

## 6. 데이터 모델

### 6.1 MySQL — `users`

`docs/database/mysql-schema.md`와 동기화. 본 서비스가 소유하는 유일한 테이블.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 사용자 고유 ID |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL | 로그인 ID, 소문자 정규화 저장 |
| `password` | VARCHAR(255) | NOT NULL | BCrypt 해시 (cost 10) |
| `nickname` | VARCHAR(50) | NOT NULL | 표시 이름 (2~20자) |
| `profile_img` | VARCHAR(500) | NULL | HTTPS URL만 허용 |
| `status` | ENUM | DEFAULT 'ACTIVE' | ACTIVE / INACTIVE / BANNED |
| `created_at` | DATETIME | DEFAULT NOW() | 가입 시각 (UTC) |
| `updated_at` | DATETIME | DEFAULT NOW() ON UPDATE NOW() | 마지막 수정 시각 |

**인덱스:** `email` UNIQUE 인덱스 (자동) — 로그인 시 단일 조회.

### 6.2 Redis 키 설계

| 키 패턴 | 값 | TTL | 용도 |
|---------|-----|-----|------|
| `user:refresh:{tokenHash}` | userId | 7일 | Refresh Token 유효성 검증 |
| `user:refresh:index:{userId}` | SET of tokenHash | 7일 | 사용자별 Refresh Token 인덱스 (전체 무효화용) |
| `user:login:fail:{email}` | 카운터 | 1분 | 브루트포스 실패 카운터 |
| `user:locked:{email}` | "1" | 5분 | 잠금 상태 |

> `tokenHash`는 SHA-256 해시(16진수 소문자). 원본 토큰은 Redis에 저장하지 않는다.
>
> **Phase 1 범위 조정**: 초기 드래프트에는 `user:session:{userId}:{sessionId}` 키가 포함되어 있었으나,
> 현재 디자인에서는 Refresh Token Rotation 만으로 세션 수명을 관리한다.
> 세션 단위 식별자는 Gateway 가 sessionId 헤더를 전달하도록 확장되는 **Phase 2** 에서 도입한다.
> 그 전까지 "단일 디바이스만 로그아웃" 요구가 발생하면 이 표에 키를 다시 추가하고 AuthService.logout() 을 확장해야 한다.

---

## 7. Given-When-Then 테스트 케이스

### TC-001: 정상 회원가입

**Given**
- DB에 `test@example.com` 계정이 존재하지 않는다.

**When**
- 클라이언트가 `POST /api/v1/auth/register`에 다음 바디로 요청한다:
  ```json
  { "email": "test@example.com", "password": "P@ssw0rd123!", "nickname": "철수" }
  ```

**Then**
- HTTP 201을 반환한다.
- 응답 `data`에 `userId`, `accessToken`, `refreshToken`이 포함된다.
- DB의 `users` 테이블에 신규 row가 1건 생성된다.
- 저장된 `password` 컬럼은 BCrypt 해시(`$2a$10$...`)이며 평문 `P@ssw0rd123!`이 아니다.
- Redis에 해당 `userId`의 세션과 Refresh Token이 저장된다.

---

### TC-002: 중복 이메일 회원가입 실패

**Given**
- DB에 `test@example.com` 계정이 이미 존재한다.

**When**
- 동일 이메일로 `POST /api/v1/auth/register`를 호출한다.

**Then**
- HTTP 409를 반환한다.
- 응답 `error.code`는 `AUTH_DUPLICATE_EMAIL`이다.
- DB에는 신규 row가 추가되지 않는다.

---

### TC-003: 비밀번호 길이 검증 실패

**Given**
- 어떤 사용자도 인증되어 있지 않다.

**When**
- `POST /api/v1/auth/register`에 `password: "abc"` (3자)로 요청한다.

**Then**
- HTTP 400을 반환한다.
- 응답 `error.code`는 `VALIDATION_FAILED`이다.
- DB에는 어떤 row도 생성되지 않는다.

---

### TC-004: 정상 로그인

**Given**
- DB에 `test@example.com` 계정이 존재하고 비밀번호는 `P@ssw0rd123!`이다.
- `users.status`는 `ACTIVE`이다.

**When**
- `POST /api/v1/auth/login`에 올바른 이메일/비밀번호로 요청한다.

**Then**
- HTTP 200을 반환한다.
- 응답에 `accessToken`과 `refreshToken`이 포함된다.
- Access Token을 RS256 공개키로 검증 시 `sub` 클레임이 해당 `userId`이다.
- Access Token의 `exp`는 발급 시각 + 30분이다.
- Redis에 `user:refresh:{tokenHash}` 키가 7일 TTL로 생성된다.

---

### TC-005: 잘못된 비밀번호 로그인 실패

**Given**
- DB에 `test@example.com` 계정이 존재한다.

**When**
- `POST /api/v1/auth/login`에 `password: "WrongPassword!"`로 요청한다.

**Then**
- HTTP 401을 반환한다.
- 응답 `error.code`는 `AUTH_INVALID_CREDENTIALS`이다.
- 응답 메시지는 "이메일 또는 비밀번호가 올바르지 않습니다" (계정 존재 여부 노출 금지).
- Redis의 `user:login:fail:test@example.com` 카운터가 1 증가한다.

---

### TC-006: 존재하지 않는 이메일 로그인 실패

**Given**
- DB에 `unknown@example.com` 계정이 존재하지 않는다.

**When**
- 해당 이메일로 로그인 요청한다.

**Then**
- HTTP 401을 반환한다.
- `error.code`와 메시지는 TC-005와 **완전히 동일**해야 한다.

---

### TC-007: 브루트포스 방어 — 5회 실패 후 계정 잠금

**Given**
- DB에 `test@example.com` 계정이 존재한다.
- 1분 내에 잘못된 비밀번호로 5회 로그인 시도한 직후 상태이다.

**When**
- 6번째로 로그인 요청한다 (이번엔 올바른 비밀번호여도).

**Then**
- HTTP 429를 반환한다.
- 응답 `error.code`는 `AUTH_ACCOUNT_LOCKED`이다.
- Redis에 `user:locked:test@example.com` 키가 5분 TTL로 존재한다.
- 5분 후 자동으로 잠금이 해제되어 다시 로그인 가능하다.

---

### TC-008: 비활성 계정 로그인 차단

**Given**
- DB에 `banned@example.com` 계정이 존재하며 `status = BANNED`이다.

**When**
- 올바른 비밀번호로 로그인 요청한다.

**Then**
- HTTP 403을 반환한다.
- 응답 `error.code`는 `AUTH_ACCOUNT_DISABLED`이다.
- Redis에 새 세션이 생성되지 않는다.

---

### TC-009: Refresh Token으로 Access Token 갱신 (정상)

**Given**
- 사용자가 로그인되어 있고 유효한 Refresh Token `RT_OLD`를 가지고 있다.
- Redis에 `user:refresh:{hash(RT_OLD)}` 키가 존재한다.

**When**
- `POST /api/v1/auth/refresh`에 `{ "refreshToken": "RT_OLD" }`로 요청한다.

**Then**
- HTTP 200을 반환한다.
- 응답에 새로운 `accessToken`과 새로운 `refreshToken` (`RT_NEW`)이 포함된다.
- `RT_NEW != RT_OLD`이다.
- Redis에서 `user:refresh:{hash(RT_OLD)}`가 삭제된다.
- Redis에 `user:refresh:{hash(RT_NEW)}`가 7일 TTL로 생성된다.

---

### TC-010: 만료된 Refresh Token 사용

**Given**
- Refresh Token `RT_EXPIRED`의 `exp`가 현재 시각보다 이전이다.
- Redis의 해당 키도 TTL 만료로 사라진 상태이다.

**When**
- `POST /api/v1/auth/refresh`에 `RT_EXPIRED`로 요청한다.

**Then**
- HTTP 401을 반환한다.
- 응답 `error.code`는 `AUTH_INVALID_REFRESH_TOKEN`이다.

---

### TC-011: Refresh Token 재사용 탐지 (탈취 의심)

**Given**
- 사용자가 정상적으로 `RT_OLD` → `RT_NEW`로 Rotation을 마친 상태이다.
- 공격자가 탈취한 `RT_OLD`로 다시 갱신을 시도한다.

**When**
- `POST /api/v1/auth/refresh`에 이미 사용된 `RT_OLD`로 요청한다.

**Then**
- HTTP 401을 반환한다.
- 응답 `error.code`는 `AUTH_REFRESH_TOKEN_REUSED`이다.
- 해당 사용자의 **모든 활성 세션**(`user:session:{userId}:*`, `user:refresh:*`)이 Redis에서 삭제된다.
- 사용자는 모든 디바이스에서 강제 로그아웃된다.

---

### TC-012: 정상 로그아웃

**Given**
- 사용자가 로그인되어 있고 유효한 Access Token을 헤더에 가지고 있다.

**When**
- `POST /api/v1/auth/logout`에 `Authorization: Bearer {accessToken}`로 요청한다.

**Then**
- HTTP 204 No Content를 반환한다.
- 응답 바디는 비어 있다.
- Redis의 해당 사용자 세션 키가 삭제된다.
- Redis의 Refresh Token 키도 삭제된다.
- 이후 동일 Access Token으로 보호된 엔드포인트 호출 시 401을 받는다.

---

### TC-013: 인증 없이 로그아웃 시도

**Given**
- 어떤 인증 토큰도 제공되지 않는다.

**When**
- `POST /api/v1/auth/logout`을 호출한다.

**Then**
- HTTP 401을 반환한다.
- Redis 상태는 변경되지 않는다.

---

### TC-014: 내 프로필 조회 (정상)

**Given**
- 사용자가 로그인되어 있고 Gateway가 `X-User-Id: 1` 헤더를 주입한 상태이다.

**When**
- `GET /api/v1/users/me`를 호출한다.

**Then**
- HTTP 200을 반환한다.
- 응답 `data`에 `userId`, `email`, `nickname`, `profileImg`, `createdAt`이 포함된다.
- 응답 어디에도 `password` 필드가 존재하지 않는다.

---

### TC-015: X-User-Id 헤더 위조 시도

**Given**
- 사용자가 인증되지 않았다.
- 클라이언트가 직접 `X-User-Id: 999` 헤더를 주입해 요청한다.

**When**
- `GET /api/v1/users/me`를 호출한다.

**Then**
- Gateway 단에서 차단되어 HTTP 401을 반환한다.
- user-service는 호출 자체를 받지 않는다.

---

### TC-016: 프로필 수정 (닉네임만)

**Given**
- 사용자 `id=1`이 로그인되어 있고 현재 닉네임은 "철수"이다.

**When**
- `PUT /api/v1/users/me`에 `{ "nickname": "영희" }`로 요청한다.

**Then**
- HTTP 200을 반환한다.
- 응답의 `nickname`은 "영희"이다.
- DB의 `users.nickname`이 "영희"로 갱신된다.
- DB의 `users.updated_at`이 갱신된다.
- DB의 `users.profile_img`는 변경되지 않는다.

---

### TC-017: 잘못된 닉네임 길이로 수정 시도

**Given**
- 사용자가 로그인되어 있다.

**When**
- `PUT /api/v1/users/me`에 `{ "nickname": "A" }` (1자)로 요청한다.

**Then**
- HTTP 400을 반환한다.
- `error.code`는 `VALIDATION_FAILED`이다.
- DB는 변경되지 않는다.

---

### TC-018: HTTP 프로필 이미지 URL 거부 (SSRF 방어)

**Given**
- 사용자가 로그인되어 있다.

**When**
- `PUT /api/v1/users/me`에 `{ "profileImg": "http://internal.local/admin" }`로 요청한다.

**Then**
- HTTP 400을 반환한다.
- `error.code`는 `VALIDATION_FAILED`이며 메시지는 "프로필 이미지는 https URL만 허용됩니다"이다.

---

### TC-019: 로그에 비밀번호 노출 금지

**Given**
- 로그 캡처가 활성화된 상태이다.

**When**
- 회원가입 또는 로그인 요청을 처리한다.

**Then**
- 어떤 로그 라인에도 평문 비밀번호 `P@ssw0rd123!`이 출력되지 않는다.
- 어떤 로그 라인에도 Access/Refresh Token 원문이 출력되지 않는다.
- 이메일은 마스킹된 형태(`t***@example.com`)로만 출력된다.

---

### TC-020: 동시 회원가입 경합 (동일 이메일)

**Given**
- DB에 `race@example.com` 계정이 존재하지 않는다.

**When**
- 두 클라이언트가 동시에 동일 이메일로 회원가입을 요청한다.

**Then**
- 정확히 1건만 HTTP 201로 성공한다.
- 다른 1건은 HTTP 409 (`AUTH_DUPLICATE_EMAIL`)로 실패한다.
- DB의 `users` 테이블에 row는 1건만 존재한다 (UNIQUE 제약 보장).

---

## 8. 비기능 요구사항

### 8.1 성능
| 지표 | 목표 |
|------|------|
| 로그인 P95 | < 500ms |
| 토큰 갱신 P95 | < 200ms |
| 프로필 조회 P95 | < 200ms |
| 회원가입 P95 | < 800ms (BCrypt 비용 포함) |
| 동시 처리 | 인스턴스당 200 RPS |

### 8.2 가용성
- SLA: > 99.9%
- 무중단 배포 (Rolling Update, K8s)
- Redis 장애 시 Fail-Closed (인증 거부)

### 8.3 보안
- RS256 JWT, BCrypt(cost 10) 패스워드, HTTPS Only
- 모든 시크릿은 Vault 런타임 주입
- OWASP Top 10 전 항목 대응 (`docs/context/security-guide.md` 참조)
- 감사 로그: 로그인 성공/실패, 로그아웃, 토큰 Rotation, 프로필 변경

### 8.4 관측성
- Prometheus 메트릭: `auth_login_total{result="success|fail"}`, `auth_token_refresh_total`, `auth_login_latency_seconds`
- 로그 포맷: JSON (correlation ID, userId 마스킹)
- Grafana 대시보드: 로그인 성공률, 평균 응답 시간, 잠금 발생 건수

### 8.5 국제화
- 에러 메시지는 한국어 기본, `Accept-Language: en`이면 영어 (Phase 2)

---

## 9. 에러 코드 카탈로그

| 코드 | HTTP | 메시지 | 발생 상황 |
|------|------|--------|----------|
| `AUTH_DUPLICATE_EMAIL` | 409 | 이미 사용 중인 이메일입니다 | 회원가입 시 이메일 중복 |
| `AUTH_INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호가 올바르지 않습니다 | 로그인 실패 (계정 미존재 포함) |
| `AUTH_ACCOUNT_LOCKED` | 429 | 너무 많은 로그인 실패. 5분 후 다시 시도해주세요 | 1분 내 5회 실패 |
| `AUTH_ACCOUNT_DISABLED` | 403 | 비활성화된 계정입니다 | status = INACTIVE / BANNED |
| `AUTH_INVALID_REFRESH_TOKEN` | 401 | 유효하지 않거나 만료된 토큰입니다 | Refresh Token 검증 실패 |
| `AUTH_REFRESH_TOKEN_REUSED` | 401 | 토큰 재사용이 감지되어 모든 세션이 종료되었습니다 | Rotation된 토큰 재사용 |
| `AUTH_UNAUTHORIZED` | 401 | 인증이 필요합니다 | Access Token 누락/만료 |
| `VALIDATION_FAILED` | 400 | 입력값이 올바르지 않습니다 | @Valid 검증 실패 |
| `RATE_LIMIT_EXCEEDED` | 429 | 요청이 너무 많습니다 | Gateway Rate Limit |
| `SERVER_ERROR` | 500 | 서버 내부 오류 | 예기치 못한 예외 |

---

## 변경 이력

| 버전 | 날짜 | 작성자 | 변경 사항 |
|------|------|--------|----------|
| 1.0 | 2026-04-09 | Spec Writer | 초안 작성 (6개 엔드포인트, 20개 테스트 케이스) |

# 보안 가이드

> 모든 서비스와 코드는 이 가이드를 준수해야 합니다.
> security-lead 스킬은 이 문서를 기준으로 보안 검토를 수행합니다.

---

## 인증 / 인가

### JWT 전략
```
알고리즘:   RS256 (비대칭키)
공개키:     Vault에서 런타임 주입 (하드코딩 금지)
Access:     만료 30분, Redis 세션 저장
Refresh:    만료 7일, Redis 저장, 사용 시 Rotation
```

### Gateway 필터
```
1. Authorization 헤더에서 JWT 추출
2. RS256 공개키로 서명 검증
3. Redis에서 세션 유효성 확인
4. 유효하면 X-User-Id, X-User-Email 헤더 추가
5. 하위 서비스로 전달
```

### 하위 서비스 규칙
- JWT 직접 검증 절대 금지 (Gateway 역할)
- `X-User-Id` 헤더만 신뢰
- 외부에서 `X-User-Id` 직접 주입 시도 → Gateway에서 차단

---

## Vault 시크릿 관리

### 절대 금지
```yaml
# ❌ 하드코딩 절대 금지
spring:
  datasource:
    password: "mysecretpassword"

# ❌ .env 파일 git 커밋 절대 금지
# ❌ application.yml에 실제 패스워드 금지
```

### 올바른 방법
```yaml
# ✅ 환경변수 참조 (Vault Agent가 주입)
spring:
  datasource:
    password: ${DB_PASSWORD}
  redis:
    password: ${REDIS_PASSWORD}
```

### Vault 경로 구조
```
secret/gymplan/
  ├── user-service/
  │   ├── db-password
  │   └── jwt-private-key
  ├── plan-service/
  │   └── db-password
  ├── kafka/
  │   ├── username
  │   └── password
  └── elasticsearch/
      └── password
```

---

## API 보안

### Rate Limiting
```
IP당:    100 req/min  (Gateway Redis 기반)
유저당:  300 req/min  (Gateway Redis 기반)
초과 시: HTTP 429 반환
```

### 입력 검증
```kotlin
// ✅ Spring Validation 사용
data class RegisterRequest(
    @field:Email val email: String,
    @field:Size(min=8, max=20) val password: String,
    @field:Size(min=2, max=20) val nickname: String
)

// ❌ 수동 파싱 금지
// ❌ Raw SQL 쿼리 금지 (JPA Parameterized Query만 사용)
```

### CORS
```kotlin
// 허용 출처 화이트리스트 (와일드카드 금지)
allowedOrigins = listOf(
    "http://localhost:3000",       // 로컬 웹
    "https://gymplan.io",          // 운영 웹
    "capacitor://localhost"        // 모바일 WebView
)
```

---

## 민감 정보 보호

### git 커밋 전 체크리스트
```
[ ] .env 파일 → .gitignore에 포함되어 있는가?
[ ] application-*.yml에 실제 패스워드가 없는가?
[ ] API 키, 토큰이 코드에 하드코딩되어 있지 않은가?
[ ] 로그에 패스워드, 토큰이 출력되지 않는가?
```

### 로그 마스킹
```kotlin
// ❌ 민감 정보 로그 금지
logger.info("로그인: email=$email, password=$password")

// ✅ 마스킹
logger.info("로그인 시도: email=${email.mask()}")
```

---

## OWASP Top 10 체크리스트

| 항목 | 대응 방법 |
|------|----------|
| A01 Broken Access Control | X-User-Id 헤더 검증, 타인 리소스 접근 차단 |
| A02 Cryptographic Failures | RS256 JWT, BCrypt 패스워드, HTTPS only |
| A03 Injection | JPA Parameterized Query, @Valid 입력 검증 |
| A04 Insecure Design | 최소 권한 원칙, 서비스 간 mTLS |
| A05 Security Misconfiguration | Vault 시크릿, 기본 패스워드 금지 |
| A06 Vulnerable Components | 의존성 주기적 업데이트 |
| A07 Auth Failures | JWT Rotation, Rate Limiting |
| A08 Software Integrity | CI/CD 서명 검증, ArgoCD GitOps |
| A09 Logging Failures | 민감 정보 마스킹, 감사 로그 |
| A10 SSRF | 외부 URL 화이트리스트 |

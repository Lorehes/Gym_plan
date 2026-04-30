# GymPlan Web (clients/web)

GymPlan 의 **계획 + 분석** 모드 웹 클라이언트.
체육관 환경에서 동작하는 모바일 앱(`clients/mobile`)과 백엔드 REST API 를 공유합니다.

## 책임 범위 (2-Track 클라이언트 전략)

`docs/architecture/services.md` 의 매트릭스를 따릅니다.

호출 **하는** 엔드포인트:
- `auth/*`, `users/me` (PUT 포함 — 프로필 수정은 웹 전용)
- `/plans` 전체 CRUD (단, `/plans/today` 는 모바일 전용 — 호출하지 않음)
- `/exercises` 전체 (검색·상세·커스텀 종목)
- `/analytics/{summary,volume,frequency,personal-records}`
- `/sessions/history`, `/sessions/{id}` (회고 전용)
- `/notifications/settings`

호출 **하지 않는** 엔드포인트:
- `/plans/today` — 체육관에서 즉시 조회 (모바일 전용)
- `/sessions/active`, `POST /sessions`, `/sessions/{id}/sets`, `/complete`, `/cancel`
- `/notifications/timer/stream` (SSE 휴식 타이머)
- FCM 푸시 (모바일 디바이스 전용)

## 기술 스택

- Vite + React 18 + TypeScript (strict)
- TanStack Query v5 (서버 상태) + Zustand (클라이언트 상태)
- React Router v6
- Tailwind CSS v3 (`docs/design/colors.md` 토큰 매핑)
- React Hook Form + Zod (폼 검증)
- Axios (`src/api/client.ts` — envelope 풀기 + JWT 자동 refresh 인터셉터)
- Recharts (통계 차트), react-day-picker (캘린더 히트맵)

## 빠른 시작

```bash
cd clients/web
cp .env.example .env       # VITE_API_BASE_URL 확인
npm install
npm run dev                # http://localhost:3000
```

## 스크립트

| 명령 | 설명 |
|------|------|
| `npm run dev` | 개발 서버 (port 3000) |
| `npm run build` | 타입체크 + 프로덕션 번들 |
| `npm run typecheck` | `tsc --noEmit` (strict) |
| `npm run lint` | ESLint |
| `npm run preview` | 빌드 결과물 미리보기 |

## 폴더 구조

```
src/
├── api/              REST 클라이언트 (모듈별 + 공통 인터셉터)
│   ├── client.ts     Axios + envelope 풀기 + 401 refresh
│   ├── types.ts      ApiResponse / ApiException / 도메인 enum
│   ├── auth.ts       /auth, /users/me
│   ├── plan.ts       /plans (웹 전용 CRUD)
│   ├── exercise.ts   /exercises
│   ├── workout.ts    /sessions/history, /sessions/{id} (회고만)
│   ├── analytics.ts  /analytics/*
│   └── notification.ts  /notifications/settings
├── auth/
│   ├── tokenStorage.ts   accessToken=메모리, refreshToken=localStorage
│   ├── authStore.ts      Zustand — bootstrap/signIn/signOut
│   └── useAuth.ts
├── components/       AppShell, PageHeader, RequireAuth
├── pages/            라우팅 단위 (Login/Register/Plans/...)
├── theme/            tokens.ts, muscleGroup.ts (라벨 매핑)
├── hooks/
├── lib/              env, queryClient, cn
├── App.tsx
└── main.tsx
```

## 인증 토큰 저장 정책

- **accessToken**: 메모리(JS 변수)에만 보관. 새로고침 시 사라지지만,
  `/users/me` 호출 → 401 → 인터셉터가 `refreshToken` 으로 자동 재발급.
- **refreshToken**: `localStorage` (key: `gymplan.refreshToken`).
  서버는 사용 시마다 rotation 하므로 단일 사용 토큰.

> ⚠️ 백엔드가 향후 httpOnly Set-Cookie 로 refresh 를 발급하도록 확장되면,
> `src/auth/tokenStorage.ts` 의 refresh 분기를 no-op 으로 비우면 됩니다.

자세한 보안 가이드: `docs/context/security-guide.md`.

## 디자인 시스템

- 컬러·타이포그래피 토큰: `docs/design/{colors,typography}.md`
- Tailwind 매핑: `tailwind.config.js`
- 라이트모드 기본 (다크모드 토큰은 `src/theme/tokens.ts` 에 함께 정의)
- 정보 밀도 우선 — 모바일의 큰 터치 타겟(48dp) 대신 데스크탑형 컴팩트 UI.

## 검증된 API 스키마 주의사항

모바일 앱(`clients/mobile/src/api/`)과 동일한 백엔드 계약을 사용합니다.
다음 항목은 검증 시 발견된 미묘한 부분이라 변경 금지:

| 필드 | 타입 | 비고 |
|------|------|------|
| `SessionDetail.planId` | `string \| null` | MongoDB 호환을 위한 String. plan-service 의 number 와 비교 시 변환 필요. |
| `SessionDetail.exercises[].sets[]` | nested 배열 | flat `sets[]` 가 아님. |
| `PlanExercise.targetWeightKg` | `number \| null` | 맨몸 운동(BODYWEIGHT)에서 null. |
| `PlanExercise.notes` | `string \| null` | 운동 항목 메모 (선택). |
| `TodayPlan.dayOfWeek` | `number \| null` | 백엔드 직렬화 계약 (Int?). |
| `PersonalRecord.isReliable` | `boolean` | Epley 추정 신뢰도. |

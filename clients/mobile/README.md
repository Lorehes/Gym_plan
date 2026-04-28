# GymPlan Mobile

Expo (React Native) 앱. 다크모드 기본, 체육관 환경 최적화.

## 시작하기

```bash
cd clients/mobile
npm install
# babel-plugin-module-resolver 별칭(@/*) 설정에 필요:
npm install --save-dev babel-plugin-module-resolver

npm start
```

`expo-secure-store`를 위해 dev client/native 빌드가 필요할 수 있습니다 (Expo Go에서는 SecureStore 동작):
```bash
npx expo prebuild
npx expo run:ios   # 또는 run:android
```

## 구조

```
src/
├── api/            # Axios 인스턴스 + 인터셉터, ApiException, 공통 타입
├── auth/           # SecureStore 토큰 저장 + Zustand auth 스토어
├── components/     # 공용 컴포넌트 (OfflineBanner 등)
├── config/         # env (Constants.expoConfig.extra 기반)
├── hooks/          # useOnlineStatus, useReactQueryOnlineSync
├── navigation/     # RootNavigator + AuthStack + MainTabs
├── providers/      # AppProviders (QueryClient + 온라인 동기화)
├── screens/        # 화면 컴포넌트
└── theme/          # colors, typography, spacing — docs/design/ 토큰
```

## 핵심 동작

### 인증 흐름
1. 앱 시작 → `RootNavigator`가 `useAuthStore.bootstrap()` 호출 → SecureStore에서 토큰 로드.
2. 토큰 있음 → `MainTabs`, 없음 → `AuthStack`.
3. 401 응답 → Axios 인터셉터가 `refreshToken`으로 access 갱신 후 원 요청 재시도.
4. refresh 실패 → SecureStore 클리어 + `signOut()` 호출 → `AuthStack`으로 자동 전환.

### API 응답 envelope
- `docs/api/common.md`의 `{ success, data, error, timestamp }` 포맷을 인터셉터가 자동으로 풀어냄.
- 호출자는 `data` 필드만 받음. 실패는 `ApiException`으로 throw.

### 보안
- JWT는 `expo-secure-store` (Keychain/Keystore)에만 저장 — AsyncStorage 사용 금지.
- refresh 동시성: 진행 중 refresh가 있으면 같은 Promise를 공유.

### 오프라인
- `@react-native-community/netinfo` 기반 `useOnlineStatus`.
- React Query `onlineManager`에 자동 연결 — 복귀 시 stale 쿼리 refetch.

## 다음 단계

- [ ] user-service `/auth/login`, `/auth/signup` 연동
- [ ] React Query persistor (`@tanstack/query-async-storage-persister`) — 오프라인 캐시 유지
- [ ] 디자인 시스템 컴포넌트 추출 (Button, Card, Input, SetCheck — docs/design/components/)
- [ ] FCM 푸시 (notification-service) 연동
- [ ] e2e 테스트 (Detox 또는 Maestro)

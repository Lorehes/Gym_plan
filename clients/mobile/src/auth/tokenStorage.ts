import * as SecureStore from 'expo-secure-store';

// security-guide.md: JWT는 디바이스 보안 저장소에만 (Keychain/Keystore).
// AsyncStorage 사용 금지 — 평문 저장됨.

const ACCESS_KEY = 'gymplan.accessToken';
const REFRESH_KEY = 'gymplan.refreshToken';

const SECURE_OPTIONS: SecureStore.SecureStoreOptions = {
  keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
};

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export const tokenStorage = {
  async getAccessToken(): Promise<string | null> {
    return SecureStore.getItemAsync(ACCESS_KEY, SECURE_OPTIONS);
  },

  async getRefreshToken(): Promise<string | null> {
    return SecureStore.getItemAsync(REFRESH_KEY, SECURE_OPTIONS);
  },

  async getTokens(): Promise<TokenPair | null> {
    const [accessToken, refreshToken] = await Promise.all([
      SecureStore.getItemAsync(ACCESS_KEY, SECURE_OPTIONS),
      SecureStore.getItemAsync(REFRESH_KEY, SECURE_OPTIONS),
    ]);
    if (!accessToken || !refreshToken) return null;
    return { accessToken, refreshToken };
  },

  async setTokens({ accessToken, refreshToken }: TokenPair): Promise<void> {
    await Promise.all([
      SecureStore.setItemAsync(ACCESS_KEY, accessToken, SECURE_OPTIONS),
      SecureStore.setItemAsync(REFRESH_KEY, refreshToken, SECURE_OPTIONS),
    ]);
  },

  async clear(): Promise<void> {
    await Promise.all([
      SecureStore.deleteItemAsync(ACCESS_KEY, SECURE_OPTIONS),
      SecureStore.deleteItemAsync(REFRESH_KEY, SECURE_OPTIONS),
    ]);
  },
};

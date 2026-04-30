// 웹 토큰 저장 — security-guide.md
//
// 이상적인 형태는 refreshToken 을 httpOnly cookie 로 두는 것이지만,
// 현재 백엔드(/auth/refresh)는 body 로 refreshToken 을 받기 때문에
// 클라이언트가 자바스크립트에서 접근 가능한 위치에 보관해야 한다.
//
// 절충안: accessToken 은 메모리에만, refreshToken 은 localStorage.
// - accessToken 은 짧은 수명(30분)이라 새로고침 시 refresh 1회로 복구된다.
// - localStorage 에 보관되는 것은 refreshToken 1개뿐이며, XSS 노출 시
//   refreshToken rotation(서버에서 사용 시 재발급) 으로 단일 사용 제약.
//
// 추후 backend 가 httpOnly Set-Cookie 로 refresh 를 발급하도록 변경하면
// 이 파일에서 setRefresh/getRefresh 를 no-op 으로 만든다.

const REFRESH_KEY = 'gymplan.refreshToken';

let accessTokenInMemory: string | null = null;

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export const tokenStorage = {
  getAccessToken(): string | null {
    return accessTokenInMemory;
  },

  getRefreshToken(): string | null {
    try {
      return localStorage.getItem(REFRESH_KEY);
    } catch {
      return null;
    }
  },

  getTokens(): TokenPair | null {
    const refreshToken = tokenStorage.getRefreshToken();
    if (!accessTokenInMemory || !refreshToken) return null;
    return { accessToken: accessTokenInMemory, refreshToken };
  },

  hasRefreshToken(): boolean {
    return tokenStorage.getRefreshToken() !== null;
  },

  setTokens({ accessToken, refreshToken }: TokenPair): void {
    accessTokenInMemory = accessToken;
    try {
      localStorage.setItem(REFRESH_KEY, refreshToken);
    } catch {
      // private mode 등 — refresh 토큰 사용 시 로그인 재요청.
    }
  },

  setAccessToken(accessToken: string): void {
    accessTokenInMemory = accessToken;
  },

  clear(): void {
    accessTokenInMemory = null;
    try {
      localStorage.removeItem(REFRESH_KEY);
    } catch {
      /* ignore */
    }
  },
};

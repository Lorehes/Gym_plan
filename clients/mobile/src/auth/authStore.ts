import { create } from 'zustand';

import { fetchMe, logout as logoutApi, type MeResponse } from '@/api/auth';
import { ApiException } from '@/api/types';

import { tokenStorage, type TokenPair } from './tokenStorage';

export interface AuthUser {
  id: number;
  email: string;
  nickname: string;
  profileImg: string | null;
}

interface AuthState {
  status: 'loading' | 'authenticated' | 'unauthenticated';
  user: AuthUser | null;

  bootstrap: () => Promise<void>;
  signIn: (tokens: TokenPair) => Promise<void>;
  signOut: () => Promise<void>;
  setUser: (user: AuthUser | null) => void;
}

function toAuthUser(me: MeResponse): AuthUser {
  return {
    id: me.userId,
    email: me.email,
    nickname: me.nickname,
    profileImg: me.profileImg,
  };
}

export const useAuthStore = create<AuthState>((set) => ({
  status: 'loading',
  user: null,

  // 앱 시작 — SecureStore에 토큰이 있으면 /users/me로 검증 + 사용자 hydrate.
  // 401이면 인터셉터가 refresh 시도 후 실패 시 onUnauthorized → signOut.
  bootstrap: async () => {
    const tokens = await tokenStorage.getTokens();
    if (!tokens) {
      set({ status: 'unauthenticated', user: null });
      return;
    }
    try {
      const me = await fetchMe();
      set({ status: 'authenticated', user: toAuthUser(me) });
    } catch (error) {
      // 인터셉터가 토큰을 정리했어도 명시적으로 한 번 더.
      await tokenStorage.clear();
      set({ status: 'unauthenticated', user: null });
      if (!(error instanceof ApiException)) throw error;
    }
  },

  // 로그인/회원가입 성공 후 호출. 토큰을 저장한 뒤 /users/me로 사용자 hydrate.
  signIn: async (tokens) => {
    await tokenStorage.setTokens(tokens);
    const me = await fetchMe();
    set({ status: 'authenticated', user: toAuthUser(me) });
  },

  // 서버 로그아웃은 best-effort — 네트워크 실패해도 로컬은 항상 정리.
  // 토큰이 없으면 API 호출 생략 — 401 인터셉터에서 트리거된 강제 로그아웃의
  // 재진입(401 → refresh 실패 → onUnauthorized → signOut → 401 …)을 차단.
  signOut: async () => {
    const tokens = await tokenStorage.getTokens();
    if (tokens) {
      try {
        await logoutApi();
      } catch {
        // 로그아웃은 클라이언트 의도가 우선 — 서버 응답 무시.
      }
    }
    await tokenStorage.clear();
    set({ status: 'unauthenticated', user: null });
  },

  setUser: (user) => set({ user }),
}));

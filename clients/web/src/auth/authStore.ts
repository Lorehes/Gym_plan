import { create } from 'zustand';

import { fetchMe, logout as logoutApi, type MeResponse } from '@/api/auth';
import { ApiException } from '@/api/types';

import { tokenStorage, type TokenPair } from './tokenStorage';

// 모바일 authStore 와 동일한 모양 — 차이는 토큰 저장소뿐.

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

  // 새로고침 시 호출 — accessToken 은 메모리이므로 항상 비어 있음.
  // refreshToken 이 localStorage 에 있으면 /users/me 호출 → 인터셉터가
  // 401 을 받고 자동 refresh → 토큰 복구 + 사용자 hydrate.
  bootstrap: async () => {
    if (!tokenStorage.hasRefreshToken()) {
      set({ status: 'unauthenticated', user: null });
      return;
    }
    try {
      const me = await fetchMe();
      set({ status: 'authenticated', user: toAuthUser(me) });
    } catch (error) {
      tokenStorage.clear();
      set({ status: 'unauthenticated', user: null });
      if (!(error instanceof ApiException)) throw error;
    }
  },

  signIn: async (tokens) => {
    tokenStorage.setTokens(tokens);
    const me = await fetchMe();
    set({ status: 'authenticated', user: toAuthUser(me) });
  },

  signOut: async () => {
    if (tokenStorage.hasRefreshToken() || tokenStorage.getAccessToken()) {
      try {
        await logoutApi();
      } catch {
        // 로컬 정리가 우선.
      }
    }
    tokenStorage.clear();
    set({ status: 'unauthenticated', user: null });
  },

  setUser: (user) => set({ user }),
}));

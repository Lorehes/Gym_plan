// 사용자별 격리: 모든 키에 userId 포함 → 로그아웃 후 다른 계정 로그인 시 자동 분리.

export const queryKeys = {
  plan: {
    today: (userId: number | null) => ['plan', 'today', userId] as const,
    list: (userId: number | null) => ['plan', 'list', userId] as const,
    detail: (userId: number | null, planId: number) => ['plan', 'detail', userId, planId] as const,
  },
  user: {
    me: () => ['user', 'me'] as const,
  },
  workout: {
    active: (userId: number | null) => ['workout', 'active', userId] as const,
  },
} as const;

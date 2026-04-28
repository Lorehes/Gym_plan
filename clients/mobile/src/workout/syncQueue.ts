import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

import { ApiException } from '@/api/types';
import { logSet, type SetLogRequest } from '@/api/workout';

// performance-goals.md: "세트 기록을 로컬 큐에 저장 → 네트워크 복구 시 자동 동기화 (FIFO)".
// 사양: 최대 3회 재시도. 실패 시 dead 상태로 남겨 사용자에게 노출.

export const MAX_ATTEMPTS = 3;
const RETRY_BACKOFF_MS = 5_000; // 동일 아이템 재시도 사이 최소 대기.
const DEAD_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7일 지난 dead 항목은 hydrate 시 정리.

export interface SyncItem {
  id: string;
  sessionId: string;
  // workoutStore 인덱스 — drain 콜백에서 set 상태 갱신용.
  exerciseLocalIndex: number;
  setNo: number;

  payload: SetLogRequest;

  attempts: number; // 시도된 횟수 — 0 ~ MAX_ATTEMPTS.
  status: 'queued' | 'inflight' | 'dead';
  enqueuedAt: number;
  lastAttemptAt?: number;
  lastError?: string;
}

type DrainListener = (event:
  | { type: 'success'; item: SyncItem }
  | { type: 'dead'; item: SyncItem; error: string }
  | { type: 'progress'; item: SyncItem }
) => void;

interface SyncQueueState {
  items: SyncItem[];

  enqueue: (input: Omit<SyncItem, 'id' | 'attempts' | 'status' | 'enqueuedAt'>) => string;
  remove: (id: string) => void;
  setStatus: (id: string, status: SyncItem['status'], lastError?: string) => void;
  recordAttempt: (id: string) => void;
  // 앱 시작 시 inflight로 남은 항목 → queued로 복원 (크래시 직후 재진행).
  rehydrate: () => void;
  clearDead: () => void;
  clearForSession: (sessionId: string) => void;
}

function makeId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

export const useSyncQueue = create<SyncQueueState>()(
  persist(
    (set, get) => ({
      items: [],

      enqueue: (input) => {
        const id = makeId();
        const item: SyncItem = {
          id,
          ...input,
          attempts: 0,
          status: 'queued',
          enqueuedAt: Date.now(),
        };
        set({ items: [...get().items, item] });
        return id;
      },

      remove: (id) => {
        set({ items: get().items.filter((x) => x.id !== id) });
      },

      setStatus: (id, status, lastError) => {
        set({
          items: get().items.map((x) =>
            x.id === id ? { ...x, status, lastError: lastError ?? x.lastError } : x,
          ),
        });
      },

      recordAttempt: (id) => {
        set({
          items: get().items.map((x) =>
            x.id === id ? { ...x, attempts: x.attempts + 1, lastAttemptAt: Date.now() } : x,
          ),
        });
      },

      rehydrate: () => {
        // 앱 종료 시점에 inflight였던 항목은 결과 미정 → queued로 되돌려 재시도.
        // 7일 지난 dead 항목은 사용자가 잊었을 가능성 — 자동 정리해 무한 누적 방지.
        const now = Date.now();
        set({
          items: get().items
            .filter((x) => !(x.status === 'dead' && now - x.enqueuedAt > DEAD_TTL_MS))
            .map((x) => (x.status === 'inflight' ? { ...x, status: 'queued' } : x)),
        });
      },

      clearDead: () => {
        set({ items: get().items.filter((x) => x.status !== 'dead') });
      },

      clearForSession: (sessionId) => {
        // 세션 취소/완료 시 잔여 항목 일괄 제거 — 더 이상 적용할 곳 없음.
        set({ items: get().items.filter((x) => x.sessionId !== sessionId) });
      },
    }),
    {
      name: 'gymplan.sync-queue.v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({ items: state.items }),
      version: 1,
      onRehydrateStorage: () => (state) => {
        state?.rehydrate();
      },
    },
  ),
);

// ---- Drain worker (모듈 싱글톤) -----------------------------------------------

const listeners = new Set<DrainListener>();

export function subscribeDrain(listener: DrainListener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function emit(event: Parameters<DrainListener>[0]) {
  listeners.forEach((l) => l(event));
}

let drainPromise: Promise<void> | null = null;
let nextDrainTimer: ReturnType<typeof setTimeout> | null = null;

function scheduleNextDrain(delayMs: number) {
  if (nextDrainTimer) clearTimeout(nextDrainTimer);
  nextDrainTimer = setTimeout(() => {
    nextDrainTimer = null;
    void drainOnce();
  }, delayMs);
}

// FIFO로 첫 queued 항목을 가져옴 — 백오프 미충족 항목은 건너뛰지 않고 대기 결정.
function pickNextQueued(items: SyncItem[]): SyncItem | null {
  const queued = items.filter((x) => x.status === 'queued').sort((a, b) => a.enqueuedAt - b.enqueuedAt);
  return queued[0] ?? null;
}

function backoffWaitMs(item: SyncItem): number {
  if (!item.lastAttemptAt) return 0;
  const elapsed = Date.now() - item.lastAttemptAt;
  const required = RETRY_BACKOFF_MS * Math.max(1, item.attempts);
  return Math.max(0, required - elapsed);
}

export async function drainOnce(): Promise<void> {
  if (drainPromise) return drainPromise;

  drainPromise = (async () => {
    try {
      while (true) {
        const store = useSyncQueue.getState();
        const item = pickNextQueued(store.items);
        if (!item) return;

        const wait = backoffWaitMs(item);
        if (wait > 0) {
          // 백오프 미충족 — 큐 자체는 더 진행 못 함. 대기 후 재드레인.
          scheduleNextDrain(wait);
          return;
        }

        store.setStatus(item.id, 'inflight');
        store.recordAttempt(item.id);

        try {
          await logSet(item.sessionId, item.payload);
          store.remove(item.id);
          emit({ type: 'success', item });
        } catch (error) {
          const errMsg = error instanceof Error ? error.message : 'unknown';
          const after = useSyncQueue.getState().items.find((x) => x.id === item.id);
          const attempts = after?.attempts ?? item.attempts + 1;

          // 4xx는 영구 실패 — 검증 오류/세션 없음/권한 없음. 재시도 의미 없음.
          // 5xx와 네트워크 오류만 백오프 재시도 대상.
          const permanent = isPermanentFailure(error);

          if (permanent || attempts >= MAX_ATTEMPTS) {
            useSyncQueue.getState().setStatus(item.id, 'dead', errMsg);
            const dead = useSyncQueue.getState().items.find((x) => x.id === item.id) ?? item;
            emit({ type: 'dead', item: dead, error: errMsg });
            // 다음 항목은 계속 시도 — dead는 큐에서 제외됨.
            continue;
          }

          useSyncQueue.getState().setStatus(item.id, 'queued', errMsg);
          emit({ type: 'progress', item });
          // 백오프 후 재시도. 큐 헤드가 이 항목이라면 즉시 시도해도 다시 대기 결정됨.
          scheduleNextDrain(RETRY_BACKOFF_MS);
          return;
        }
      }
    } finally {
      drainPromise = null;
    }
  })();

  return drainPromise;
}

function isPermanentFailure(error: unknown): boolean {
  if (!(error instanceof ApiException)) return false;
  // 401은 client.ts 인터셉터가 refresh로 처리 — 여기까지 도달하면 refresh도 실패한 상태.
  // 그 경우 사용자 재로그인 필요 → 큐를 보존하다 재로그인 후 자동 drain.
  if (error.status === 401) return false;
  // 4xx는 클라이언트 측 결함이거나 서버가 거부한 영구 실패.
  return error.status >= 400 && error.status < 500;
}

// 외부에서 enqueue 직후 호출 — 온라인이면 즉시 진행.
export function kickDrain() {
  if (nextDrainTimer) {
    clearTimeout(nextDrainTimer);
    nextDrainTimer = null;
  }
  void drainOnce();
}

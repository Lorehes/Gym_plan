import { ApiException } from '@/api/types';
import * as workoutApi from '@/api/workout';

import {
  MAX_ATTEMPTS,
  drainOnce,
  subscribeDrain,
  useSyncQueue,
} from '../syncQueue';

jest.mock('@/api/workout', () => ({
  __esModule: true,
  logSet: jest.fn(),
}));

const logSetMock = workoutApi.logSet as jest.MockedFunction<typeof workoutApi.logSet>;

function basePayload(setNo: number) {
  return {
    exerciseId: '1',
    exerciseName: 'Bench',
    muscleGroup: 'CHEST' as const,
    setNo,
    reps: 10,
    weightKg: 60,
    isSuccess: true,
  };
}

function enqueue(setNo: number, sessionId = 'sess-1') {
  return useSyncQueue.getState().enqueue({
    sessionId,
    exerciseLocalIndex: 0,
    setNo,
    payload: basePayload(setNo),
  });
}

beforeEach(() => {
  jest.useFakeTimers();
  useSyncQueue.setState({ items: [] });
  logSetMock.mockReset();
});

afterEach(() => {
  jest.useRealTimers();
});

describe('syncQueue (offlineQueue)', () => {
  describe('FIFO 순서 보장', () => {
    it('enqueued 순서대로 logSet이 호출된다', async () => {
      logSetMock.mockResolvedValue(undefined);
      enqueue(1);
      // 두 번째 enqueue가 동일 ms에 끼어들지 않도록 시간을 한 틱 진행
      jest.setSystemTime(new Date(Date.now() + 5));
      enqueue(2);
      jest.setSystemTime(new Date(Date.now() + 5));
      enqueue(3);

      await drainOnce();

      const order = logSetMock.mock.calls.map((c) => c[1].setNo);
      expect(order).toEqual([1, 2, 3]);
      expect(useSyncQueue.getState().items).toHaveLength(0);
    });
  });

  describe('재시도 로직 (최대 3회)', () => {
    it('5xx 실패 시 큐에 남고 attempts가 증가한다', async () => {
      logSetMock.mockRejectedValueOnce(new ApiException('SRV', 'boom', 500));
      enqueue(1);

      await drainOnce();

      const item = useSyncQueue.getState().items[0]!;
      expect(item.status).toBe('queued');
      expect(item.attempts).toBe(1);
      expect(item.lastError).toContain('boom');
    });

    it('연속 5xx 실패가 MAX_ATTEMPTS회 발생하면 dead 처리된다', async () => {
      logSetMock.mockRejectedValue(new ApiException('SRV', 'boom', 500));
      enqueue(1);

      for (let i = 0; i < MAX_ATTEMPTS; i += 1) {
        // backoff 무력화 — 시간을 충분히 진행
        jest.setSystemTime(new Date(Date.now() + 60_000));
        await drainOnce();
      }

      const item = useSyncQueue.getState().items[0]!;
      expect(item.attempts).toBe(MAX_ATTEMPTS);
      expect(item.status).toBe('dead');
      expect(logSetMock).toHaveBeenCalledTimes(MAX_ATTEMPTS);
    });

    it('네트워크 오류(ApiException 아님)도 5xx처럼 재시도된다', async () => {
      logSetMock.mockRejectedValueOnce(new Error('Network down'));
      enqueue(1);

      await drainOnce();

      const item = useSyncQueue.getState().items[0]!;
      expect(item.status).toBe('queued');
      expect(item.attempts).toBe(1);
    });
  });

  describe('dead 처리 및 사용자 알림', () => {
    it('4xx (영구 실패) → 즉시 dead, dead 이벤트 emit', async () => {
      logSetMock.mockRejectedValueOnce(
        new ApiException('SESSION_NOT_FOUND', 'gone', 404),
      );
      const events: any[] = [];
      const unsub = subscribeDrain((e) => events.push(e));
      enqueue(1);

      await drainOnce();

      const item = useSyncQueue.getState().items[0]!;
      expect(item.status).toBe('dead');
      expect(item.attempts).toBe(1);
      expect(events.some((e) => e.type === 'dead' && e.error === 'gone')).toBe(true);
      unsub();
    });

    it('401은 영구 실패가 아니라 재시도 대상이다', async () => {
      logSetMock.mockRejectedValueOnce(new ApiException('AUTH', 'unauth', 401));
      enqueue(1);

      await drainOnce();

      const item = useSyncQueue.getState().items[0]!;
      expect(item.status).toBe('queued');
    });

    it('성공 시 success 이벤트가 emit되고 큐에서 제거된다', async () => {
      logSetMock.mockResolvedValue(undefined);
      const events: any[] = [];
      const unsub = subscribeDrain((e) => events.push(e));
      enqueue(1);

      await drainOnce();

      expect(useSyncQueue.getState().items).toHaveLength(0);
      expect(events.some((e) => e.type === 'success')).toBe(true);
      unsub();
    });
  });

  describe('큐 보조 동작', () => {
    it('rehydrate: inflight → queued로 복원한다', () => {
      const id = enqueue(1);
      useSyncQueue.getState().setStatus(id, 'inflight');

      useSyncQueue.getState().rehydrate();

      expect(useSyncQueue.getState().items[0]!.status).toBe('queued');
    });

    it('clearForSession: 해당 세션 항목만 제거', () => {
      enqueue(1, 'sess-A');
      enqueue(2, 'sess-B');

      useSyncQueue.getState().clearForSession('sess-A');

      const items = useSyncQueue.getState().items;
      expect(items).toHaveLength(1);
      expect(items[0]!.sessionId).toBe('sess-B');
    });

    it('clearDead: dead 항목만 제거', () => {
      const id1 = enqueue(1);
      const id2 = enqueue(2);
      useSyncQueue.getState().setStatus(id1, 'dead');

      useSyncQueue.getState().clearDead();

      const items = useSyncQueue.getState().items;
      expect(items.map((x) => x.id)).toEqual([id2]);
    });
  });
});

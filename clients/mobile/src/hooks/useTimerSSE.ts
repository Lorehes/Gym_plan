import { useEffect, useRef, useState } from 'react';
import EventSource from 'react-native-sse';

import { env } from '@/config/env';
import { tokenStorage } from '@/auth/tokenStorage';

// docs/api/notification-service.md — GET /notifications/timer/stream (SSE)
// 로컬 타이머가 기준이며 SSE는 보조 — 실패해도 운동 진행을 막지 않음.

interface TimerStartEvent {
  sessionId: string;
  restSeconds: number;
  exerciseName: string;
}

interface TimerEndEvent {
  sessionId: string;
  message: string;
}

export type SSEStatus = 'disabled' | 'connecting' | 'connected' | 'disconnected';

interface Options {
  sessionId: string | null;
  enabled: boolean;
  onTimerStart?: (e: TimerStartEvent) => void;
  onTimerEnd?: (e: TimerEndEvent) => void;
}

type CustomEvents = 'timer-start' | 'timer-end';

export function useTimerSSE({
  sessionId,
  enabled,
  onTimerStart,
  onTimerEnd,
}: Options): { status: SSEStatus } {
  const onStartRef = useRef(onTimerStart);
  const onEndRef = useRef(onTimerEnd);
  onStartRef.current = onTimerStart;
  onEndRef.current = onTimerEnd;

  const [status, setStatus] = useState<SSEStatus>('disabled');

  useEffect(() => {
    if (!enabled || !sessionId) {
      setStatus('disabled');
      return;
    }
    let cancelled = false;
    let es: EventSource<CustomEvents> | null = null;
    setStatus('connecting');

    (async () => {
      const token = await tokenStorage.getAccessToken();
      if (!token || cancelled) {
        if (!cancelled) setStatus('disconnected');
        return;
      }

      es = new EventSource<CustomEvents>(`${env.apiBaseUrl}/notifications/timer/stream`, {
        headers: { Authorization: `Bearer ${token}` },
        // react-native-sse 기본 자동 재연결.
      });

      es.addEventListener('open', () => {
        if (!cancelled) setStatus('connected');
      });

      es.addEventListener('timer-start', (event) => {
        if (!event.data) return;
        try {
          const payload = JSON.parse(event.data) as TimerStartEvent;
          if (payload.sessionId === sessionId) onStartRef.current?.(payload);
        } catch {
          // 보조 채널 — 파싱 실패는 무시.
        }
      });

      es.addEventListener('timer-end', (event) => {
        if (!event.data) return;
        try {
          const payload = JSON.parse(event.data) as TimerEndEvent;
          if (payload.sessionId === sessionId) onEndRef.current?.(payload);
        } catch {
          // 무시.
        }
      });

      es.addEventListener('error', () => {
        // 라이브러리가 자동 재연결을 시도. 사용자에겐 \"로컬 모드\"로 보이게 처리.
        if (!cancelled) setStatus('disconnected');
      });

      es.addEventListener('close', () => {
        if (!cancelled) setStatus('disconnected');
      });
    })();

    return () => {
      cancelled = true;
      es?.close();
    };
  }, [sessionId, enabled]);

  return { status };
}

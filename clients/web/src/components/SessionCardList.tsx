import { Link } from 'react-router-dom';
import { ChevronRight, Clock, Dumbbell, Weight } from 'lucide-react';

import type { SessionDetail, SessionStatus } from '@/api/workout';
import { cn } from '@/lib/cn';

interface Props {
  sessions: SessionDetail[];
  selectedDate: string; // 'YYYY-MM-DD'
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

function formatDuration(sec: number): string {
  if (sec < 60) return `${sec}초`;
  const m = Math.floor(sec / 60);
  if (m < 60) return `${m}분`;
  const h = Math.floor(m / 60);
  const rem = m % 60;
  return rem > 0 ? `${h}시간 ${rem}분` : `${h}시간`;
}

function formatVolume(kg: number): string {
  return `${Math.round(kg).toLocaleString('ko-KR')}kg`;
}

const STATUS_LABEL: Record<SessionStatus, string> = {
  COMPLETED: '완료',
  CANCELLED: '취소됨',
  IN_PROGRESS: '진행 중',
};

const STATUS_CLASS: Record<SessionStatus, string> = {
  COMPLETED: 'bg-success-100 text-success-600',
  CANCELLED: 'bg-neutral-100 text-neutral-500',
  IN_PROGRESS: 'bg-primary-100 text-primary-600',
};

function SessionCard({ session }: { session: SessionDetail }) {
  const isCancelled = session.status === 'CANCELLED';

  return (
    <Link
      to={`/history/${session.sessionId}`}
      className={cn(
        'block rounded-xl border border-neutral-200 bg-white p-4 transition-colors',
        'hover:border-primary-300 hover:bg-primary-50/40',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
        isCancelled && 'opacity-60',
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-semibold text-neutral-900 truncate">
              {session.planName ?? '자유 운동'}
            </span>
            <span
              className={cn(
                'shrink-0 rounded-full px-2 py-0.5 text-xs font-medium',
                STATUS_CLASS[session.status],
              )}
            >
              {STATUS_LABEL[session.status]}
            </span>
          </div>
          <p className="mt-0.5 text-xs text-neutral-400">
            {formatTime(session.startedAt)}
            {session.completedAt && ` — ${formatTime(session.completedAt)}`}
          </p>
        </div>
        <ChevronRight size={16} className="shrink-0 text-neutral-300 mt-0.5" aria-hidden />
      </div>

      <div className="mt-3 flex items-center gap-4 text-xs text-neutral-500">
        <span className="flex items-center gap-1">
          <Clock size={12} aria-hidden />
          {formatDuration(session.durationSec)}
        </span>
        {session.totalVolume > 0 && (
          <span className="flex items-center gap-1">
            <Weight size={12} aria-hidden />
            {formatVolume(session.totalVolume)}
          </span>
        )}
        <span className="flex items-center gap-1">
          <Dumbbell size={12} aria-hidden />
          {session.totalSets}세트
        </span>
      </div>
    </Link>
  );
}

export function SessionCardList({ sessions, selectedDate }: Props) {
  if (sessions.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-sm text-neutral-400">
        <p>{selectedDate.replace(/-/g, '.')}에 운동 기록이 없어요.</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <p className="text-xs font-medium text-neutral-500 px-1">
        {selectedDate.replace(/-/g, '.')} · {sessions.length}개 세션
      </p>
      {sessions.map((s) => (
        <SessionCard key={s.sessionId} session={s} />
      ))}
    </div>
  );
}

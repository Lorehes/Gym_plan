import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  ChevronDown,
  ChevronUp,
  Clock,
  Dumbbell,
  Weight,
} from 'lucide-react';

import type { SessionExercise, SessionStatus } from '@/api/workout';
import { Skeleton } from '@/components/Skeleton';
import { useSessionDetailQuery } from '@/hooks/useSessionHistory';
import { muscleGroupLabel } from '@/theme/muscleGroup';
import { cn } from '@/lib/cn';

function pad(n: number) {
  return String(n).padStart(2, '0');
}

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${d.getHours()}:${pad(d.getMinutes())}`;
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

function StatChip({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="flex flex-col items-center gap-1 rounded-xl bg-neutral-50 px-4 py-3 min-w-[80px]">
      <span className="text-neutral-400">{icon}</span>
      <span className="text-base font-semibold text-neutral-900">{value}</span>
      <span className="text-xs text-neutral-400">{label}</span>
    </div>
  );
}

function ExerciseRow({ exercise }: { exercise: SessionExercise }) {
  const [collapsed, setCollapsed] = useState(false);

  const successSets = exercise.sets.filter((s) => s.isSuccess).length;

  return (
    <div className="rounded-xl border border-neutral-200 overflow-hidden">
      {/* Exercise header */}
      <button
        type="button"
        onClick={() => setCollapsed((v) => !v)}
        className={cn(
          'flex w-full items-center justify-between px-4 py-3 text-left transition-colors',
          'hover:bg-neutral-50 focus-visible:outline-none focus-visible:ring-2',
          'focus-visible:ring-inset focus-visible:ring-primary-500',
        )}
        aria-expanded={!collapsed}
      >
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-semibold text-neutral-900 truncate">
              {exercise.exerciseName}
            </span>
            <span className="shrink-0 rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-neutral-500">
              {muscleGroupLabel[exercise.muscleGroup] ?? exercise.muscleGroup}
            </span>
          </div>
          <p className="mt-0.5 text-xs text-neutral-400">
            {successSets}/{exercise.sets.length}세트 성공
          </p>
        </div>
        {collapsed ? (
          <ChevronDown size={16} className="shrink-0 text-neutral-400" aria-hidden />
        ) : (
          <ChevronUp size={16} className="shrink-0 text-neutral-400" aria-hidden />
        )}
      </button>

      {/* Sets table */}
      {!collapsed && (
        <div className="border-t border-neutral-100">
          <table className="min-w-full divide-y divide-neutral-100">
            <thead className="bg-neutral-50">
              <tr>
                {['세트', '무게', '횟수', '결과'].map((h) => (
                  <th
                    key={h}
                    scope="col"
                    className="px-4 py-2 text-left text-xs font-medium text-neutral-400"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100 bg-white">
              {exercise.sets.map((set) => (
                <tr
                  key={set.setNo}
                  className={cn(!set.isSuccess && 'opacity-50')}
                >
                  <td className="px-4 py-2.5 text-sm text-neutral-500">
                    {set.setNo}
                  </td>
                  <td className="px-4 py-2.5 text-sm text-neutral-700">
                    {set.weightKg > 0 ? `${set.weightKg}kg` : '맨몸'}
                  </td>
                  <td className="px-4 py-2.5 text-sm text-neutral-700">
                    {set.reps}회
                  </td>
                  <td className="px-4 py-2.5 text-sm">
                    {set.isSuccess ? (
                      <span className="font-medium text-success-500">성공</span>
                    ) : (
                      <span className="text-neutral-400">실패</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function DetailSkeleton() {
  return (
    <section className="px-4 py-6 md:px-8 md:py-8 space-y-6 max-w-3xl mx-auto">
      <Skeleton className="h-5 w-32" />
      <div className="card p-6 space-y-4">
        <Skeleton className="h-6 w-48" />
        <Skeleton className="h-4 w-64" />
        <div className="flex gap-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-20 rounded-xl" />
          ))}
        </div>
      </div>
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-16 w-full rounded-xl" />
        ))}
      </div>
    </section>
  );
}

export default function SessionDetailPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const { data, isPending, isError, refetch } = useSessionDetailQuery(sessionId ?? '');

  if (isPending) return <DetailSkeleton />;

  if (isError || !data) {
    return (
      <section className="px-4 py-6 md:px-8 md:py-8 max-w-3xl mx-auto">
        <div className="flex flex-col items-center justify-center py-20 text-sm text-neutral-500 space-y-3">
          <AlertTriangle size={32} className="text-warning-400" aria-hidden />
          <p>세션 정보를 불러오지 못했어요.</p>
          <button onClick={() => refetch()} className="text-primary-600 hover:underline">
            다시 시도
          </button>
        </div>
      </section>
    );
  }

  const isCancelled = data.status === 'CANCELLED';

  return (
    <section className="px-4 py-6 md:px-8 md:py-8 space-y-6 max-w-3xl mx-auto">
      {/* Back */}
      <Link
        to="/history"
        className="inline-flex items-center gap-1 text-sm text-neutral-500 hover:text-neutral-700
                   focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 rounded"
      >
        <ArrowLeft size={14} aria-hidden />
        히스토리
      </Link>

      {/* Session header */}
      <div className={cn('card p-6 space-y-4', isCancelled && 'opacity-75')}>
        <div className="flex items-start justify-between gap-2">
          <div>
            <h1 className="text-lg font-semibold text-neutral-900">
              {data.planName ?? '자유 운동'}
            </h1>
            <p className="mt-0.5 text-sm text-neutral-400">
              {formatDateTime(data.startedAt)}
              {data.completedAt && ` — ${formatDateTime(data.completedAt)}`}
            </p>
          </div>
          <span
            className={cn(
              'shrink-0 rounded-full px-2.5 py-1 text-xs font-medium',
              STATUS_CLASS[data.status],
            )}
          >
            {STATUS_LABEL[data.status]}
          </span>
        </div>

        {/* Summary stats */}
        <div className="flex flex-wrap gap-3">
          <StatChip
            icon={<Clock size={16} aria-hidden />}
            label="운동 시간"
            value={formatDuration(data.durationSec)}
          />
          {data.totalVolume > 0 && (
            <StatChip
              icon={<Weight size={16} aria-hidden />}
              label="총 볼륨"
              value={formatVolume(data.totalVolume)}
            />
          )}
          <StatChip
            icon={<Dumbbell size={16} aria-hidden />}
            label="총 세트"
            value={`${data.totalSets}세트`}
          />
          <StatChip
            icon={<span className="text-sm font-bold text-neutral-400">#</span>}
            label="종목 수"
            value={`${data.exercises.length}종목`}
          />
        </div>

        {/* Notes */}
        {data.notes && (
          <div className="rounded-lg bg-neutral-50 px-4 py-3 text-sm text-neutral-600">
            {data.notes}
          </div>
        )}
      </div>

      {/* Exercise list */}
      {data.exercises.length > 0 ? (
        <div className="space-y-3">
          <h2 className="text-sm font-semibold text-neutral-700">운동 기록</h2>
          {data.exercises.map((ex) => (
            <ExerciseRow key={ex.exerciseId} exercise={ex} />
          ))}
        </div>
      ) : (
        <div className="py-12 text-center text-sm text-neutral-400">
          기록된 운동이 없어요.
        </div>
      )}
    </section>
  );
}

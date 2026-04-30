import { useMemo, useState } from 'react';
import { AlertTriangle, CheckCircle, ChevronDown, ChevronUp, ChevronsUpDown } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { ko } from 'date-fns/locale';

import type { PersonalRecord } from '@/api/analytics';
import { Skeleton } from '@/components/Skeleton';
import { usePersonalRecordsQuery } from '@/hooks/useAnalytics';
import { cn } from '@/lib/cn';

type SortKey = 'exerciseName' | 'maxWeightKg' | 'maxReps' | 'estimated1RM' | 'achievedAt';
type SortDir = 'asc' | 'desc';

interface SortState {
  key: SortKey;
  dir: SortDir;
}

function SortIcon({ current, field }: { current: SortState; field: SortKey }) {
  if (current.key !== field) {
    return <ChevronsUpDown size={13} className="text-neutral-300" aria-hidden />;
  }
  return current.dir === 'asc' ? (
    <ChevronUp size={13} className="text-primary-600" aria-hidden />
  ) : (
    <ChevronDown size={13} className="text-primary-600" aria-hidden />
  );
}

function formatDate(iso: string): string {
  try {
    return formatDistanceToNow(new Date(iso), { locale: ko, addSuffix: true });
  } catch {
    return iso.slice(0, 10);
  }
}

function sortRecords(records: PersonalRecord[], sort: SortState): PersonalRecord[] {
  return [...records].sort((a, b) => {
    let cmp = 0;
    switch (sort.key) {
      case 'exerciseName':
        cmp = a.exerciseName.localeCompare(b.exerciseName, 'ko');
        break;
      case 'maxWeightKg':
        cmp = a.maxWeightKg - b.maxWeightKg;
        break;
      case 'maxReps':
        cmp = a.maxReps - b.maxReps;
        break;
      case 'estimated1RM':
        cmp = a.estimated1RM - b.estimated1RM;
        break;
      case 'achievedAt':
        cmp = new Date(a.achievedAt).getTime() - new Date(b.achievedAt).getTime();
        break;
    }
    return sort.dir === 'asc' ? cmp : -cmp;
  });
}

export function PersonalRecordsTable() {
  const { data, isPending, isError, refetch } = usePersonalRecordsQuery();

  const [sort, setSort] = useState<SortState>({ key: 'estimated1RM', dir: 'desc' });
  const [onlyReliable, setOnlyReliable] = useState(false);

  const toggleSort = (key: SortKey) => {
    setSort((prev) =>
      prev.key === key
        ? { key, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
        : { key, dir: 'desc' },
    );
  };

  const displayRecords = useMemo(() => {
    if (!data) return [];
    const filtered = onlyReliable ? data.filter((r) => r.isReliable) : data;
    return sortRecords(filtered, sort);
  }, [data, sort, onlyReliable]);

  if (isPending) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="flex gap-4 py-2">
            <Skeleton className="h-4 flex-1" />
            <Skeleton className="h-4 w-16" />
            <Skeleton className="h-4 w-12" />
            <Skeleton className="h-4 w-16" />
          </div>
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center py-8 text-sm text-neutral-500 space-y-2">
        <p>신기록 데이터를 불러오지 못했어요.</p>
        <button onClick={() => refetch()} className="text-primary-600 hover:underline">
          다시 시도
        </button>
      </div>
    );
  }

  if (!data || data.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-neutral-400">
        아직 신기록 데이터가 없어요.
      </p>
    );
  }

  const ColHeader = ({
    field,
    label,
    className,
  }: {
    field: SortKey;
    label: string;
    className?: string;
  }) => (
    <th
      scope="col"
      className={cn('px-3 py-2.5 text-left', className)}
    >
      <button
        type="button"
        onClick={() => toggleSort(field)}
        className="inline-flex items-center gap-1 text-xs font-medium text-neutral-500
                   hover:text-neutral-800 focus-visible:outline-none focus-visible:ring-2
                   focus-visible:ring-primary-500 rounded"
      >
        {label}
        <SortIcon current={sort} field={field} />
      </button>
    </th>
  );

  return (
    <div className="space-y-3">
      {/* 필터 */}
      <div className="flex items-center justify-end">
        <label className="flex cursor-pointer items-center gap-2 text-sm text-neutral-600">
          <input
            type="checkbox"
            checked={onlyReliable}
            onChange={(e) => setOnlyReliable(e.target.checked)}
            className="h-4 w-4 rounded border-neutral-300 text-primary-600
                       focus:ring-primary-500 focus:ring-offset-0"
          />
          신뢰 가능한 기록만 보기
        </label>
      </div>

      {/* 테이블 */}
      <div className="overflow-x-auto rounded-lg border border-neutral-200">
        <table className="min-w-full divide-y divide-neutral-100">
          <thead className="bg-neutral-50">
            <tr>
              <ColHeader field="exerciseName" label="종목" className="min-w-[140px]" />
              <ColHeader field="maxWeightKg" label="최고 무게" />
              <ColHeader field="maxReps" label="최고 횟수" />
              <ColHeader field="estimated1RM" label="추정 1RM" />
              <th scope="col" className="px-3 py-2.5 text-left text-xs font-medium text-neutral-500">
                신뢰도
              </th>
              <ColHeader field="achievedAt" label="달성일" className="min-w-[100px]" />
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100 bg-white">
            {displayRecords.length === 0 ? (
              <tr>
                <td colSpan={6} className="py-8 text-center text-sm text-neutral-400">
                  해당하는 기록이 없어요.
                </td>
              </tr>
            ) : (
              displayRecords.map((pr) => (
                <tr key={pr.exerciseId} className="hover:bg-neutral-50 transition-colors">
                  <td className="px-3 py-3 text-sm font-medium text-neutral-900 whitespace-nowrap">
                    {pr.exerciseName}
                  </td>
                  <td className="px-3 py-3 text-sm text-neutral-700 whitespace-nowrap">
                    {pr.maxWeightKg > 0 ? `${pr.maxWeightKg}kg` : '맨몸'}
                  </td>
                  <td className="px-3 py-3 text-sm text-neutral-700 whitespace-nowrap">
                    {pr.maxReps}회
                  </td>
                  <td className="px-3 py-3 whitespace-nowrap">
                    <span className="text-sm font-semibold text-primary-700">
                      {pr.estimated1RM.toFixed(1)}kg
                    </span>
                  </td>
                  <td className="px-3 py-3 whitespace-nowrap">
                    {pr.isReliable ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-success-100 px-2 py-0.5 text-xs font-medium text-success-500">
                        <CheckCircle size={11} aria-hidden />
                        신뢰 가능
                      </span>
                    ) : (
                      <span
                        title="세트 수가 적어 추정 1RM의 신뢰도가 낮습니다."
                        className="inline-flex items-center gap-1 rounded-full bg-warning-100 px-2 py-0.5 text-xs font-medium text-warning-500 cursor-help"
                      >
                        <AlertTriangle size={11} aria-hidden />
                        표본 부족
                      </span>
                    )}
                  </td>
                  <td className="px-3 py-3 text-xs text-neutral-400 whitespace-nowrap">
                    {formatDate(pr.achievedAt)}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <p className="text-right text-xs text-neutral-400">
        추정 1RM: Epley 공식 (weight × (1 + reps / 30))
      </p>
    </div>
  );
}

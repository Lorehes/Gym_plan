import { useMemo } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import type { AnalyticsPeriod } from '@/api/analytics';
import type { MuscleGroup } from '@/api/types';
import { muscleGroupLabel } from '@/theme/muscleGroup';
import { Skeleton } from '@/components/Skeleton';
import { useVolumeQuery } from '@/hooks/useAnalytics';

interface Props {
  period: AnalyticsPeriod;
}

// 디자인 토큰에서 추출한 부위별 색상 (token 외 hex 금지 — 토큰 값 사용)
const MUSCLE_COLORS: Record<string, string> = {
  CHEST: '#3B82F6',     // primary-500
  BACK: '#1D4ED8',      // primary-700
  SHOULDERS: '#F97316', // accent-500
  ARMS: '#22C55E',      // success-500
  BICEPS: '#22C55E',
  TRICEPS: '#60A5FA',   // primary-400
  LEGS: '#F59E0B',      // warning-500
  GLUTES: '#EA580C',    // accent-600
  CORE: '#737373',      // neutral-500
  CARDIO: '#EF4444',    // error-500
};

function formatVolumeAxis(v: number): string {
  if (v >= 1000) return `${(v / 1000).toFixed(0)}k`;
  return String(v);
}

function formatVolumeTooltip(v: number): string {
  return `${Math.round(v).toLocaleString('ko-KR')}kg`;
}

export function VolumeChart({ period }: Props) {
  const { data, isPending, isError, refetch } = useVolumeQuery(period);

  // 부위별 볼륨 집계 (time-series → muscle aggregate)
  const chartData = useMemo(() => {
    if (!data) return [];
    const map: Record<string, number> = {};
    data.forEach(({ muscle, volume }) => {
      map[muscle] = (map[muscle] ?? 0) + volume;
    });
    return Object.entries(map)
      .map(([muscle, volume]) => ({
        muscle,
        label: muscleGroupLabel[muscle as MuscleGroup] ?? muscle,
        volume: Math.round(volume),
      }))
      .sort((a, b) => b.volume - a.volume);
  }, [data]);

  const chartHeight = Math.max(200, chartData.length * 48 + 60);

  if (isPending) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="flex items-center gap-3">
            <Skeleton className="h-4 w-10" />
            <div className="h-7 animate-pulse rounded-md bg-neutral-200" style={{ width: `${60 - i * 10}%` }} />
          </div>
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-10 text-sm text-neutral-500 space-y-2">
        <p>볼륨 데이터를 불러오지 못했어요.</p>
        <button onClick={() => refetch()} className="text-primary-600 hover:underline">
          다시 시도
        </button>
      </div>
    );
  }

  if (chartData.length === 0) {
    return (
      <div className="flex items-center justify-center py-14 text-sm text-neutral-400">
        운동 기록이 없습니다.
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={chartHeight}>
      <BarChart
        data={chartData}
        layout="vertical"
        margin={{ top: 4, right: 16, bottom: 4, left: 8 }}
      >
        <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#E5E5E5" />
        <XAxis
          type="number"
          tickFormatter={formatVolumeAxis}
          tick={{ fontSize: 12, fill: '#737373' }}
          axisLine={false}
          tickLine={false}
        />
        <YAxis
          type="category"
          dataKey="label"
          width={40}
          tick={{ fontSize: 12, fill: '#525252' }}
          axisLine={false}
          tickLine={false}
        />
        <Tooltip
          formatter={(value: number) => [formatVolumeTooltip(value), '볼륨']}
          contentStyle={{
            borderRadius: '8px',
            border: '1px solid #E5E5E5',
            boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1)',
            fontSize: '13px',
          }}
          cursor={{ fill: '#F5F5F5' }}
        />
        <Bar dataKey="volume" radius={[0, 4, 4, 0]} maxBarSize={36}>
          {chartData.map((entry) => (
            <Cell
              key={entry.muscle}
              fill={MUSCLE_COLORS[entry.muscle] ?? '#A3A3A3'}
            />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

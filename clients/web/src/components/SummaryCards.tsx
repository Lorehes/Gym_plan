import { Activity, Clock, Dumbbell, Target } from 'lucide-react';

import type { AnalyticsPeriod } from '@/api/analytics';
import { muscleGroupLabel } from '@/theme/muscleGroup';
import { Skeleton } from '@/components/Skeleton';
import { useSummaryQuery } from '@/hooks/useAnalytics';
import { cn } from '@/lib/cn';

interface Props {
  period: AnalyticsPeriod;
}

function formatVolume(kg: number): string {
  return `${Math.round(kg).toLocaleString('ko-KR')}kg`;
}

function formatDuration(seconds: number): string {
  const m = Math.round(seconds / 60);
  if (m < 60) return `${m}분`;
  const h = Math.floor(m / 60);
  const rem = m % 60;
  return rem > 0 ? `${h}시간 ${rem}분` : `${h}시간`;
}

interface CardConfig {
  label: string;
  icon: React.ElementType;
  iconBg: string;
  iconColor: string;
}

const CARD_CONFIG: CardConfig[] = [
  {
    label: '총 운동 횟수',
    icon: Activity,
    iconBg: 'bg-primary-50',
    iconColor: 'text-primary-600',
  },
  {
    label: '총 볼륨',
    icon: Dumbbell,
    iconBg: 'bg-success-100',
    iconColor: 'text-success-500',
  },
  {
    label: '평균 운동 시간',
    icon: Clock,
    iconBg: 'bg-warning-100',
    iconColor: 'text-warning-500',
  },
  {
    label: '가장 많이 한 부위',
    icon: Target,
    iconBg: 'bg-accent-100',
    iconColor: 'text-accent-500',
  },
];

export function SummaryCards({ period }: Props) {
  const { data, isPending, isError, refetch } = useSummaryQuery(period);

  if (isPending) {
    return (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="card p-5 space-y-3">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-8 w-20" />
          </div>
        ))}
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="card p-5 text-center space-y-2">
        <p className="text-sm text-neutral-500">요약 통계를 불러오지 못했어요.</p>
        <button
          onClick={() => refetch()}
          className="text-sm text-primary-600 hover:underline"
        >
          다시 시도
        </button>
      </div>
    );
  }

  const muscleLabel = data.mostTrainedMuscle
    ? (muscleGroupLabel[data.mostTrainedMuscle] ?? data.mostTrainedMuscle)
    : '-';

  const values = [
    `${data.totalSessions}회`,
    formatVolume(data.totalVolume),
    formatDuration(data.avgDurationSec),
    muscleLabel,
  ];

  const isEmpty = data.totalSessions === 0;

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {CARD_CONFIG.map(({ label, icon: Icon, iconBg, iconColor }, i) => (
          <div
            key={label}
            className={cn(
              'card p-5 flex items-start gap-4',
              'transition-shadow hover:shadow-md',
            )}
          >
            <div
              className={cn(
                'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg',
                iconBg,
              )}
            >
              <Icon size={20} className={iconColor} aria-hidden />
            </div>
            <div className="min-w-0">
              <p className="text-xs text-neutral-500">{label}</p>
              <p className="mt-1 text-2xl font-bold tracking-tight text-neutral-900 truncate">
                {values[i]}
              </p>
            </div>
          </div>
        ))}
      </div>

      {isEmpty && (
        <p className="text-center text-sm text-neutral-400">
          아직 운동 기록이 없어요. 운동을 시작해보세요!
        </p>
      )}
    </div>
  );
}

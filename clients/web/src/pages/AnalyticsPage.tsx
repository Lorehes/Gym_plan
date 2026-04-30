import { useState } from 'react';

import type { AnalyticsPeriod } from '@/api/analytics';
import { FrequencyHeatmap } from '@/components/FrequencyHeatmap';
import { PageHeader } from '@/components/PageHeader';
import { PersonalRecordsTable } from '@/components/PersonalRecordsTable';
import { SummaryCards } from '@/components/SummaryCards';
import { VolumeChart } from '@/components/VolumeChart';
import { cn } from '@/lib/cn';

type Tab = AnalyticsPeriod;

const TABS: { key: Tab; label: string }[] = [
  { key: 'WEEK', label: '이번 주' },
  { key: 'MONTH', label: '이번 달' },
];

// 이전/다음 월 계산 — 연도 경계 처리 포함
function adjustMonth(year: number, month: number, delta: number): [number, number] {
  let m = month + delta;
  let y = year;
  if (m < 1) { m = 12; y -= 1; }
  if (m > 12) { m = 1; y += 1; }
  return [y, m];
}

export default function AnalyticsPage() {
  const [period, setPeriod] = useState<Tab>('WEEK');

  const now = new Date();
  const [calYear, setCalYear] = useState(now.getFullYear());
  const [calMonth, setCalMonth] = useState(now.getMonth() + 1); // 1-based

  const handlePrevMonth = () => {
    const [y, m] = adjustMonth(calYear, calMonth, -1);
    setCalYear(y);
    setCalMonth(m);
  };

  const handleNextMonth = () => {
    const [y, m] = adjustMonth(calYear, calMonth, 1);
    setCalYear(y);
    setCalMonth(m);
  };

  return (
    <>
      <PageHeader
        title="통계"
        description="주·월 단위 운동 통계와 신기록을 확인하세요."
      />

      <section className="px-4 py-6 md:px-8 md:py-8 space-y-6">
        {/* 기간 탭 */}
        <div className="inline-flex rounded-lg border border-neutral-200 bg-neutral-50 p-1 gap-1">
          {TABS.map(({ key, label }) => (
            <button
              key={key}
              type="button"
              onClick={() => setPeriod(key)}
              className={cn(
                'rounded-md px-4 py-1.5 text-sm font-medium transition-colors',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
                period === key
                  ? 'bg-white text-neutral-900 shadow-sm'
                  : 'text-neutral-500 hover:text-neutral-700',
              )}
              aria-pressed={period === key}
            >
              {label}
            </button>
          ))}
        </div>

        {/* 요약 카드 */}
        <SummaryCards period={period} />

        {/* 볼륨 차트 + 빈도 캘린더 — 2열 그리드 */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
          {/* 부위별 볼륨 차트 (3/5) */}
          <div className="card p-5 lg:col-span-3">
            <h2 className="mb-4 text-sm font-semibold text-neutral-700">
              부위별 볼륨
            </h2>
            <VolumeChart period={period} />
          </div>

          {/* 운동 빈도 캘린더 (2/5) */}
          <div className="card p-5 lg:col-span-2">
            <h2 className="mb-4 text-sm font-semibold text-neutral-700">
              운동 빈도
            </h2>
            <FrequencyHeatmap
              year={calYear}
              month={calMonth}
              onPrev={handlePrevMonth}
              onNext={handleNextMonth}
            />
          </div>
        </div>

        {/* PR 테이블 */}
        <div className="card p-5">
          <h2 className="mb-4 text-sm font-semibold text-neutral-700">
            개인 최고 기록 (PR)
          </h2>
          <PersonalRecordsTable />
        </div>
      </section>
    </>
  );
}

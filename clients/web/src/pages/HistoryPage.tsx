import { useMemo, useState } from 'react';

import type { SessionDetail } from '@/api/workout';
import { HistoryCalendar } from '@/components/HistoryCalendar';
import { PageHeader } from '@/components/PageHeader';
import { SessionCardList } from '@/components/SessionCardList';
import { useSessionHistoryQuery } from '@/hooks/useSessionHistory';

function pad(n: number) {
  return String(n).padStart(2, '0');
}

function adjustMonth(year: number, month: number, delta: number): [number, number] {
  let m = month + delta;
  let y = year;
  if (m < 1) { m = 12; y -= 1; }
  if (m > 12) { m = 1; y += 1; }
  return [y, m];
}

export default function HistoryPage() {
  const now = new Date();
  const [calYear, setCalYear] = useState(now.getFullYear());
  const [calMonth, setCalMonth] = useState(now.getMonth() + 1);

  const todayStr = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  const [selectedDate, setSelectedDate] = useState<string | null>(todayStr);

  const { data } = useSessionHistoryQuery();

  // Group sessions by local date string
  const sessionsByDate = useMemo(() => {
    const map: Record<string, SessionDetail[]> = {};
    if (!data) return map;
    data.content.forEach((s) => {
      const d = new Date(s.startedAt);
      const key = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
      (map[key] ??= []).push(s);
    });
    return map;
  }, [data]);

  const selectedSessions = selectedDate ? (sessionsByDate[selectedDate] ?? []) : [];

  const handlePrev = () => {
    const [y, m] = adjustMonth(calYear, calMonth, -1);
    setCalYear(y);
    setCalMonth(m);
  };

  const handleNext = () => {
    const [y, m] = adjustMonth(calYear, calMonth, 1);
    setCalYear(y);
    setCalMonth(m);
  };

  return (
    <>
      <PageHeader
        title="히스토리"
        description="지난 운동 세션을 회고하세요."
      />

      <section className="px-4 py-6 md:px-8 md:py-8">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
          {/* Calendar — 60% on desktop */}
          <div className="card p-5 lg:flex-[3]">
            <HistoryCalendar
              year={calYear}
              month={calMonth}
              selectedDate={selectedDate}
              onSelectDate={setSelectedDate}
              onPrev={handlePrev}
              onNext={handleNext}
            />
          </div>

          {/* Session list — 40% on desktop */}
          <div className="lg:flex-[2]">
            {selectedDate ? (
              <SessionCardList
                sessions={selectedSessions}
                selectedDate={selectedDate}
              />
            ) : (
              <div className="flex items-center justify-center py-16 text-sm text-neutral-400">
                날짜를 선택하면 세션 목록이 표시돼요.
              </div>
            )}
          </div>
        </div>
      </section>
    </>
  );
}

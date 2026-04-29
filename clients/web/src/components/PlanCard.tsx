import { Menu, MenuButton, MenuItem, MenuItems } from '@headlessui/react';
import { MoreVertical, Pencil, Trash2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

import type { PlanSummary } from '@/api/plan';
import { dayOfWeekLabel } from '@/theme/muscleGroup';
import { cn } from '@/lib/cn';

interface Props {
  plan: PlanSummary;
  onDelete: (plan: PlanSummary) => void;
}

// Card 의 a 태그 + 메뉴 버튼이 중첩되면 클릭 충돌이 나므로
// 카드 전체는 button(navigate) 으로, 메뉴는 stopPropagation 으로 분리.
export function PlanCard({ plan, onDelete }: Props) {
  const navigate = useNavigate();
  const goDetail = () => navigate(`/plans/${plan.planId}`);

  return (
    <div
      className={cn(
        'card relative p-5',
        'transition-shadow hover:shadow-md focus-within:shadow-md',
      )}
    >
      <button
        type="button"
        onClick={goDetail}
        // 카드 전체 클릭 영역 — 메뉴 영역만 제외하고 덮음.
        className="absolute inset-0 rounded-lg focus:outline-none
                   focus-visible:ring-2 focus-visible:ring-primary-500"
        aria-label={`루틴 ${plan.name} 열기`}
      />

      <div className="relative flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <h3 className="text-base font-semibold text-neutral-900 truncate">
            {plan.name}
          </h3>
          <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs">
            {plan.dayOfWeek !== null && plan.dayOfWeek !== undefined && (
              <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-primary-50 text-primary-700 font-medium">
                {dayOfWeekLabel[plan.dayOfWeek] ?? '?'}요일
              </span>
            )}
            {plan.isTemplate && (
              <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-neutral-100 text-neutral-600">
                템플릿
              </span>
            )}
            <span className="text-neutral-500">
              운동 {plan.exerciseCount}종목
            </span>
          </div>
        </div>

        <Menu as="div" className="relative">
          <MenuButton
            onClick={(e: React.MouseEvent) => e.stopPropagation()}
            className="inline-flex h-8 w-8 items-center justify-center rounded-md text-neutral-500
                       hover:bg-neutral-100 hover:text-neutral-800
                       focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            aria-label={`${plan.name} 메뉴 열기`}
          >
            <MoreVertical size={16} aria-hidden />
          </MenuButton>
          <MenuItems
            anchor="bottom end"
            // anchor 사용 시 z-index 가 stacking context 위에 자동 배치됨.
            className="w-36 rounded-md border border-neutral-200 bg-white shadow-lg p-1
                       focus:outline-none [--anchor-gap:6px]"
          >
            <MenuItem>
              {({ focus }) => (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    goDetail();
                  }}
                  className={cn(
                    'flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm text-neutral-700',
                    focus && 'bg-neutral-100',
                  )}
                >
                  <Pencil size={14} aria-hidden /> 수정
                </button>
              )}
            </MenuItem>
            <MenuItem>
              {({ focus }) => (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(plan);
                  }}
                  className={cn(
                    'flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm text-error-500',
                    focus && 'bg-error-100',
                  )}
                >
                  <Trash2 size={14} aria-hidden /> 삭제
                </button>
              )}
            </MenuItem>
          </MenuItems>
        </Menu>
      </div>
    </div>
  );
}

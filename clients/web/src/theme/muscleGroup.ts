import type { MuscleGroup } from '@/api/types';

// 부위 enum → 한국어 라벨. 검색 필터, 칩, 통계 차트에서 공유.
// docs/api/exercise-catalog.md 의 enum 값과 1:1.
export const muscleGroupLabel: Record<MuscleGroup, string> = {
  CHEST: '가슴',
  BACK: '등',
  SHOULDERS: '어깨',
  ARMS: '팔',
  BICEPS: '이두',
  TRICEPS: '삼두',
  LEGS: '하체',
  GLUTES: '둔근',
  CORE: '코어',
  CARDIO: '유산소',
};

export const equipmentLabel: Record<string, string> = {
  BARBELL: '바벨',
  DUMBBELL: '덤벨',
  MACHINE: '머신',
  CABLE: '케이블',
  BODYWEIGHT: '맨몸',
  BAND: '밴드',
};

// 백엔드 기준 (plan-service PlanRequests.kt): 0=월, 1=화, ..., 6=일, null=무요일.
// 주의: docs/api/plan-service.md 예시와 indexing 이 다르다 — 실제 동작은 0-based.
export const DAY_OF_WEEK_VALUES = [0, 1, 2, 3, 4, 5, 6] as const;

export const dayOfWeekLabel: Record<number, string> = {
  0: '월', 1: '화', 2: '수', 3: '목', 4: '금', 5: '토', 6: '일',
};

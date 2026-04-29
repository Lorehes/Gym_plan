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

export const dayOfWeekLabel: Record<number, string> = {
  1: '월', 2: '화', 3: '수', 4: '목', 5: '금', 6: '토', 7: '일',
};

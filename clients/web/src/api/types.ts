// 공통 응답 형식 — docs/api/common.md
// 모바일(clients/mobile/src/api/types.ts)과 동일 계약.

export interface ApiSuccess<T> {
  success: true;
  data: T;
  error: null;
  timestamp: string;
}

export interface ApiError {
  success: false;
  data: null;
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
  };
  timestamp: string;
}

export type ApiResponse<T> = ApiSuccess<T> | ApiError;

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

// docs/api/common.md "에러 코드" 표.
export type KnownErrorCode =
  | 'AUTH_INVALID_TOKEN'
  | 'AUTH_EXPIRED_TOKEN'
  | 'AUTH_DUPLICATE_EMAIL'
  | 'PLAN_NOT_FOUND'
  | 'PLAN_ACCESS_DENIED'
  | 'EXERCISE_NOT_FOUND'
  | 'SESSION_NOT_FOUND'
  | 'SESSION_ALREADY_ACTIVE'
  | 'SESSION_ALREADY_TERMINATED'
  | 'RATE_LIMIT_EXCEEDED'
  | 'NETWORK_ERROR';

export class ApiException extends Error {
  readonly code: string;
  readonly status: number;
  readonly details?: Record<string, unknown>;

  constructor(code: string, message: string, status: number, details?: Record<string, unknown>) {
    super(message);
    this.name = 'ApiException';
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

// docs/api/exercise-catalog.md 에 명시된 enum 값.
export type MuscleGroup =
  | 'CHEST' | 'BACK' | 'SHOULDERS' | 'ARMS' | 'LEGS' | 'CORE' | 'CARDIO'
  | 'BICEPS' | 'TRICEPS' | 'GLUTES';

export type Equipment =
  | 'BARBELL' | 'DUMBBELL' | 'MACHINE' | 'CABLE' | 'BODYWEIGHT' | 'BAND';

export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

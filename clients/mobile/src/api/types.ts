// 공통 응답 형식 — docs/api/common.md

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

// 알려진 에러 코드 (docs/api/common.md)
export type KnownErrorCode =
  | 'AUTH_INVALID_TOKEN'
  | 'AUTH_EXPIRED_TOKEN'
  | 'AUTH_DUPLICATE_EMAIL'
  | 'PLAN_NOT_FOUND'
  | 'PLAN_ACCESS_DENIED'
  | 'EXERCISE_NOT_FOUND'
  | 'SESSION_NOT_FOUND'
  | 'SESSION_ALREADY_ACTIVE'
  | 'RATE_LIMIT_EXCEEDED';

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

import { ApiException } from '@/api/types';

// 서버 에러 코드 → 사용자 친화 메시지.
// security-guide.md / OWASP A07: 로그인은 계정 존재 여부를 노출하지 않음.

export function loginErrorMessage(error: unknown): string {
  if (error instanceof ApiException) {
    // 401 / 403 / 그 외 자격증명 관련 — 모두 동일 메시지로 통일.
    // (이메일 미가입 vs 비밀번호 오답 구분 금지)
    if (error.status === 401 || error.status === 403 || error.status === 404) {
      return '이메일 또는 비밀번호를 확인해주세요.';
    }
    if (error.code === 'RATE_LIMIT_EXCEEDED') {
      return '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.';
    }
    if (error.code === 'NETWORK_ERROR' || error.status === 0) {
      return '네트워크에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.';
    }
    if (error.status >= 500) {
      return '서버에 일시적인 문제가 있어요. 잠시 후 다시 시도해주세요.';
    }
    return '이메일 또는 비밀번호를 확인해주세요.';
  }
  return '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.';
}

// 회원가입 에러는 입력 가이드 차원에서 일부 정보 노출 — 단,
// "이메일 존재 여부" 가 아니라 "이 이메일로는 가입할 수 없음" 으로 표현.
export interface RegisterError {
  field?: 'email' | 'password' | 'nickname';
  message: string;
}

export function registerErrorMessage(error: unknown): RegisterError {
  if (error instanceof ApiException) {
    if (error.code === 'AUTH_DUPLICATE_EMAIL') {
      return { field: 'email', message: '해당 이메일로는 가입할 수 없습니다.' };
    }
    if (error.code === 'RATE_LIMIT_EXCEEDED') {
      return { message: '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.' };
    }
    if (error.code === 'NETWORK_ERROR' || error.status === 0) {
      return { message: '네트워크에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' };
    }
    if (error.status >= 500) {
      return { message: '서버에 일시적인 문제가 있어요. 잠시 후 다시 시도해주세요.' };
    }
    if (error.status === 400) {
      return { message: error.message || '입력값을 다시 확인해주세요.' };
    }
  }
  return { message: '회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.' };
}

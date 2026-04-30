// security-guide.md: Spring Validation 기준 — 이메일, 비밀번호 8~20, 닉네임 2~20.
// 모바일 clients/mobile/src/auth/validation.ts 와 동일 규칙 + 회원가입 복잡도 강화.

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// 영문 1자 + 숫자 1자 + 특수문자 1자 — 회원가입에만 적용 (login 은 길이만).
const PASSWORD_LETTER_RE = /[A-Za-z]/;
const PASSWORD_DIGIT_RE = /[0-9]/;
const PASSWORD_SPECIAL_RE = /[^A-Za-z0-9]/;

export function validateEmail(value: string): string | null {
  if (!value) return '이메일을 입력해주세요.';
  if (!EMAIL_RE.test(value.trim())) return '올바른 이메일 형식이 아닙니다.';
  return null;
}

export function validateLoginPassword(value: string): string | null {
  if (!value) return '비밀번호를 입력해주세요.';
  if (value.length < 8) return '비밀번호는 8자 이상이어야 합니다.';
  if (value.length > 20) return '비밀번호는 20자 이하여야 합니다.';
  return null;
}

// 회원가입 — 영문/숫자/특수문자 모두 포함.
export function validateNewPassword(value: string): string | null {
  if (!value) return '비밀번호를 입력해주세요.';
  if (value.length < 8) return '비밀번호는 8자 이상이어야 합니다.';
  if (value.length > 20) return '비밀번호는 20자 이하여야 합니다.';
  if (!PASSWORD_LETTER_RE.test(value)) return '영문을 1자 이상 포함해주세요.';
  if (!PASSWORD_DIGIT_RE.test(value)) return '숫자를 1자 이상 포함해주세요.';
  if (!PASSWORD_SPECIAL_RE.test(value)) return '특수문자를 1자 이상 포함해주세요.';
  return null;
}

export function validateNickname(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return '닉네임을 입력해주세요.';
  if (trimmed.length < 2) return '닉네임은 2자 이상이어야 합니다.';
  if (trimmed.length > 20) return '닉네임은 20자 이하여야 합니다.';
  return null;
}

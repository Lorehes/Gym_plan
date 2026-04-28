// security-guide.md: Spring Validation 기준에 맞춤 (Email / 8~20 password / 2~20 nickname).

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateEmail(value: string): string | null {
  if (!value) return '이메일을 입력해주세요.';
  if (!EMAIL_RE.test(value.trim())) return '올바른 이메일 형식이 아닙니다.';
  return null;
}

export function validatePassword(value: string): string | null {
  if (!value) return '비밀번호를 입력해주세요.';
  if (value.length < 8) return '비밀번호는 8자 이상이어야 합니다.';
  if (value.length > 20) return '비밀번호는 20자 이하여야 합니다.';
  return null;
}

export function validateNickname(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return '닉네임을 입력해주세요.';
  if (trimmed.length < 2) return '닉네임은 2자 이상이어야 합니다.';
  if (trimmed.length > 20) return '닉네임은 20자 이하여야 합니다.';
  return null;
}

import { useEffect, useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import { login } from '@/api/auth';
import { useAuthStore } from '@/auth/authStore';
import { loginErrorMessage } from '@/auth/errorMessages';
import { AuthLayout } from '@/components/AuthLayout';
import { Button } from '@/components/Button';
import { Input } from '@/components/Input';
import { PasswordToggle } from '@/components/PasswordToggle';

// 로그인은 길이만 검증 — 정책 변경된 기존 계정도 로그인은 가능해야 함.
const schema = z.object({
  email: z.string().min(1, '이메일을 입력해주세요.').email('올바른 이메일 형식이 아닙니다.'),
  password: z
    .string()
    .min(1, '비밀번호를 입력해주세요.')
    .min(8, '비밀번호는 8자 이상이어야 합니다.'),
});

type FormValues = z.infer<typeof schema>;

interface LocationState {
  from?: { pathname: string };
}

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const signIn = useAuthStore((s) => s.signIn);

  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: 'onSubmit',
    reValidateMode: 'onChange',
  });

  useEffect(() => {
    setFocus('email');
  }, [setFocus]);

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const res = await login({
        email: values.email.trim(),
        password: values.password,
      });
      await signIn({ accessToken: res.accessToken, refreshToken: res.refreshToken });
      const from = (location.state as LocationState | null)?.from?.pathname ?? '/plans';
      navigate(from, { replace: true });
    } catch (err) {
      // 보안: 로그인 실패는 항상 동일 메시지 (계정 존재 여부 노출 금지).
      setFormError(loginErrorMessage(err));
    }
  });

  return (
    <AuthLayout
      title="로그인"
      description="GymPlan 웹 — 루틴을 미리 계획하고 통계를 확인하세요."
      footer={
        <>
          계정이 없으신가요?{' '}
          <Link to="/register" className="font-medium text-primary-600 hover:underline">
            회원가입
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        <Input
          {...register('email')}
          label="이메일"
          type="email"
          autoComplete="email"
          inputMode="email"
          placeholder="user@example.com"
          error={errors.email?.message}
          disabled={isSubmitting}
        />

        <Input
          {...register('password')}
          id="login-password"
          label="비밀번호"
          type={showPassword ? 'text' : 'password'}
          autoComplete="current-password"
          placeholder="비밀번호"
          error={errors.password?.message}
          disabled={isSubmitting}
          rightSlot={
            <PasswordToggle
              visible={showPassword}
              onToggle={() => setShowPassword((v) => !v)}
              ariaControls="login-password"
            />
          }
        />

        {formError && (
          <p
            role="alert"
            aria-live="polite"
            className="rounded-md bg-error-100 px-3 py-2 text-sm text-error-500"
          >
            {formError}
          </p>
        )}

        <Button type="submit" size="full" loading={isSubmitting}>
          로그인
        </Button>
      </form>
    </AuthLayout>
  );
}

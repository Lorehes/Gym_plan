import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import { register as registerApi } from '@/api/auth';
import { useAuthStore } from '@/auth/authStore';
import { registerErrorMessage } from '@/auth/errorMessages';
import { AuthLayout } from '@/components/AuthLayout';
import { Button } from '@/components/Button';
import { Input } from '@/components/Input';
import { PasswordToggle } from '@/components/PasswordToggle';

// security-guide.md: 8~20자 + 영문/숫자/특수문자 포함, 닉네임 2~20자.
const schema = z
  .object({
    email: z.string().min(1, '이메일을 입력해주세요.').email('올바른 이메일 형식이 아닙니다.'),
    nickname: z
      .string()
      .min(1, '닉네임을 입력해주세요.')
      .transform((v) => v.trim())
      .pipe(
        z
          .string()
          .min(2, '닉네임은 2자 이상이어야 합니다.')
          .max(20, '닉네임은 20자 이하여야 합니다.'),
      ),
    password: z
      .string()
      .min(1, '비밀번호를 입력해주세요.')
      .min(8, '비밀번호는 8자 이상이어야 합니다.')
      .max(20, '비밀번호는 20자 이하여야 합니다.')
      .regex(/[A-Za-z]/, '영문을 1자 이상 포함해주세요.')
      .regex(/[0-9]/, '숫자를 1자 이상 포함해주세요.')
      .regex(/[^A-Za-z0-9]/, '특수문자를 1자 이상 포함해주세요.'),
    passwordConfirm: z.string().min(1, '비밀번호 확인을 입력해주세요.'),
  })
  .refine((v) => v.password === v.passwordConfirm, {
    message: '비밀번호가 일치하지 않습니다.',
    path: ['passwordConfirm'],
  });

type FormValues = z.infer<typeof schema>;

export default function RegisterPage() {
  const navigate = useNavigate();
  const signIn = useAuthStore((s) => s.signIn);

  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setFocus,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    // 입력 중에는 잠잠하게, 제출 후에는 실시간 피드백.
    mode: 'onSubmit',
    reValidateMode: 'onChange',
  });

  useEffect(() => {
    setFocus('email');
  }, [setFocus]);

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const res = await registerApi({
        email: values.email.trim(),
        password: values.password,
        nickname: values.nickname,
      });
      // 회원가입 응답에 토큰 포함 — 즉시 인증 상태로 전환.
      await signIn({ accessToken: res.accessToken, refreshToken: res.refreshToken });
      navigate('/plans', { replace: true });
    } catch (err) {
      const e = registerErrorMessage(err);
      if (e.field) {
        // 이메일 등 특정 필드에 묶여 있는 에러 — 해당 필드 아래에 노출.
        setError(e.field, { type: 'server', message: e.message });
        setFocus(e.field);
      } else {
        setFormError(e.message);
      }
    }
  });

  return (
    <AuthLayout
      title="회원가입"
      description="이메일과 비밀번호로 시작하세요."
      footer={
        <>
          이미 계정이 있으신가요?{' '}
          <Link to="/login" className="font-medium text-primary-600 hover:underline">
            로그인
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
          {...register('nickname')}
          label="닉네임"
          type="text"
          autoComplete="nickname"
          maxLength={20}
          placeholder="GymPlan에서 사용할 이름"
          helper="2~20자"
          error={errors.nickname?.message}
          disabled={isSubmitting}
        />

        <Input
          {...register('password')}
          id="register-password"
          label="비밀번호"
          type={showPassword ? 'text' : 'password'}
          autoComplete="new-password"
          maxLength={20}
          placeholder="영문·숫자·특수문자 포함 8~20자"
          helper="영문·숫자·특수문자 각 1자 이상, 8~20자"
          error={errors.password?.message}
          disabled={isSubmitting}
          rightSlot={
            <PasswordToggle
              visible={showPassword}
              onToggle={() => setShowPassword((v) => !v)}
              ariaControls="register-password"
            />
          }
        />

        <Input
          {...register('passwordConfirm')}
          id="register-password-confirm"
          label="비밀번호 확인"
          type={showPasswordConfirm ? 'text' : 'password'}
          autoComplete="new-password"
          maxLength={20}
          placeholder="비밀번호 재입력"
          error={errors.passwordConfirm?.message}
          disabled={isSubmitting}
          rightSlot={
            <PasswordToggle
              visible={showPasswordConfirm}
              onToggle={() => setShowPasswordConfirm((v) => !v)}
              ariaControls="register-password-confirm"
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
          가입하기
        </Button>
      </form>
    </AuthLayout>
  );
}

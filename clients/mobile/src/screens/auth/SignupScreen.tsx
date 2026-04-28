import { useRef, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useMutation } from '@tanstack/react-query';

import { register } from '@/api/auth';
import { ApiException } from '@/api/types';
import { useAuthStore } from '@/auth/authStore';
import { validateEmail, validateNickname, validatePassword } from '@/auth/validation';
import { Button } from '@/components/Button';
import { Input } from '@/components/Input';
import { colors, spacing, textStyles } from '@/theme';

interface FieldErrors {
  email?: string;
  password?: string;
  passwordConfirm?: string;
  nickname?: string;
}

export function SignupScreen() {
  const signIn = useAuthStore((s) => s.signIn);

  const passwordRef = useRef<TextInput>(null);
  const passwordConfirmRef = useRef<TextInput>(null);
  const nicknameRef = useRef<TextInput>(null);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [nickname, setNickname] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: register,
    onSuccess: async (data) => {
      // 회원가입 응답에 토큰 포함 — 즉시 로그인 상태로 전환.
      await signIn({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    },
    onError: (error) => {
      if (error instanceof ApiException && error.code === 'AUTH_DUPLICATE_EMAIL') {
        setErrors((prev) => ({ ...prev, email: '이미 사용 중인 이메일입니다.' }));
        return;
      }
      if (error instanceof ApiException) {
        setFormError(error.message || '회원가입에 실패했습니다. 입력값을 확인해주세요.');
      } else {
        setFormError('네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
      }
    },
  });

  const validate = (): FieldErrors => {
    const next: FieldErrors = {};
    const eErr = validateEmail(email);
    if (eErr) next.email = eErr;
    const pErr = validatePassword(password);
    if (pErr) next.password = pErr;
    if (!passwordConfirm) next.passwordConfirm = '비밀번호 확인을 입력해주세요.';
    else if (password !== passwordConfirm) next.passwordConfirm = '비밀번호가 일치하지 않습니다.';
    const nErr = validateNickname(nickname);
    if (nErr) next.nickname = nErr;
    return next;
  };

  const handleSubmit = () => {
    setFormError(null);
    const next = validate();
    setErrors(next);
    if (Object.keys(next).length > 0) return;
    mutation.mutate({
      email: email.trim(),
      password,
      nickname: nickname.trim(),
    });
  };

  const clearFieldError = (field: keyof FieldErrors) => {
    setErrors((prev) => (prev[field] ? { ...prev, [field]: undefined } : prev));
    if (formError) setFormError(null);
  };

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.background }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={{ flexGrow: 1, padding: spacing.lg, gap: spacing.lg }}
        keyboardShouldPersistTaps="handled"
      >
        <View style={{ gap: spacing.sm, paddingBottom: spacing.md }}>
          <Text style={{ ...textStyles.h1, color: colors.text }}>계정 만들기</Text>
          <Text style={{ ...textStyles.body, color: colors.textMuted }}>
            이메일과 비밀번호로 가입하세요.
          </Text>
        </View>

        <View style={{ gap: spacing.lg }}>
          <Input
            label="이메일"
            value={email}
            onChangeText={(v) => {
              setEmail(v);
              clearFieldError('email');
            }}
            error={errors.email}
            placeholder="user@example.com"
            keyboardType="email-address"
            autoCapitalize="none"
            autoComplete="email"
            textContentType="emailAddress"
            returnKeyType="next"
            onSubmitEditing={() => passwordRef.current?.focus()}
            editable={!mutation.isPending}
          />

          <Input
            ref={passwordRef}
            label="비밀번호"
            value={password}
            onChangeText={(v) => {
              setPassword(v);
              clearFieldError('password');
            }}
            error={errors.password}
            helper="8~20자"
            placeholder="비밀번호"
            secureTextEntry
            autoCapitalize="none"
            autoComplete="new-password"
            textContentType="newPassword"
            returnKeyType="next"
            onSubmitEditing={() => passwordConfirmRef.current?.focus()}
            editable={!mutation.isPending}
          />

          <Input
            ref={passwordConfirmRef}
            label="비밀번호 확인"
            value={passwordConfirm}
            onChangeText={(v) => {
              setPasswordConfirm(v);
              clearFieldError('passwordConfirm');
            }}
            error={errors.passwordConfirm}
            placeholder="비밀번호 재입력"
            secureTextEntry
            autoCapitalize="none"
            autoComplete="new-password"
            textContentType="newPassword"
            returnKeyType="next"
            onSubmitEditing={() => nicknameRef.current?.focus()}
            editable={!mutation.isPending}
          />

          <Input
            ref={nicknameRef}
            label="닉네임"
            value={nickname}
            onChangeText={(v) => {
              setNickname(v);
              clearFieldError('nickname');
            }}
            error={errors.nickname}
            helper="2~20자"
            placeholder="GymPlan에서 사용할 이름"
            autoCapitalize="none"
            returnKeyType="done"
            onSubmitEditing={handleSubmit}
            maxLength={20}
            editable={!mutation.isPending}
          />

          {formError && (
            <View style={{ paddingHorizontal: spacing.sm }}>
              <Text style={{ ...textStyles.bodySm, color: colors.error }}>{formError}</Text>
            </View>
          )}
        </View>

        <View style={{ flex: 1 }} />

        <View style={{ paddingTop: spacing.lg }}>
          <Button onPress={handleSubmit} loading={mutation.isPending} size="full">
            가입하기
          </Button>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

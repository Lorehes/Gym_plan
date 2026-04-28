import { useRef, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useMutation } from '@tanstack/react-query';

import { login } from '@/api/auth';
import { ApiException } from '@/api/types';
import { useAuthStore } from '@/auth/authStore';
import { validateEmail, validatePassword } from '@/auth/validation';
import { Button } from '@/components/Button';
import { Input } from '@/components/Input';
import { colors, spacing, textStyles } from '@/theme';
import type { AuthScreenProps } from '@/navigation/types';

export function LoginScreen({ navigation }: AuthScreenProps<'Login'>) {
  const signIn = useAuthStore((s) => s.signIn);
  const passwordRef = useRef<TextInput>(null);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: async (data) => {
      await signIn({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    },
    onError: (error) => {
      if (error instanceof ApiException) {
        // 서버 응답: 잘못된 자격증명은 보통 401 + 메시지.
        // 보안상 "이메일/비밀번호 중 무엇이 틀렸는지"는 구분하지 않음.
        setFormError(error.message || '이메일 또는 비밀번호를 확인해주세요.');
      } else {
        setFormError('네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
      }
    },
  });

  const handleSubmit = () => {
    setFormError(null);
    const eErr = validateEmail(email);
    const pErr = validatePassword(password);
    setEmailError(eErr);
    setPasswordError(pErr);
    if (eErr || pErr) return;
    mutation.mutate({ email: email.trim(), password });
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
        <View style={{ paddingTop: spacing['2xl'], gap: spacing.sm }}>
          <Text style={{ ...textStyles.display, color: colors.text }}>GymPlan</Text>
          <Text style={{ ...textStyles.body, color: colors.textMuted }}>
            운동을 기록하고 성장을 추적하세요.
          </Text>
        </View>

        <View style={{ gap: spacing.lg, marginTop: spacing.xl }}>
          <Input
            label="이메일"
            value={email}
            onChangeText={(v) => {
              setEmail(v);
              if (emailError) setEmailError(null);
              if (formError) setFormError(null);
            }}
            error={emailError ?? undefined}
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
              if (passwordError) setPasswordError(null);
              if (formError) setFormError(null);
            }}
            error={passwordError ?? undefined}
            placeholder="8자 이상"
            secureTextEntry
            autoCapitalize="none"
            autoComplete="password"
            textContentType="password"
            returnKeyType="done"
            onSubmitEditing={handleSubmit}
            editable={!mutation.isPending}
          />

          {formError && (
            <View style={{ paddingHorizontal: spacing.sm }}>
              <Text style={{ ...textStyles.bodySm, color: colors.error }}>{formError}</Text>
            </View>
          )}
        </View>

        <View style={{ flex: 1 }} />

        <View style={{ gap: spacing.md }}>
          <Button onPress={handleSubmit} loading={mutation.isPending} size="full">
            로그인
          </Button>

          <Pressable
            onPress={() => navigation.navigate('Signup')}
            disabled={mutation.isPending}
            style={{ height: 48, alignItems: 'center', justifyContent: 'center' }}
            accessibilityRole="link"
          >
            <Text style={{ ...textStyles.body, color: colors.textMuted }}>
              계정이 없으신가요? <Text style={{ color: colors.accent }}>회원가입</Text>
            </Text>
          </Pressable>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

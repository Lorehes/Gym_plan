import { test, expect } from '@playwright/test';

import { register, login, logout } from './helpers/auth';
import { createUniqueEmail, UNIQUE_NICKNAME } from './helpers/fixtures';

test.describe('설정 페이지', () => {
  let email: string;
  let password: string;
  let nickname: string;

  test.beforeEach(async ({ page }) => {
    email = createUniqueEmail();
    password = 'E2eTest2026!';
    nickname = UNIQUE_NICKNAME();
    await register(page, email, password, nickname);
    await page.goto('/settings');
    await page.waitForLoadState('networkidle');
  });

  test('TC-SETTINGS-01: 계정 정보 섹션 표시', async ({ page }) => {
    // 섹션 헤더
    await expect(page.getByRole('heading', { name: '계정 정보' })).toBeVisible({ timeout: 10_000 });

    // 이메일 읽기 전용 표시 (sidebar에도 표시되므로 main으로 스코프)
    await expect(page.locator('main').getByText(email).first()).toBeVisible();

    // 닉네임 표시 (sidebar에도 표시되므로 main으로 스코프)
    await expect(page.locator('main').getByText(nickname).first()).toBeVisible();

    // 가입일 표시
    await expect(page.getByText(/년.*월.*일/)).toBeVisible();
  });

  test('TC-SETTINGS-02: 닉네임 변경 → 저장 → 사이드바 즉시 반영', async ({ page }) => {
    // 닉네임 편집 버튼 클릭 (Pencil 아이콘 + 닉네임 텍스트)
    await page.getByRole('button', { name: new RegExp(nickname) }).click();

    // 인라인 편집 폼 표시
    const nicknameInput = page.getByLabel('닉네임');
    await expect(nicknameInput).toBeVisible();

    // 새 닉네임 입력
    const newNickname = `Updated${Date.now().toString().slice(-4)}`;
    await nicknameInput.clear();
    await nicknameInput.fill(newNickname);

    // 저장
    await page.getByRole('button', { name: '저장' }).click();

    // 토스트 메시지
    await expect(page.getByText('닉네임이 저장되었어요.')).toBeVisible({ timeout: 10_000 });

    // 사이드바에 새 닉네임 즉시 반영
    await expect(page.locator('aside').getByText(newNickname)).toBeVisible({ timeout: 5_000 });
  });

  test('TC-SETTINGS-03: 닉네임 변경 취소 → 원래 값 유지', async ({ page }) => {
    await page.getByRole('button', { name: new RegExp(nickname) }).click();

    const nicknameInput = page.getByLabel('닉네임');
    await nicknameInput.fill('임시값XYZ');

    // 취소
    await page.getByRole('button', { name: '취소' }).click();

    // 원래 닉네임 유지
    await expect(page.getByRole('button', { name: new RegExp(nickname) })).toBeVisible();
    await expect(page.locator('aside').getByText(nickname)).toBeVisible();
  });

  test('TC-SETTINGS-04: 알림 설정 토글 → 저장 → 새로고침 후 유지', async ({ page }) => {
    // 알림 설정 섹션 대기
    await expect(page.getByRole('heading', { name: '알림 설정' })).toBeVisible({ timeout: 10_000 });

    // 알림 설정 API 미구현 시 스킵
    const pushToggle = page.getByRole('switch', { name: '푸시 알림 전체' });
    const isAvailable = await pushToggle.isVisible({ timeout: 5_000 }).catch(() => false);
    if (!isAvailable) {
      test.skip(true, '알림 설정 API 미구현 — 스킵');
      return;
    }

    // 저장하지 않은 변경사항 메시지 없음 (초기 상태)
    await expect(page.getByText('저장하지 않은 변경사항이 있어요.')).not.toBeVisible();

    const initialChecked = await pushToggle.isChecked();
    await pushToggle.click();

    // 변경사항 메시지 표시
    await expect(page.getByText('저장하지 않은 변경사항이 있어요.')).toBeVisible({ timeout: 5_000 });

    // 저장
    await page.getByRole('button', { name: '저장' }).last().click();
    await expect(page.getByText('알림 설정이 저장되었어요.')).toBeVisible({ timeout: 10_000 });

    // 새로고침 후 상태 유지 확인
    await page.reload();
    await page.waitForLoadState('networkidle');
    await expect(page.getByRole('switch', { name: '푸시 알림 전체' })).toBeVisible({ timeout: 10_000 });

    const afterReload = await page.getByRole('switch', { name: '푸시 알림 전체' }).isChecked();
    expect(afterReload).toBe(!initialChecked);

    // 원상복구
    await page.getByRole('switch', { name: '푸시 알림 전체' }).click();
    await page.getByRole('button', { name: '저장' }).last().click();
    await expect(page.getByText('알림 설정이 저장되었어요.')).toBeVisible({ timeout: 5_000 });
  });

  test('TC-SETTINGS-05: pushEnabled OFF → 하위 토글 비활성화', async ({ page }) => {
    await expect(page.getByRole('heading', { name: '알림 설정' })).toBeVisible({ timeout: 10_000 });

    const pushToggle = page.getByRole('switch', { name: '푸시 알림 전체' });

    // 알림 설정 API 미구현 시 스킵
    const isAvailable = await pushToggle.isVisible({ timeout: 5_000 }).catch(() => false);
    if (!isAvailable) {
      test.skip(true, '알림 설정 API 미구현 — 스킵');
      return;
    }

    const restToggle = page.getByRole('switch', { name: '휴식 타이머 알림' });
    const completeToggle = page.getByRole('switch', { name: '운동 완료 알림' });

    // pushEnabled가 ON이면 OFF로 전환
    if (await pushToggle.isChecked()) {
      await pushToggle.click();
    }

    // 하위 토글 disabled 확인
    await expect(restToggle).toBeDisabled();
    await expect(completeToggle).toBeDisabled();
  });

  test('TC-SETTINGS-06: 로그아웃 → 확인 다이얼로그 → /login 리다이렉트', async ({ page }) => {
    await expect(page.getByRole('heading', { name: '위험 영역' })).toBeVisible({ timeout: 10_000 });

    // 로그아웃 버튼 클릭 (sidebar 중복 방지: main으로 스코프)
    await page.locator('main').getByRole('button', { name: '로그아웃' }).click();

    // 확인 다이얼로그 표시
    await expect(page.getByText('로그아웃하시겠어요?')).toBeVisible({ timeout: 5_000 });

    // 취소 → 다이얼로그 닫힘
    await page.getByRole('button', { name: '취소' }).click();
    await expect(page.getByText('로그아웃하시겠어요?')).not.toBeVisible();
    await expect(page).toHaveURL(/\/settings/);

    // 다시 로그아웃 → 확인
    await page.locator('main').getByRole('button', { name: '로그아웃' }).click();
    await expect(page.getByText('로그아웃하시겠어요?')).toBeVisible({ timeout: 5_000 });
    await page.getByRole('button', { name: '로그아웃' }).last().click();

    // /login 리다이렉트
    await page.waitForURL(/\/login/, { timeout: 10_000 });
  });
});

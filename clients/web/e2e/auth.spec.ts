import { test, expect } from '@playwright/test';

import { login, logout, register } from './helpers/auth';
import { createUniqueEmail, UNIQUE_NICKNAME } from './helpers/fixtures';

test.describe('인증 — 회원가입 + 로그인', () => {
  test('TC-AUTH-01: 신규 가입 → /plans 리다이렉트 → 로그아웃 → 재로그인', async ({ page }) => {
    const email = createUniqueEmail();
    const password = 'E2eTest2026!';
    const nickname = UNIQUE_NICKNAME();

    // 1. 회원가입
    await register(page, email, password, nickname);
    await expect(page).toHaveURL(/\/plans/);

    // 2. 사이드바에 닉네임 표시 확인
    await expect(page.locator('aside').getByText(nickname)).toBeVisible();

    // 3. 로그아웃
    await logout(page);
    await expect(page).toHaveURL(/\/login/);

    // 4. 재로그인
    await login(page, email, password);
    await expect(page).toHaveURL(/\/plans/);
  });

  test('TC-AUTH-02: 잘못된 비밀번호 → 에러 메시지', async ({ page }) => {
    const email = createUniqueEmail();
    const password = 'Correct2026!';
    const nickname = UNIQUE_NICKNAME();

    // 가입 후 로그아웃
    await register(page, email, password, nickname);
    await logout(page);

    // 틀린 비밀번호로 로그인
    await page.goto('/login');
    await page.getByLabel('이메일').fill(email);
    await page.getByLabel('비밀번호', { exact: true }).fill('WrongPassword1!');
    await page.getByRole('button', { name: '로그인' }).click();

    // 에러 메시지 표시, 페이지 유지
    await expect(page.locator('.bg-error-100')).toContainText('이메일 또는 비밀번호를 확인해주세요.');
    await expect(page).toHaveURL(/\/login/);
  });

  test('TC-AUTH-03: 이미 인증된 상태에서 /login 접근 → /plans 리다이렉트', async ({ page }) => {
    const email = createUniqueEmail();
    const password = 'E2eTest2026!';
    const nickname = UNIQUE_NICKNAME();

    await register(page, email, password, nickname);
    // 로그인된 상태에서 /login 접근
    await page.goto('/login');
    await expect(page).toHaveURL(/\/plans/);
  });

  test('TC-AUTH-04: 빈 폼 제출 → 검증 에러', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: '로그인' }).click();

    // 이메일, 비밀번호 검증 에러
    await expect(page.getByText('이메일을 입력해주세요.')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });
});

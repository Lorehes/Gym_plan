import type { Page } from '@playwright/test';

export async function login(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.waitForLoadState('networkidle');
  await page.getByLabel('이메일').fill(email);
  await page.getByLabel('비밀번호', { exact: true }).fill(password);
  await page.getByRole('button', { name: '로그인' }).click();
  // /plans로 리다이렉트 대기
  await page.waitForURL('**/plans', { timeout: 15_000 });
}

export async function register(
  page: Page,
  email: string,
  password: string,
  nickname: string,
) {
  await page.goto('/register');
  await page.waitForLoadState('networkidle');
  await page.getByLabel('이메일').fill(email);
  await page.getByLabel('닉네임').fill(nickname);
  // exact:true — "비밀번호 확인"과 구분, PasswordToggle aria-label("비밀번호 표시") 제외
  await page.locator('input[name="password"]').fill(password);
  await page.locator('input[name="passwordConfirm"]').fill(password);
  await page.getByRole('button', { name: '가입하기' }).click();
  await page.waitForURL('**/plans', { timeout: 15_000 });
}

export async function logout(page: Page) {
  await page.getByRole('button', { name: '로그아웃' }).click();
  await page.waitForURL('**/login', { timeout: 10_000 });
}

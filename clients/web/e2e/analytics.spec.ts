import { test, expect } from '@playwright/test';

import { login } from './helpers/auth';
import { DEMO_USER } from './helpers/fixtures';

test.describe('통계 대시보드', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, DEMO_USER.email, DEMO_USER.password);
    await page.goto('/analytics');
    await page.waitForLoadState('networkidle');
  });

  test('TC-ANALYTICS-01: 요약 카드 4개 표시', async ({ page }) => {
    // 스켈레톤이 사라질 때까지 대기
    await expect(page.getByText('총 운동 횟수')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('총 볼륨')).toBeVisible();
    await expect(page.getByText('평균 운동 시간')).toBeVisible();
    await expect(page.getByText('가장 많이 한 부위')).toBeVisible();
  });

  test('TC-ANALYTICS-02: 부위별 볼륨 차트 렌더링', async ({ page }) => {
    // Recharts ResponsiveContainer 렌더링 확인
    await expect(page.locator('.recharts-wrapper')).toBeVisible({ timeout: 15_000 });
    // "부위별 볼륨" 섹션 헤더
    await expect(page.getByText('부위별 볼륨')).toBeVisible();
  });

  test('TC-ANALYTICS-03: 기간 탭 전환 (이번 주 → 이번 달)', async ({ page }) => {
    // 기본값 "이번 주" 활성 확인
    const weekTab = page.getByRole('button', { name: '이번 주' });
    await expect(weekTab).toBeVisible();
    await expect(weekTab).toHaveAttribute('aria-pressed', 'true');

    // "이번 달" 클릭
    await page.getByRole('button', { name: '이번 달' }).click();
    await expect(page.getByRole('button', { name: '이번 달' })).toHaveAttribute('aria-pressed', 'true');
    await expect(weekTab).toHaveAttribute('aria-pressed', 'false');

    // 데이터 재조회 완료 대기
    await page.waitForTimeout(500);
    await expect(page.getByText('부위별 볼륨')).toBeVisible();
  });

  test('TC-ANALYTICS-04: 빈도 캘린더 월 이동', async ({ page }) => {
    // 현재 월 표시 확인
    const now = new Date();
    await expect(page.getByText(`${now.getMonth() + 1}월`)).toBeVisible({ timeout: 10_000 });

    // 이전 달 이동
    await page.getByRole('button', { name: '이전 달' }).click();
    const prevMonth = now.getMonth() === 0 ? 12 : now.getMonth();
    await expect(page.getByText(`${prevMonth}월`)).toBeVisible();

    // 다음 달 이동 (현재 달까지만 가능)
    await page.getByRole('button', { name: '다음 달' }).click();
    await expect(page.getByText(`${now.getMonth() + 1}월`)).toBeVisible();

    // 현재 달에서 다음 달 버튼 비활성화
    await expect(page.getByRole('button', { name: '다음 달' })).toBeDisabled();
  });

  test('TC-ANALYTICS-05: PR 테이블 표시 및 정렬', async ({ page }) => {
    await expect(page.getByText('개인 최고 기록 (PR)')).toBeVisible();

    // 테이블 헤더 확인
    await expect(page.getByRole('columnheader', { name: /종목/ })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('columnheader', { name: /추정 1RM/ })).toBeVisible();

    // 종목 이름 컬럼 클릭 → 정렬 전환
    await page.getByRole('button', { name: /종목/ }).click();
    await page.waitForTimeout(200);
    await page.getByRole('button', { name: /종목/ }).click();
    // 재정렬 후 테이블 유지 확인
    await expect(page.getByRole('table')).toBeVisible();
  });
});

import { test, expect } from '@playwright/test';

import { login } from './helpers/auth';
import { DEMO_USER } from './helpers/fixtures';

test.describe('운동 히스토리', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, DEMO_USER.email, DEMO_USER.password);
    await page.goto('/history');
    await page.waitForLoadState('networkidle');
  });

  test('TC-HISTORY-01: 캘린더 표시 및 월 이동', async ({ page }) => {
    const now = new Date();

    // 현재 월 표시
    await expect(page.getByText(`${now.getMonth() + 1}월`)).toBeVisible({ timeout: 10_000 });

    // 요일 라벨 표시 (월~일)
    for (const day of ['월', '화', '수', '목', '금', '토', '일']) {
      await expect(page.getByText(day, { exact: true }).first()).toBeVisible();
    }

    // 이전 달 이동
    await page.getByRole('button', { name: '이전 달' }).click();
    const prevMonth = now.getMonth() === 0 ? 12 : now.getMonth();
    await expect(page.getByText(`${prevMonth}월`)).toBeVisible();

    // 현재 달로 복귀
    await page.getByRole('button', { name: '다음 달' }).click();
    await expect(page.getByText(`${now.getMonth() + 1}월`)).toBeVisible();

    // 현재 달에서 다음 달 버튼 비활성화
    await expect(page.getByRole('button', { name: '다음 달' })).toBeDisabled();
  });

  test('TC-HISTORY-02: 운동한 날 클릭 → 세션 카드 표시', async ({ page }) => {
    // 운동 횟수 도트가 있는 날짜 셀을 찾는다 (title 속성에 "회" 포함)
    const sessionDay = page.locator('[role="gridcell"][data-date]').filter({
      hasText: /\d+회/,
    }).first();

    // demo 사용자는 히스토리 데이터가 있어야 함
    const dayCount = await sessionDay.count();
    if (dayCount === 0) {
      // 데이터 없으면 이전 달 확인
      await page.getByRole('button', { name: '이전 달' }).click();
      await page.waitForTimeout(1000);
    }

    const anyDay = page.locator('[role="gridcell"][data-date]').filter({ hasText: /\d+회/ }).first();
    if (await anyDay.count() > 0) {
      await anyDay.click();

      // 세션 카드 목록 표시
      await expect(page.getByText(/\d+개 세션/)).toBeVisible({ timeout: 10_000 });
    }
  });

  test('TC-HISTORY-03: 세션 카드 → 상세 페이지 이동 → 뒤로가기', async ({ page }) => {
    // 운동 데이터가 있는 날짜 찾기
    let sessionDay = page.locator('[role="gridcell"][data-date]').filter({ hasText: /\d+회/ }).first();

    // 없으면 이전 달로 이동
    if (await sessionDay.count() === 0) {
      await page.getByRole('button', { name: '이전 달' }).click();
      await page.waitForTimeout(1000);
      sessionDay = page.locator('[role="gridcell"][data-date]').filter({ hasText: /\d+회/ }).first();
    }

    if (await sessionDay.count() === 0) {
      test.skip(true, 'demo 사용자 히스토리 데이터 없음 — 스킵');
      return;
    }

    await sessionDay.click();
    await page.waitForTimeout(500);

    // 세션 카드 링크 클릭
    const sessionCard = page.locator('a[href*="/history/"]').first();
    await expect(sessionCard).toBeVisible({ timeout: 10_000 });
    await sessionCard.click();

    // 세션 상세 페이지
    await page.waitForURL(/\/history\/.+/);
    await expect(page.locator('main').getByRole('link', { name: /히스토리/ })).toBeVisible();

    // 세션 헤더 (운동 이름 또는 자유 운동)
    await expect(
      page.getByText(/자유 운동/).or(page.getByRole('heading')).first()
    ).toBeVisible({ timeout: 10_000 });

    // 뒤로가기
    await page.locator('main').getByRole('link', { name: /히스토리/ }).click();
    await page.waitForURL(/\/history$/);
  });

  test('TC-HISTORY-04: 날짜 미선택 시 안내 메시지', async ({ page }) => {
    // 기본적으로 오늘이 선택되므로, 같은 날짜 재클릭으로 선택 해제
    const todayCell = page.locator('[role="gridcell"][aria-pressed="true"]').first();
    if (await todayCell.count() > 0) {
      await todayCell.click(); // 선택 해제
    }
    // 안내 메시지
    await expect(page.getByText('날짜를 선택하면 세션 목록이 표시돼요.')).toBeVisible({ timeout: 5_000 });
  });
});

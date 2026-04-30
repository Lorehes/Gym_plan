import { test, expect } from '@playwright/test';

import { register } from './helpers/auth';
import { createUniqueEmail, UNIQUE_NICKNAME } from './helpers/fixtures';

// 각 테스트마다 신규 사용자 생성 → 데이터 격리
test.describe('루틴 관리', () => {
  let email: string;
  let password: string;

  test.beforeEach(async ({ page }) => {
    email = createUniqueEmail();
    password = 'E2eTest2026!';
    await register(page, email, password, UNIQUE_NICKNAME());
  });

  test('TC-PLAN-01: 빈 상태 → 루틴 생성 → 운동 추가 → 수정 → 삭제 → 루틴 삭제', async ({ page }) => {
    // 1. 빈 상태 확인
    await page.goto('/plans');
    await page.waitForLoadState('networkidle');
    await expect(page.getByText('아직 만든 루틴이 없어요')).toBeVisible();

    // 2. 새 루틴 생성
    await page.getByRole('button', { name: /새 루틴/ }).click();
    await expect(page.getByLabel('루틴 이름')).toBeVisible({ timeout: 10_000 });

    const planName = `E2E 루틴 ${Date.now()}`;
    await page.getByLabel('루틴 이름').fill(planName);
    await page.getByRole('button', { name: '만들기' }).click();

    // 3. 루틴 상세 페이지로 이동
    await page.waitForURL(/\/plans\/.+/);
    await expect(page.getByRole('heading', { name: planName })).toBeVisible();

    // 4. 운동 추가 다이얼로그 열기
    await page.getByRole('button', { name: '운동 추가', exact: true }).click();
    await expect(page.getByPlaceholder('종목 이름 검색')).toBeVisible({ timeout: 10_000 });

    // 5. 종목 검색
    await page.getByPlaceholder('종목 이름 검색').fill('벤치');
    // 검색 결과 대기 (디바운스 300ms + API)
    await page.waitForTimeout(600);

    // 6. 첫 번째 결과 선택
    const firstResult = page.locator('[role="dialog"] button').filter({ hasText: '벤치' }).first();
    await expect(firstResult).toBeVisible({ timeout: 10_000 });
    await firstResult.click();

    // 7. 세트/횟수 설정 후 추가
    await page.waitForSelector('input[type="number"]', { timeout: 5_000 });
    await page.getByRole('button', { name: '추가' }).click();

    // 8. 다이얼로그 닫기
    await page.keyboard.press('Escape');
    await page.waitForSelector('[role="dialog"]', { state: 'hidden' });

    // 9. 운동 카드 표시 확인
    await expect(page.getByText('벤치', { exact: false }).first()).toBeVisible({ timeout: 10_000 });

    // 10. 운동 수정
    const editBtn = page.getByRole('button', { name: /수정/ }).first();
    if (await editBtn.isVisible()) {
      await editBtn.click();
      await page.waitForTimeout(500); // 편집 다이얼로그 열림 대기
      // 세트 수 수정
      const setsInput = page.getByLabel(/세트/).first();
      if (await setsInput.isVisible()) {
        await setsInput.clear();
        await setsInput.fill('4');
      }
      await page.getByRole('button', { name: '저장' }).click();
      await page.waitForSelector('[role="dialog"]', { state: 'hidden' });
    }

    // 11. 운동 삭제
    const deleteExBtn = page.getByRole('button', { name: /삭제/ }).first();
    await deleteExBtn.click();
    await expect(page.getByText('운동을 삭제할까요?')).toBeVisible({ timeout: 10_000 });
    await page.getByRole('button', { name: '삭제' }).last().click();
    await page.waitForSelector('[role="dialog"]', { state: 'hidden' });

    // 12. 루틴 삭제 (메뉴)
    await page.getByRole('button', { name: '루틴 목록' }).click();
    await page.waitForURL(/\/plans$/);

    // 루틴 카드 메뉴 열기
    const planMenu = page.getByRole('button', { name: new RegExp(`${planName} 메뉴`) });
    if (await planMenu.isVisible()) {
      await planMenu.click();
      await page.getByRole('menuitem', { name: '삭제' }).click();
      await expect(page.getByText('루틴을 삭제할까요?')).toBeVisible({ timeout: 10_000 });
      await page.getByRole('button', { name: '삭제' }).last().click();
      await page.waitForSelector('[role="dialog"]', { state: 'hidden' });
    }

    // 빈 상태로 복귀
    await expect(page.getByText('아직 만든 루틴이 없어요')).toBeVisible({ timeout: 10_000 });
  });

  test('TC-PLAN-02: 루틴 목록에서 카드 클릭 → 상세 이동', async ({ page }) => {
    // 루틴 생성
    await page.getByRole('button', { name: /새 루틴/ }).click();
    await expect(page.getByLabel('루틴 이름')).toBeVisible({ timeout: 10_000 });
    const name = `Click Test ${Date.now()}`;
    await page.getByLabel('루틴 이름').fill(name);
    await page.getByRole('button', { name: '만들기' }).click();
    await page.waitForURL(/\/plans\/.+/);

    // 목록으로 돌아가서 카드 클릭
    await page.getByRole('button', { name: '루틴 목록' }).click();
    await page.waitForURL(/\/plans$/);

    await page.getByRole('button', { name: new RegExp(`루틴 ${name} 열기`) }).click();
    await page.waitForURL(/\/plans\/.+/);
    await expect(page.getByRole('heading', { name })).toBeVisible();
  });
});

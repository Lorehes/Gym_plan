import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false, // 백엔드 공유 상태 보호
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['html', { outputFolder: 'playwright-report' }], ['list']],

  use: {
    baseURL: 'http://localhost:3000',
    actionTimeout: 10_000,
    navigationTimeout: 15_000,
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'retain-on-failure',
    locale: 'ko-KR',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  // 테스트 실행 전 dev server 기동 (필요 시 주석 해제)
  // webServer: {
  //   command: 'npm run dev',
  //   url: 'http://localhost:3000',
  //   reuseExistingServer: true,
  // },
});

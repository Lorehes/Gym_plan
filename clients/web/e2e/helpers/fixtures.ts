export const DEMO_USER = {
  email: 'demo@gymplan.test',
  password: 'DemoUser2026!',
};

export function createUniqueEmail(): string {
  return `e2e_${Date.now()}@test.local`;
}

export const UNIQUE_NICKNAME = () => `TestUser${Date.now().toString().slice(-6)}`;

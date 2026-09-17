import { afterEach, describe, expect, it, vi } from 'vitest';
import { clearAccessToken, setAccessToken } from '../auth/utils/tokenManager';
import { stubFetch } from '../test-utils/mockApi';
import { workPlanService } from './workPlanService';

const BRANCH_ID = '11111111-1111-4111-8111-111111111111';
const PLAN_ID = '22222222-2222-4222-8222-222222222222';
const PLAN = {
  id: PLAN_ID,
  branchId: BRANCH_ID,
  name: 'Тренировки',
  createdAt: '2026-09-17T10:00:00Z',
  updatedAt: '2026-09-17T10:00:00Z',
};

afterEach(() => {
  clearAccessToken();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('workPlanService', () => {
  it('FE-04: кодирует параметры списка и использует авторизованный клиент', async () => {
    setAccessToken('access-token');
    const { calls } = stubFetch([
      {
        method: 'GET',
        path: `/branches/${BRANCH_ID}/plans?limit=20&cursor=next-page&q=%D0%94%D0%BE%D0%BC`,
        status: 200,
        body: { items: [], nextCursor: null },
      },
    ]);

    await workPlanService.getWorkPlans(BRANCH_ID, { limit: 20, cursor: 'next-page', q: 'Дом' });

    expect(calls[0].authHeader).toBe('Bearer access-token');
  });

  it('FE-02 и FE-07: отправляет только name и вызывает endpoint архивации', async () => {
    setAccessToken('access-token');
    const { calls } = stubFetch([
      { method: 'POST', path: `/branches/${BRANCH_ID}/plans`, status: 201, body: PLAN },
      { method: 'PUT', path: `/work-plans/${PLAN_ID}`, status: 200, body: PLAN },
      { method: 'POST', path: `/work-plans/${PLAN_ID}/archive`, status: 204 },
    ]);

    await workPlanService.createWorkPlan(BRANCH_ID, PLAN.name);
    await workPlanService.updateWorkPlan(PLAN_ID, PLAN.name);
    await workPlanService.archiveWorkPlan(PLAN_ID);

    expect(JSON.parse(calls[0].body ?? '{}')).toEqual({ name: PLAN.name });
    expect(JSON.parse(calls[1].body ?? '{}')).toEqual({ name: PLAN.name });
    expect(calls[0].body).not.toContain('branchId');
    expect(calls[2].url).toContain(`/work-plans/${PLAN_ID}/archive`);
  });
});

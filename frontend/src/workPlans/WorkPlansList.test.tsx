import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ApiError } from '../api/client';
import { redirectHandler } from '../api/refreshInterceptor';
import { AuthProvider } from '../auth/context/AuthContext';
import { stubFetch, type MockRoute } from '../test-utils/mockApi';
import { WorkPlansList } from './WorkPlansList';
import { workPlanService, type WorkPlanPage } from './workPlanService';
import { WORK_PLAN_STRINGS } from './strings';

const BRANCH_ID = '11111111-1111-4111-8111-111111111111';
const FIRST_PLAN = {
  id: '22222222-2222-4222-8222-222222222222',
  branchId: BRANCH_ID,
  name: 'Тренировки',
  createdAt: '2026-09-17T10:00:00Z',
  updatedAt: '2026-09-17T10:00:00Z',
};

const AUTH_ROUTES: MockRoute[] = [
  { method: 'POST', path: '/auth/refresh', status: 200, body: { accessToken: 'token' } },
  { method: 'GET', path: '/auth/me', status: 200, body: { id: 'u-1', email: 'user@example.com', name: 'Иван' } },
];

function renderList() {
  vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
  const api = stubFetch(AUTH_ROUTES);
  render(
    <AuthProvider>
      <MemoryRouter>
        <WorkPlansList branchId={BRANCH_ID} onBranchNotFound={vi.fn()} />
      </MemoryRouter>
    </AuthProvider>,
  );
  return api;
}

beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('WorkPlansList', () => {
  it('FE-01: показывает пустое состояние и создание плана', async () => {
    vi.spyOn(workPlanService, 'getWorkPlans').mockResolvedValue({ items: [], nextCursor: null });
    renderList();

    expect(await screen.findByText(WORK_PLAN_STRINGS.emptyTitle)).toBeTruthy();
    expect(screen.getAllByRole('button', { name: WORK_PLAN_STRINGS.createAction }).length).toBeGreaterThan(0);
  });

  it('FE-02 и FE-03: нормализует название и не отправляет невалидное', async () => {
    vi.spyOn(workPlanService, 'getWorkPlans').mockResolvedValue({ items: [], nextCursor: null });
    const create = vi.spyOn(workPlanService, 'createWorkPlan').mockResolvedValue(FIRST_PLAN);
    renderList();

    await screen.findByText(WORK_PLAN_STRINGS.emptyTitle);
    fireEvent.click(screen.getAllByRole('button', { name: WORK_PLAN_STRINGS.createAction })[0]);
    const field = screen.getByLabelText(WORK_PLAN_STRINGS.nameLabel);
    fireEvent.change(field, { target: { value: 'A' } });
    fireEvent.submit(field.closest('form')!);
    expect(await screen.findByText(WORK_PLAN_STRINGS.nameLengthError)).toBeTruthy();
    expect(create).not.toHaveBeenCalled();

    fireEvent.change(field, { target: { value: '  Тренировки  ' } });
    fireEvent.submit(field.closest('form')!);
    expect(await screen.findByText(FIRST_PLAN.name)).toBeTruthy();
    expect(create).toHaveBeenCalledWith(BRANCH_ID, FIRST_PLAN.name);
    expect(screen.getByText(WORK_PLAN_STRINGS.createdSuccess(FIRST_PLAN.name))).toBeTruthy();
  });

  it('FE-04: показывает отдельное состояние для пустого результата поиска', async () => {
    vi.spyOn(workPlanService, 'getWorkPlans')
      .mockResolvedValueOnce({ items: [FIRST_PLAN], nextCursor: null })
      .mockResolvedValueOnce({ items: [], nextCursor: null });
    renderList();

    await screen.findByText(FIRST_PLAN.name);
    fireEvent.change(screen.getByLabelText(WORK_PLAN_STRINGS.searchLabel), { target: { value: 'нет' } });

    expect(await screen.findByText(WORK_PLAN_STRINGS.noResultsTitle)).toBeTruthy();
    expect(screen.getByRole('button', { name: WORK_PLAN_STRINGS.clearSearchAction })).toBeTruthy();
  });

  it('FE-12: после создания при активном поиске сбрасывает фильтр и показывает первую страницу', async () => {
    const created = { ...FIRST_PLAN, id: '33333333-3333-4333-8333-333333333333', name: 'Дом' };
    vi.spyOn(workPlanService, 'getWorkPlans')
      .mockResolvedValueOnce({ items: [], nextCursor: 'cursor-for-search' })
      .mockResolvedValueOnce({ items: [], nextCursor: null })
      .mockResolvedValueOnce({ items: [created], nextCursor: null });
    vi.spyOn(workPlanService, 'createWorkPlan').mockResolvedValue(created);
    renderList();

    await screen.findByLabelText(WORK_PLAN_STRINGS.searchLabel);
    fireEvent.change(screen.getByLabelText(WORK_PLAN_STRINGS.searchLabel), { target: { value: 'Дом' } });
    await waitFor(() => expect(vi.mocked(workPlanService.getWorkPlans).mock.calls).toHaveLength(2));
    fireEvent.click(screen.getByRole('button', { name: WORK_PLAN_STRINGS.createAction }));
    fireEvent.change(screen.getByLabelText(WORK_PLAN_STRINGS.nameLabel), { target: { value: created.name } });
    fireEvent.submit(screen.getByLabelText(WORK_PLAN_STRINGS.nameLabel).closest('form')!);

    expect(await screen.findByText(created.name)).toBeTruthy();
    expect(screen.getByLabelText(WORK_PLAN_STRINGS.searchLabel)).toHaveProperty('value', '');
    await waitFor(() => expect(vi.mocked(workPlanService.getWorkPlans).mock.calls).toHaveLength(3));
    expect(vi.mocked(workPlanService.getWorkPlans).mock.calls[2][1]).toEqual({ q: undefined });
  });

  it('FE-05: догружает страницу без дубликатов', async () => {
    vi.spyOn(workPlanService, 'getWorkPlans')
      .mockResolvedValueOnce({ items: [FIRST_PLAN], nextCursor: 'next-page' })
      .mockResolvedValueOnce({
        items: [FIRST_PLAN, { ...FIRST_PLAN, id: '33333333-3333-4333-8333-333333333333', name: 'Дом' }],
        nextCursor: null,
      });
    renderList();

    await screen.findByText(FIRST_PLAN.name);
    fireEvent.click(screen.getByRole('button', { name: WORK_PLAN_STRINGS.loadMoreAction }));

    expect(await screen.findByText('Дом')).toBeTruthy();
    expect(screen.getAllByText(FIRST_PLAN.name)).toHaveLength(1);
  });

  it('FE-11: устаревший поисковый ответ не заменяет актуальный', async () => {
    let resolveFirst!: (value: WorkPlanPage) => void;
    let resolveSecond!: (value: WorkPlanPage) => void;
    const first = new Promise<WorkPlanPage>((resolve) => { resolveFirst = resolve; });
    const second = new Promise<WorkPlanPage>((resolve) => { resolveSecond = resolve; });
    vi.spyOn(workPlanService, 'getWorkPlans').mockReturnValueOnce(first).mockReturnValueOnce(second);
    renderList();

    await screen.findByLabelText(WORK_PLAN_STRINGS.searchLabel);
    fireEvent.change(screen.getByLabelText(WORK_PLAN_STRINGS.searchLabel), { target: { value: 'Дом' } });
    await waitFor(() => expect(vi.mocked(workPlanService.getWorkPlans).mock.calls).toHaveLength(2));
    resolveSecond({ items: [{ ...FIRST_PLAN, name: 'Дом' }], nextCursor: null });
    expect(await screen.findByText('Дом')).toBeTruthy();
    resolveFirst({ items: [FIRST_PLAN], nextCursor: null });
    await waitFor(() => expect(screen.queryByText(FIRST_PLAN.name)).toBeNull());
  });

  it('FE-15: 502 показывает повтор без refresh/logout', async () => {
    vi.spyOn(workPlanService, 'getWorkPlans').mockRejectedValue(
      new ApiError(502, { code: 'UPSTREAM_UNAVAILABLE', message: 'internal failure' }),
    );
    const { calls } = renderList();

    expect(await screen.findByText(WORK_PLAN_STRINGS.upstreamUnavailable)).toBeTruthy();
    expect(screen.getByRole('button', { name: WORK_PLAN_STRINGS.retryAction })).toBeTruthy();
    expect(calls.filter((call) => call.method === 'POST' && call.url.includes('/auth/refresh'))).toHaveLength(1);
  });
});

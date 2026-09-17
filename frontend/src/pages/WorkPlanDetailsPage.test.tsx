import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ApiError } from '../api/client';
import { redirectHandler } from '../api/refreshInterceptor';
import { AuthProvider } from '../auth/context/AuthContext';
import { branchService } from '../branches/branchService';
import { ProtectedRoute } from '../routes/ProtectedRoute';
import { stubFetch, type MockRoute } from '../test-utils/mockApi';
import { workPlanService } from '../workPlans/workPlanService';
import { WORK_PLAN_STRINGS } from '../workPlans/strings';
import WorkPlanDetailsPage from './WorkPlanDetailsPage';

const BRANCH = {
  id: '11111111-1111-4111-8111-111111111111',
  name: 'Спорт',
  createdAt: '2026-09-16T10:00:00Z',
  updatedAt: '2026-09-16T10:00:00Z',
};
const PLAN = {
  id: '22222222-2222-4222-8222-222222222222',
  branchId: BRANCH.id,
  name: 'Тренировки',
  createdAt: '2026-09-17T10:00:00Z',
  updatedAt: '2026-09-17T10:00:00Z',
};
const AUTH_ROUTES: MockRoute[] = [
  { method: 'POST', path: '/auth/refresh', status: 200, body: { accessToken: 'token' } },
  { method: 'GET', path: '/auth/me', status: 200, body: { id: 'u-1', email: 'user@example.com', name: 'Иван' } },
];

function renderDetails() {
  vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
  stubFetch(AUTH_ROUTES);
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={[`/work-plans/${PLAN.id}`]}>
        <Routes>
          <Route path="/work-plans/:planId" element={<ProtectedRoute><WorkPlanDetailsPage /></ProtectedRoute>} />
          <Route path="/branches/:branchId" element={<div>BranchDetails</div>} />
          <Route path="/branches" element={<div>Branches</div>} />
          <Route path="/login" element={<div>LoginScreen</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
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

describe('WorkPlanDetailsPage', () => {
  it('FE-06: переименование сразу обновляет заголовок', async () => {
    vi.spyOn(workPlanService, 'getWorkPlan').mockResolvedValue(PLAN);
    vi.spyOn(branchService, 'getBranch').mockResolvedValue(BRANCH);
    vi.spyOn(workPlanService, 'updateWorkPlan').mockResolvedValue({ ...PLAN, name: 'Кардио' });
    renderDetails();

    await screen.findByText(WORK_PLAN_STRINGS.tasksPlaceholder);
    fireEvent.click(screen.getByRole('button', { name: WORK_PLAN_STRINGS.renameTitle }));
    fireEvent.change(screen.getByLabelText(WORK_PLAN_STRINGS.nameLabel), { target: { value: 'Кардио' } });
    fireEvent.click(screen.getByRole('button', { name: WORK_PLAN_STRINGS.saveAction }));

    expect(await screen.findByRole('heading', { name: 'Кардио' })).toBeTruthy();
  });

  it('FE-07: отмена не архивирует план, подтверждение возвращает к ветке', async () => {
    vi.spyOn(workPlanService, 'getWorkPlan').mockResolvedValue(PLAN);
    vi.spyOn(branchService, 'getBranch').mockResolvedValue(BRANCH);
    const archive = vi.spyOn(workPlanService, 'archiveWorkPlan').mockResolvedValue(undefined);
    renderDetails();

    await screen.findByText(WORK_PLAN_STRINGS.tasksPlaceholder);
    fireEvent.click(screen.getByRole('button', { name: WORK_PLAN_STRINGS.archiveAction }));
    fireEvent.click(await screen.findByRole('button', { name: WORK_PLAN_STRINGS.cancelAction }));
    expect(archive).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: WORK_PLAN_STRINGS.archiveAction }));
    fireEvent.click(await screen.findByRole('button', { name: WORK_PLAN_STRINGS.archiveConfirmAction }));
    expect(await screen.findByText('BranchDetails')).toBeTruthy();
    expect(archive).toHaveBeenCalledWith(PLAN.id);
  });

  it('FE-16: Escape отменяет диалог, а повторное подтверждение во время запроса не дублируется', async () => {
    vi.spyOn(workPlanService, 'getWorkPlan').mockResolvedValue(PLAN);
    vi.spyOn(branchService, 'getBranch').mockResolvedValue(BRANCH);
    let resolveArchive!: () => void;
    const archive = vi.spyOn(workPlanService, 'archiveWorkPlan').mockReturnValue(
      new Promise<void>((resolve) => { resolveArchive = resolve; }),
    );
    renderDetails();

    await screen.findByText(WORK_PLAN_STRINGS.tasksPlaceholder);
    const archiveButton = screen.getByRole('button', { name: WORK_PLAN_STRINGS.archiveAction });
    fireEvent.click(archiveButton);
    const dialog = await screen.findByRole('dialog');
    fireEvent.keyDown(dialog, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).toBeNull();

    fireEvent.click(archiveButton);
    const confirm = await screen.findByRole('button', { name: WORK_PLAN_STRINGS.archiveConfirmAction });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    expect(archive).toHaveBeenCalledTimes(1);
    expect(confirm).toHaveProperty('disabled', true);
    resolveArchive();
  });

  it('FE-08 и FE-14: 404 плана или родительской ветки показывает единый экран', async () => {
    vi.spyOn(workPlanService, 'getWorkPlan').mockResolvedValue(PLAN);
    vi.spyOn(branchService, 'getBranch').mockRejectedValue(new ApiError(404, { code: 'NOT_FOUND' }));
    renderDetails();

    expect(await screen.findByText(WORK_PLAN_STRINGS.notFoundTitle)).toBeTruthy();
  });
});

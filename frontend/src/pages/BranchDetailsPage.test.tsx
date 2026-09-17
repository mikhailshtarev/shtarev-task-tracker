import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../auth/context/AuthContext';
import { ApiError } from '../api/client';
import { redirectHandler } from '../api/refreshInterceptor';
import { branchService } from '../branches/branchService';
import { BRANCH_STRINGS } from '../branches/strings';
import { ProtectedRoute } from '../routes/ProtectedRoute';
import { stubFetch, type MockRoute } from '../test-utils/mockApi';
import { workPlanService } from '../workPlans/workPlanService';
import { WORK_PLAN_STRINGS } from '../workPlans/strings';
import BranchDetailsPage from './BranchDetailsPage';

const BRANCH = {
  id: '11111111-1111-4111-8111-111111111111',
  name: 'Спорт',
  createdAt: '2026-09-16T10:00:00Z',
  updatedAt: '2026-09-16T10:00:00Z',
};

const AUTH_ROUTES: MockRoute[] = [
  { method: 'POST', path: '/auth/refresh', status: 200, body: { accessToken: 'token' } },
  {
    method: 'GET',
    path: '/auth/me',
    status: 200,
    body: { id: 'u-1', email: 'user@example.com', name: 'Иван' },
  },
];

function renderDetails(branchId = BRANCH.id) {
  vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
  vi.spyOn(workPlanService, 'getWorkPlans').mockResolvedValue({ items: [], nextCursor: null });
  stubFetch(AUTH_ROUTES);
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={[`/branches/${branchId}`]}>
        <Routes>
          <Route
            path="/branches/:branchId"
            element={
              <ProtectedRoute>
                <BranchDetailsPage />
              </ProtectedRoute>
            }
          />
          <Route path="/branches" element={<div>BranchesScreen</div>} />
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

describe('BranchDetailsPage', () => {
  it('F-5: показывает список планов ветки', async () => {
    vi.spyOn(branchService, 'getBranch').mockResolvedValue(BRANCH);
    renderDetails();

    expect(await screen.findByText(WORK_PLAN_STRINGS.emptyTitle)).toBeTruthy();
    expect(screen.getByRole('heading', { name: BRANCH.name })).toBeTruthy();
  });

  it('FE-05: переименование сразу обновляет заголовок ветки', async () => {
    vi.spyOn(branchService, 'getBranch').mockResolvedValue(BRANCH);
    vi.spyOn(branchService, 'updateBranch').mockResolvedValue({ ...BRANCH, name: 'Здоровье' });
    renderDetails();

    await screen.findByText(WORK_PLAN_STRINGS.emptyTitle);
    fireEvent.click(screen.getByRole('button', { name: BRANCH_STRINGS.renameTitle }));
    fireEvent.change(screen.getByLabelText(BRANCH_STRINGS.nameLabel), { target: { value: 'Здоровье' } });
    fireEvent.click(screen.getByRole('button', { name: BRANCH_STRINGS.saveAction }));

    expect(await screen.findByRole('heading', { name: 'Здоровье' })).toBeTruthy();
  });

  it('FE-06 и FE-12: отмена архивации возвращает фокус и не отправляет запрос', async () => {
    vi.spyOn(branchService, 'getBranch').mockResolvedValue(BRANCH);
    const archive = vi.spyOn(branchService, 'archiveBranch');
    renderDetails();

    await screen.findByText(WORK_PLAN_STRINGS.emptyTitle);
    const archiveButton = screen.getByRole('button', { name: BRANCH_STRINGS.archiveAction });
    fireEvent.click(archiveButton);
    const cancelButton = await screen.findByRole('button', { name: BRANCH_STRINGS.cancelAction });
    expect(document.activeElement).toBe(cancelButton);
    fireEvent.click(cancelButton);

    await waitFor(() => expect(document.activeElement).toBe(archiveButton));
    expect(archive).not.toHaveBeenCalled();
  });

  it('FE-06: архивирует после подтверждения и возвращает к списку', async () => {
    vi.spyOn(branchService, 'getBranch').mockResolvedValue(BRANCH);
    vi.spyOn(branchService, 'archiveBranch').mockResolvedValue(undefined);
    renderDetails();

    await screen.findByText(WORK_PLAN_STRINGS.emptyTitle);
    fireEvent.click(screen.getByRole('button', { name: BRANCH_STRINGS.archiveAction }));
    fireEvent.click(await screen.findByRole('button', { name: BRANCH_STRINGS.archiveConfirmAction }));

    expect(await screen.findByText('BranchesScreen')).toBeTruthy();
  });

  it('FE-07: 404 показывает понятный экран', async () => {
    vi.spyOn(branchService, 'getBranch').mockRejectedValue(new ApiError(404, { code: 'NOT_FOUND' }));
    renderDetails();

    expect(await screen.findByText(BRANCH_STRINGS.notFoundTitle)).toBeTruthy();
  });
});

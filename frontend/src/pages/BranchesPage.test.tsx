import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../auth/context/AuthContext';
import { redirectHandler } from '../api/refreshInterceptor';
import { ProtectedRoute } from '../routes/ProtectedRoute';
import { stubFetch, type MockRoute } from '../test-utils/mockApi';
import { ApiError } from '../api/client';
import { branchService, type BranchPage } from '../branches/branchService';
import { BRANCH_STRINGS } from '../branches/strings';
import BranchesPage from './BranchesPage';

const FIRST_BRANCH = {
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

function renderBranches() {
  vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
  const api = stubFetch(AUTH_ROUTES);
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/branches']}>
        <Routes>
          <Route
            path="/branches"
            element={
              <ProtectedRoute>
                <BranchesPage />
              </ProtectedRoute>
            }
          />
          <Route path="/branches/:branchId" element={<div>BranchDetails</div>} />
          <Route path="/login" element={<div>LoginScreen</div>} />
        </Routes>
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

describe('BranchesPage', () => {
  it('FE-01: показывает пустое состояние и действие создания', async () => {
    vi.spyOn(branchService, 'getBranches').mockResolvedValue({ items: [], nextCursor: null });
    renderBranches();

    expect(await screen.findByText(BRANCH_STRINGS.emptyTitle)).toBeTruthy();
    expect(screen.getAllByRole('button', { name: BRANCH_STRINGS.createAction }).length).toBeGreaterThan(0);
  });

  it('FE-02: после создания ветка появляется в списке', async () => {
    vi.spyOn(branchService, 'getBranches')
      .mockResolvedValueOnce({ items: [], nextCursor: null })
      .mockResolvedValue({ items: [FIRST_BRANCH], nextCursor: null });
    const create = vi.spyOn(branchService, 'createBranch').mockResolvedValue(FIRST_BRANCH);
    renderBranches();

    await screen.findByText(BRANCH_STRINGS.emptyTitle);
    fireEvent.click(screen.getAllByRole('button', { name: BRANCH_STRINGS.createAction })[0]);
    fireEvent.change(screen.getByLabelText(BRANCH_STRINGS.nameLabel), { target: { value: '  Спорт  ' } });
    fireEvent.submit(screen.getByLabelText(BRANCH_STRINGS.nameLabel).closest('form')!);

    expect(await screen.findByText(FIRST_BRANCH.name)).toBeTruthy();
    expect(create).toHaveBeenCalledWith('Спорт');
  });

  it('FE-02a и FE-11: догружает следующую страницу и не дублирует ветку', async () => {
    vi.spyOn(branchService, 'getBranches')
      .mockResolvedValueOnce({ items: [FIRST_BRANCH], nextCursor: 'next-page' })
      .mockResolvedValueOnce({
        items: [FIRST_BRANCH, { ...FIRST_BRANCH, id: '22222222-2222-4222-8222-222222222222', name: 'Дом' }],
        nextCursor: null,
      });
    renderBranches();

    await screen.findByText('Спорт');
    fireEvent.click(screen.getByRole('button', { name: BRANCH_STRINGS.loadMoreAction }));

    expect(await screen.findByText('Дом')).toBeTruthy();
    expect(screen.getAllByText('Спорт')).toHaveLength(1);
  });

  it('FE-03: не отправляет создание для невалидного названия', async () => {
    vi.spyOn(branchService, 'getBranches').mockResolvedValue({ items: [], nextCursor: null });
    const create = vi.spyOn(branchService, 'createBranch');
    renderBranches();

    await screen.findByText(BRANCH_STRINGS.emptyTitle);
    fireEvent.click(screen.getAllByRole('button', { name: BRANCH_STRINGS.createAction })[0]);
    fireEvent.change(screen.getByLabelText(BRANCH_STRINGS.nameLabel), { target: { value: 'A' } });
    fireEvent.submit(screen.getByLabelText(BRANCH_STRINGS.nameLabel).closest('form')!);

    expect(await screen.findByText(BRANCH_STRINGS.nameLengthError)).toBeTruthy();
    expect(create).not.toHaveBeenCalled();
  });

  it('FE-04: показывает серверную ошибку валидации у поля', async () => {
    vi.spyOn(branchService, 'getBranches').mockResolvedValue({ items: [], nextCursor: null });
    vi.spyOn(branchService, 'createBranch').mockRejectedValue(
      new ApiError(400, { details: [{ field: 'name', message: 'Такое название недопустимо' }] }),
    );
    renderBranches();

    await screen.findByText(BRANCH_STRINGS.emptyTitle);
    fireEvent.click(screen.getAllByRole('button', { name: BRANCH_STRINGS.createAction })[0]);
    fireEvent.change(screen.getByLabelText(BRANCH_STRINGS.nameLabel), { target: { value: 'Спорт' } });
    fireEvent.submit(screen.getByLabelText(BRANCH_STRINGS.nameLabel).closest('form')!);

    expect(await screen.findByText('Такое название недопустимо')).toBeTruthy();
  });

  it('FE-09: устаревший поисковый ответ не перезаписывает новый результат', async () => {
    let resolveFirst!: (value: BranchPage) => void;
    let resolveSecond!: (value: BranchPage) => void;
    const first = new Promise<BranchPage>((resolve) => { resolveFirst = resolve; });
    const second = new Promise<BranchPage>((resolve) => { resolveSecond = resolve; });
    vi.spyOn(branchService, 'getBranches')
      .mockReturnValueOnce(first)
      .mockReturnValueOnce(second);
    renderBranches();

    await screen.findByLabelText(BRANCH_STRINGS.searchLabel);
    fireEvent.change(screen.getByLabelText(BRANCH_STRINGS.searchLabel), { target: { value: 'Дом' } });
    await waitFor(() => expect(vi.mocked(branchService.getBranches).mock.calls).toHaveLength(2));
    resolveSecond({ items: [{ ...FIRST_BRANCH, name: 'Дом' }], nextCursor: null });
    expect(await screen.findByText('Дом')).toBeTruthy();
    resolveFirst({ items: [FIRST_BRANCH], nextCursor: null });
    await waitFor(() => expect(screen.queryByText('Спорт')).toBeNull());
  });

  it('FE-10: показывает отсутствие результатов и сбрасывает поиск', async () => {
    vi.spyOn(branchService, 'getBranches')
      .mockResolvedValueOnce({ items: [FIRST_BRANCH], nextCursor: null })
      .mockResolvedValueOnce({ items: [], nextCursor: null })
      .mockResolvedValueOnce({ items: [FIRST_BRANCH], nextCursor: null });
    renderBranches();

    await screen.findByText('Спорт');
    fireEvent.change(screen.getByLabelText(BRANCH_STRINGS.searchLabel), { target: { value: 'Нет' } });
    expect(await screen.findByText(BRANCH_STRINGS.noResultsTitle)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: BRANCH_STRINGS.clearSearchAction }));
    expect(await screen.findByText('Спорт')).toBeTruthy();
  });

  it('FE-16: 502 показывает повтор без обновления токена', async () => {
    vi.spyOn(branchService, 'getBranches').mockRejectedValue(
      new ApiError(502, { code: 'UPSTREAM_UNAVAILABLE', message: 'internal failure' }),
    );
    const { calls } = renderBranches();

    expect(await screen.findByText(BRANCH_STRINGS.upstreamUnavailable)).toBeTruthy();
    expect(screen.getByRole('button', { name: BRANCH_STRINGS.retryAction })).toBeTruthy();
    expect(calls.filter((call) => call.method === 'POST' && call.url.includes('/auth/refresh'))).toHaveLength(1);
  });
});

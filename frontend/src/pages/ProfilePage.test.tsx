import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../auth/context/AuthContext';
import { redirectHandler } from '../api/refreshInterceptor';
import { stubFetch, type MockRoute } from '../test-utils/mockApi';
import { ProtectedRoute } from '../routes/ProtectedRoute';
import ProfilePage from './ProfilePage';

const LOGGED_IN_ROUTES: MockRoute[] = [
  { method: 'POST', path: '/auth/refresh', status: 200, body: { accessToken: 'token' } },
  {
    method: 'GET',
    path: '/auth/me',
    status: 200,
    body: { id: 'u-1', email: 'user@example.com', name: 'Старое имя' },
  },
];

function renderProfile(routes: MockRoute[]) {
  vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
  const api = stubFetch(routes);
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/profile']}>
        <ProtectedRoute>
          <ProfilePage />
        </ProtectedRoute>
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

describe('ProfilePage', () => {
  it('FE-04: сохраняет имя и сразу обновляет его в шапке', async () => {
    const { calls } = renderProfile([
      ...LOGGED_IN_ROUTES,
      {
        method: 'PUT',
        path: '/auth/me',
        status: 200,
        body: { id: 'u-1', email: 'user@example.com', name: 'Новое имя' },
      },
    ]);

    const input = await screen.findByDisplayValue('Старое имя');
    fireEvent.change(input, { target: { value: 'Новое имя' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    expect(await screen.findByText('Профиль сохранён')).toBeTruthy();
    expect(screen.getByText('Новое имя')).toBeTruthy();
    const put = calls.find((call) => call.method === 'PUT' && call.url.includes('/auth/me'));
    expect(JSON.parse(put?.body ?? '{}')).toEqual({ name: 'Новое имя' });
  });

  it('FE-05: показывает серверную ошибку под полем имени', async () => {
    renderProfile([
      ...LOGGED_IN_ROUTES,
      {
        method: 'PUT',
        path: '/auth/me',
        status: 400,
        body: {
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Проверьте данные',
            details: [{ field: 'name', message: 'Имя слишком длинное' }],
          },
        },
      },
    ]);

    const input = await screen.findByDisplayValue('Старое имя');
    fireEvent.change(input, { target: { value: 'Новое имя' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    expect(await screen.findByText('Имя слишком длинное')).toBeTruthy();
    await waitFor(() => expect(input.classList.contains('error')).toBe(true));
  });

  it('отправляет null при очистке имени', async () => {
    const { calls } = renderProfile([
      ...LOGGED_IN_ROUTES,
      {
        method: 'PUT',
        path: '/auth/me',
        status: 200,
        body: { id: 'u-1', email: 'user@example.com', name: null },
      },
    ]);

    const input = await screen.findByDisplayValue('Старое имя');
    fireEvent.change(input, { target: { value: '   ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    await screen.findByText('Профиль сохранён');
    const put = calls.find((call) => call.method === 'PUT' && call.url.includes('/auth/me'));
    expect(JSON.parse(put?.body ?? '{}')).toEqual({ name: null });
  });
});

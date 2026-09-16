import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './ProtectedRoute';
import { AuthProvider } from '../auth/context/AuthContext';
import { redirectHandler } from '../api/refreshInterceptor';
import { stubFetch } from '../test-utils/mockApi';

beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function renderDashboard(initialEntry: string, routes: Parameters<typeof stubFetch>[0]) {
  vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
  stubFetch(routes);
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <div>SecretDashboard</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>LoginScreen</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('ProtectedRoute', () => {
  it('F-17: гость → /dashboard — редирект на /login', async () => {
    renderDashboard('/dashboard', [
      {
        method: 'POST',
        path: '/auth/refresh',
        status: 401,
        body: {
          error: { code: 'INVALID_REFRESH_TOKEN', message: 'Токен обновления недействителен' },
        },
      },
    ]);

    expect(await screen.findByText('LoginScreen')).toBeTruthy();
    expect(screen.queryByText('SecretDashboard')).toBeNull();
  });

  it.each(['/profile', '/settings'])('FE-11: гость на %s — редирект на /login', async (path) => {
    vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
    stubFetch([
      {
        method: 'POST',
        path: '/auth/refresh',
        status: 401,
        body: {
          error: { code: 'INVALID_REFRESH_TOKEN', message: 'Токен обновления недействителен' },
        },
      },
    ]);
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route
              path={path}
              element={
                <ProtectedRoute>
                  <div>ProtectedPage</div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<div>LoginScreen</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    expect(await screen.findByText('LoginScreen')).toBeTruthy();
    expect(screen.queryByText('ProtectedPage')).toBeNull();
  });

  it('F-17: isAuthLoading — редиректа нет', async () => {
    const fetchMock = vi.fn(async () => new Promise<Response>(() => {}));
    vi.stubGlobal('fetch', fetchMock);
    vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/dashboard']}>
          <Routes>
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <div>SecretDashboard</div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<div>LoginScreen</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    // Silent refresh "висит" — ни контента, ни редиректа.
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByText('LoginScreen')).toBeNull();
    expect(screen.queryByText('SecretDashboard')).toBeNull();
  });
});

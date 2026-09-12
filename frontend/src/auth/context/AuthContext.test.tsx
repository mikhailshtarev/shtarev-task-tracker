import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { AuthProvider } from './AuthContext';
import { useAuth } from '../hooks/useAuth';
import { redirectHandler } from '../../api/refreshInterceptor';
import { stubFetch } from '../../test-utils/mockApi';

// Пробный компонент: показывает состояние авторизации контекста.
function AuthStateProbe() {
  const { user, isAuthLoading } = useAuth();
  if (isAuthLoading) {
    return <div>Загрузка…</div>;
  }
  return <div>{user ? user.email : 'guest'}</div>;
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

describe('AuthContext: silent refresh', () => {
  it('F-18: POST /auth/refresh → 200 + GET /auth/me → 200 — пользователь залогинен после «перезагрузки»', async () => {
    vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
    stubFetch([
      {
        method: 'POST',
        path: '/auth/refresh',
        status: 200,
        body: { accessToken: 'restored-token' },
      },
      {
        method: 'GET',
        path: '/auth/me',
        status: 200,
        body: { id: 'u-1', email: 'user@example.com', name: 'Иван' },
      },
    ]);

    render(
      <AuthProvider>
        <AuthStateProbe />
      </AuthProvider>,
    );

    // До завершения silent refresh — состояние загрузки, без редиректов.
    expect(screen.getByText('Загрузка…')).toBeTruthy();

    expect(await screen.findByText('user@example.com')).toBeTruthy();
  });
});

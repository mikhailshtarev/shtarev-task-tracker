import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ChangePasswordForm from './ChangePasswordForm';
import { AuthProvider } from '../context/AuthContext';
import { getAccessToken } from '../utils/tokenManager';
import { redirectHandler } from '../../api/refreshInterceptor';
import { stubFetch, type MockRoute } from '../../test-utils/mockApi';

const LOGGED_IN_ROUTES: MockRoute[] = [
  {
    method: 'POST',
    path: '/auth/refresh',
    status: 200,
    body: { accessToken: 'valid-token' },
  },
  {
    method: 'GET',
    path: '/auth/me',
    status: 200,
    body: { id: 'u-1', email: 'user@example.com', name: 'Иван' },
  },
];

beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('ChangePasswordForm', () => {
  it('F-16: 200 — очистка state, редирект на /login', async () => {
    vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
    stubFetch([
      ...LOGGED_IN_ROUTES,
      {
        method: 'POST',
        path: '/auth/change-password',
        status: 200,
        body: { message: 'Пароль успешно изменён' },
      },
    ]);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/change-password']}>
          <Routes>
            <Route path="/change-password" element={<ChangePasswordForm />} />
            <Route path="/login" element={<div>LoginScreen</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    // Ждём завершения silent refresh — токен получен.
    await waitFor(() => expect(getAccessToken()).toBe('valid-token'));

    fireEvent.change(screen.getByLabelText('Текущий пароль'), {
      target: { value: 'SecurePass1' },
    });
    fireEvent.change(screen.getByLabelText('Новый пароль'), {
      target: { value: 'NewSecurePass2' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Сменить пароль' }));

    expect(await screen.findByText('LoginScreen')).toBeTruthy();
    expect(getAccessToken()).toBeNull();
  });
});

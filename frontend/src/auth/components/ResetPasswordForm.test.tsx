import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ResetPasswordForm from './ResetPasswordForm';
import { AuthProvider } from '../context/AuthContext';
import { redirectHandler } from '../../api/refreshInterceptor';
import { stubFetch, type MockRoute } from '../../test-utils/mockApi';

const GUEST_ROUTES: MockRoute[] = [
  {
    method: 'POST',
    path: '/auth/refresh',
    status: 401,
    body: {
      error: { code: 'INVALID_REFRESH_TOKEN', message: 'Токен обновления недействителен' },
    },
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

describe('ResetPasswordForm', () => {
  it('F-15: 409 PASSWORD_TOO_RECENT — ошибка под полем нового пароля', async () => {
    vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
    const { fetchMock } = stubFetch([
      ...GUEST_ROUTES,
      {
        method: 'POST',
        path: '/auth/reset-password',
        status: 409,
        body: {
          error: {
            code: 'PASSWORD_TOO_RECENT',
            message: 'Нельзя использовать последний пароль',
          },
        },
      },
    ]);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/reset-password?token=abc123']}>
          <ResetPasswordForm />
        </MemoryRouter>
      </AuthProvider>,
    );

    fireEvent.change(screen.getByLabelText('Новый пароль'), {
      target: { value: 'SecurePass1' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить пароль' }));

    expect(
      await screen.findByText('Нельзя использовать последний пароль'),
    ).toBeTruthy();

    const passwordInput = screen.getByLabelText('Новый пароль') as HTMLInputElement;
    expect(passwordInput.className).toContain('error');

    const resetCalls = fetchMock.mock.calls.filter(([input]) =>
      String(input).includes('/auth/reset-password'),
    );
    expect(resetCalls).toHaveLength(1);
  });
});

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import EmailConfirmPage from './EmailConfirmPage';
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

function setupPage(routes: MockRoute[]) {
  vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
  const { fetchMock } = stubFetch([...GUEST_ROUTES, ...routes]);
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/confirm-email?token=abc123']}>
        <Routes>
          <Route path="/confirm-email" element={<EmailConfirmPage />} />
          <Route path="/login" element={<div>LoginScreen</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
  return { fetchMock };
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

describe('EmailConfirmPage', () => {
  it('F-12: ?token=... → 200 — редирект на /login', async () => {
    setupPage([
      {
        method: 'GET',
        path: '/auth/confirm',
        status: 200,
        body: { message: 'Email подтверждён' },
      },
    ]);

    expect(await screen.findByText('LoginScreen')).toBeTruthy();
  });

  it('F-13: 400 INVALID_TOKEN — ошибка и кнопка повторной отправки письма', async () => {
    setupPage([
      {
        method: 'GET',
        path: '/auth/confirm',
        status: 400,
        body: {
          error: {
            code: 'INVALID_TOKEN',
            message: 'Неверный или истёкший токен подтверждения',
          },
        },
      },
      {
        method: 'POST',
        path: '/auth/resend-confirmation',
        status: 200,
        body: {
          message:
            'Если email зарегистрирован и не подтверждён, вы получите письмо',
        },
      },
    ]);

    expect(
      await screen.findByText('Неверный или истёкший токен подтверждения'),
    ).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Отправить письмо повторно' }),
    ).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'user@example.com' },
    });
    fireEvent.click(
      screen.getByRole('button', { name: 'Отправить письмо повторно' }),
    );

    expect(
      await screen.findByText(
        'Если email зарегистрирован и не подтверждён, вы получите письмо',
      ),
    ).toBeTruthy();
  });
});

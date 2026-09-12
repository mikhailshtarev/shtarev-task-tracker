import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import RegisterForm from './RegisterForm';
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

function setupForm(routes: MockRoute[]) {
  vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
  const { fetchMock } = stubFetch([...GUEST_ROUTES, ...routes]);
  render(
    <AuthProvider>
      <MemoryRouter>
        <RegisterForm />
      </MemoryRouter>
    </AuthProvider>,
  );
  return { fetchMock };
}

function fillForm(email = 'user@example.com', password = 'SecurePass1') {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } });
  fireEvent.change(screen.getByLabelText('Пароль'), { target: { value: password } });
  fireEvent.change(screen.getByLabelText('Подтверждение пароля'), {
    target: { value: password },
  });
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

describe('RegisterForm', () => {
  it('F-10: сабмит с валидными данными → 201 — экран «Подтвердите email»', async () => {
    setupForm([
      {
        method: 'POST',
        path: '/auth/register',
        status: 201,
        body: { message: 'Проверьте email для подтверждения' },
      },
    ]);
    fillForm();

    fireEvent.click(screen.getByRole('button', { name: 'Зарегистрироваться' }));

    expect(
      await screen.findByText('Проверьте email для подтверждения'),
    ).toBeTruthy();
  });

  it('F-11: 409 EMAIL_ALREADY_EXISTS — ошибка под полем email', async () => {
    setupForm([
      {
        method: 'POST',
        path: '/auth/register',
        status: 409,
        body: {
          error: {
            code: 'EMAIL_ALREADY_EXISTS',
            message: 'Пользователь с таким email уже существует',
          },
        },
      },
    ]);
    fillForm('taken@example.com');

    fireEvent.click(screen.getByRole('button', { name: 'Зарегистрироваться' }));

    expect(
      await screen.findByText('Пользователь с таким email уже существует'),
    ).toBeTruthy();

    const emailInput = screen.getByLabelText('Email') as HTMLInputElement;
    expect(emailInput.className).toContain('error');
  });
});

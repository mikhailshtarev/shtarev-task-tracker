import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginForm from './LoginForm';
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
  const { fetchMock, calls } = stubFetch([...GUEST_ROUTES, ...routes]);
  render(
    <AuthProvider>
      <MemoryRouter>
        <LoginForm />
      </MemoryRouter>
    </AuthProvider>,
  );
  return { fetchMock, calls };
}

function fillCredentials(email = 'user@example.com', password = 'password123') {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } });
  fireEvent.change(screen.getByLabelText('Пароль'), { target: { value: password } });
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

describe('LoginForm', () => {
  it('F-06: сабмит с невалидными полями — запрос не отправлен, ошибки валидации под полями', () => {
    const { fetchMock } = setupForm([]);

    fireEvent.click(screen.getByRole('button', { name: 'Войти' }));

    expect(screen.getByText('Email должен содержать от 5 до 254 символов')).toBeTruthy();
    expect(screen.getByText('Введите пароль')).toBeTruthy();

    const loginCalls = fetchMock.mock.calls.filter(([input]) =>
      String(input).includes('/auth/login'),
    );
    expect(loginCalls).toHaveLength(0);
  });

  it('F-07: API вернул INVALID_CREDENTIALS — сообщение «Неверный email или пароль»', async () => {
    const { fetchMock } = setupForm([
      {
        method: 'POST',
        path: '/auth/login',
        status: 401,
        body: {
          error: { code: 'INVALID_CREDENTIALS', message: 'Неверный email или пароль' },
        },
      },
    ]);
    fillCredentials();

    fireEvent.click(screen.getByRole('button', { name: 'Войти' }));

    expect(await screen.findByText('Неверный email или пароль')).toBeTruthy();
    const loginCalls = fetchMock.mock.calls.filter(([input]) =>
      String(input).includes('/auth/login'),
    );
    expect(loginCalls).toHaveLength(1);
  });

  it('F-08: API вернул EMAIL_NOT_CONFIRMED — сообщение и кнопка повторной отправки письма', async () => {
    setupForm([
      {
        method: 'POST',
        path: '/auth/login',
        status: 401,
        body: {
          error: { code: 'EMAIL_NOT_CONFIRMED', message: 'Подтвердите email для входа' },
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
    fillCredentials();

    fireEvent.click(screen.getByRole('button', { name: 'Войти' }));

    expect(await screen.findByText('Подтвердите email для входа')).toBeTruthy();

    fireEvent.click(
      screen.getByRole('button', { name: 'Отправить письмо повторно' }),
    );

    expect(
      await screen.findByText(
        'Если email зарегистрирован и не подтверждён, вы получите письмо',
      ),
    ).toBeTruthy();
  });

  it('F-09: API вернул RATE_LIMITED — сообщение, кнопка заблокирована', async () => {
    setupForm([
      {
        method: 'POST',
        path: '/auth/login',
        status: 429,
        body: {
          error: {
            code: 'RATE_LIMITED',
            message: 'Слишком много попыток. Попробуйте через 15 минут',
          },
        },
      },
    ]);
    fillCredentials();

    fireEvent.click(screen.getByRole('button', { name: 'Войти' }));

    expect(
      await screen.findByText('Слишком много попыток. Попробуйте через 15 минут'),
    ).toBeTruthy();

    const submitButton = screen.getByRole('button', {
      name: 'Вход заблокирован',
    }) as HTMLButtonElement;
    expect(submitButton.disabled).toBe(true);
  });

  it('берёт значения из DOM при автозаполнении без React onChange', async () => {
    const { fetchMock } = setupForm([
      {
        method: 'POST',
        path: '/auth/login',
        status: 401,
        body: {
          error: { code: 'INVALID_CREDENTIALS', message: 'Неверный email или пароль' },
        },
      },
    ]);
    const email = screen.getByLabelText('Email') as HTMLInputElement;
    const password = screen.getByLabelText('Пароль') as HTMLInputElement;
    const setNativeValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
    setNativeValue.call(email, 'autofill@example.com');
    setNativeValue.call(password, 'Password123');

    fireEvent.submit(email.closest('form')!);

    expect(await screen.findByText('Неверный email или пароль')).toBeTruthy();
    expect(fetchMock.mock.calls.filter(([input]) => String(input).includes('/auth/login'))).toHaveLength(1);
  });
});

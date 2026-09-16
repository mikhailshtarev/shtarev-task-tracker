import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../auth/context/AuthContext';
import { redirectHandler } from '../api/refreshInterceptor';
import { stubFetch, type MockRoute } from '../test-utils/mockApi';
import SettingsPage from './SettingsPage';

const SETTINGS = {
  estimationUnit: 'hours',
  pomodoroMinutes: 25,
  gameModeEnabled: false,
  budgetHourCost: 5,
};

const LOGGED_IN_ROUTES: MockRoute[] = [
  { method: 'POST', path: '/auth/refresh', status: 200, body: { accessToken: 'token' } },
  {
    method: 'GET',
    path: '/auth/me',
    status: 200,
    body: { id: 'u-1', email: 'user@example.com', name: 'Иван' },
  },
];

function renderSettings(routes: MockRoute[]) {
  vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
  const api = stubFetch(routes);
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/settings']}>
        <SettingsPage />
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

describe('SettingsPage', () => {
  it('FE-06: загружает и заполняет текущие настройки', async () => {
    renderSettings([
      ...LOGGED_IN_ROUTES,
      { method: 'GET', path: '/auth/settings', status: 200, body: SETTINGS },
    ]);

    expect(await screen.findByLabelText('Стоимость часа для бюджета (z)')).toHaveProperty('value', '5');
    expect((screen.getByLabelText('Часы') as HTMLInputElement).checked).toBe(true);
    expect((screen.getByLabelText('Игровой режим включён') as HTMLInputElement).checked).toBe(false);
  });

  it('FE-07: показывает длительность только для помидоров', async () => {
    renderSettings([
      ...LOGGED_IN_ROUTES,
      { method: 'GET', path: '/auth/settings', status: 200, body: SETTINGS },
    ]);

    await screen.findByText('Оценка');
    expect(screen.queryByLabelText('Длительность помидора (мин)')).toBeNull();
    fireEvent.click(screen.getByLabelText('Помидоры'));
    expect(screen.getByLabelText('Длительность помидора (мин)')).toBeTruthy();
    fireEvent.click(screen.getByLabelText('Часы'));
    expect(screen.queryByLabelText('Длительность помидора (мин)')).toBeNull();
  });

  it('FE-08: не отправляет запрос при клиентской ошибке', async () => {
    const { calls } = renderSettings([
      ...LOGGED_IN_ROUTES,
      { method: 'GET', path: '/auth/settings', status: 200, body: SETTINGS },
    ]);

    const budgetInput = await screen.findByLabelText('Стоимость часа для бюджета (z)');
    fireEvent.change(budgetInput, { target: { value: '1001' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    expect(await screen.findByText('Укажите целое число от 1 до 1000')).toBeTruthy();
    expect(calls.some((call) => call.method === 'PUT' && call.url.includes('/auth/settings'))).toBe(false);
  });

  it('FE-09: отправляет полное обновление и показывает подтверждение', async () => {
    const updated = { ...SETTINGS, gameModeEnabled: true };
    const { calls } = renderSettings([
      ...LOGGED_IN_ROUTES,
      { method: 'GET', path: '/auth/settings', status: 200, body: SETTINGS },
      { method: 'PUT', path: '/auth/settings', status: 200, body: updated },
    ]);

    const checkbox = await screen.findByLabelText('Игровой режим включён');
    fireEvent.click(checkbox);
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    expect(await screen.findByText('Настройки сохранены')).toBeTruthy();
    const put = calls.find((call) => call.method === 'PUT' && call.url.includes('/auth/settings'));
    expect(JSON.parse(put?.body ?? '{}')).toEqual(updated);
  });

  it('FE-10: показывает серверную ошибку под соответствующим полем', async () => {
    renderSettings([
      ...LOGGED_IN_ROUTES,
      { method: 'GET', path: '/auth/settings', status: 200, body: SETTINGS },
      {
        method: 'PUT',
        path: '/auth/settings',
        status: 400,
        body: {
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Проверьте настройки',
            details: [{ field: 'budgetHourCost', message: 'Некорректная стоимость' }],
          },
        },
      },
    ]);

    fireEvent.click(await screen.findByRole('button', { name: 'Сохранить' }));

    expect(await screen.findByText('Некорректная стоимость')).toBeTruthy();
  });

  it('FE-12: при сбое загрузки показывает понятное сообщение без формы', async () => {
    renderSettings([
      ...LOGGED_IN_ROUTES,
      {
        method: 'GET',
        path: '/auth/settings',
        status: 503,
        body: { error: { code: 'UNAVAILABLE', message: 'Настройки временно недоступны' } },
      },
    ]);

    expect(await screen.findByText('Настройки временно недоступны')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Сохранить' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Попробовать снова' })).toBeTruthy();
  });
});

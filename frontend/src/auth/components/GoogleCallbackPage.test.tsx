import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import GoogleCallbackPage from './GoogleCallbackPage';
import { GOOGLE_STATE_KEY } from './GoogleLoginButton';
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

describe('GoogleCallbackPage', () => {
  it('F-14: state из URL не совпадает с sessionStorage — ошибка, POST /auth/google не отправлен', async () => {
    sessionStorage.setItem(GOOGLE_STATE_KEY, 'saved-state');
    vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
    const { fetchMock } = stubFetch(GUEST_ROUTES);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/google-callback?code=abc&state=wrong-state']}>
          <Routes>
            <Route path="/google-callback" element={<GoogleCallbackPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    expect(
      await screen.findByText(/недействительный параметр state/i),
    ).toBeTruthy();

    const googleCalls = fetchMock.mock.calls.filter(([input]) =>
      String(input).includes('/auth/google'),
    );
    expect(googleCalls).toHaveLength(0);
  });
});

import { ApiError, request } from './client';
import { clearAccessToken, getAccessToken, setAccessToken } from '../auth/utils/tokenManager';

// Общий promise на refresh: при параллельных 401 выполняется ровно один
// POST /auth/refresh, остальные запросы ждут его результата (раздел 3.3 контракта).
let refreshPromise: Promise<string> | null = null;

export function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = request<{ accessToken: string }>('/auth/refresh', {
      method: 'POST',
    })
      .then((data) => {
        setAccessToken(data.accessToken);
        return data.accessToken;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

// Вынесено для тестируемости: редирект на /login при невозможности обновить токен.
export const redirectHandler = {
  go(path: string) {
    window.location.assign(path);
  },
};

function withAuthorization(options: RequestInit, token: string | null): RequestInit {
  if (!token) {
    return options;
  }
  return {
    ...options,
    headers: {
      ...options.headers,
      Authorization: `Bearer ${token}`,
    },
  };
}

export async function authorizedRequest<T>(
  endpoint: string,
  options: RequestInit = {},
): Promise<T> {
  try {
    return await request<T>(endpoint, withAuthorization(options, getAccessToken()));
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) {
      throw error;
    }
    try {
      const token = await refreshAccessToken();
      return await request<T>(endpoint, withAuthorization(options, token));
    } catch (refreshError) {
      clearAccessToken();
      redirectHandler.go('/login');
      throw refreshError;
    }
  }
}

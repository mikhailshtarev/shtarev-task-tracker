import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAccessToken, getAccessToken, setAccessToken } from '../auth/utils/tokenManager';
import { authorizedRequest, redirectHandler } from './refreshInterceptor';

function jsonResponse(status: number, body?: unknown): Response {
  const init: ResponseInit = {
    status,
    headers: { 'Content-Type': 'application/json' },
  };
  return body === undefined
    ? new Response(null, init)
    : new Response(JSON.stringify(body), init);
}

describe('refreshInterceptor', () => {
  beforeEach(() => {
    clearAccessToken();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    clearAccessToken();
  });

  it('F-04: при двух параллельных 401 выполняется ровно один POST /auth/refresh, оба запроса повторены с новым токеном', async () => {
    setAccessToken('old-token');
    let refreshed = false;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : String(input);
      const auth = new Headers(init?.headers).get('Authorization');

      if (url.includes('/auth/refresh')) {
        refreshed = true;
        return jsonResponse(200, { accessToken: 'new-token' });
      }
      if (refreshed && auth === 'Bearer new-token') {
        return jsonResponse(200, { ok: true });
      }
      return jsonResponse(401, {
        error: { code: 'UNAUTHORIZED', message: 'Требуется авторизация' },
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    const [first, second] = await Promise.all([
      authorizedRequest<{ ok: boolean }>('/api/v1/tasks'),
      authorizedRequest<{ ok: boolean }>('/api/v1/projects'),
    ]);

    expect(first).toEqual({ ok: true });
    expect(second).toEqual({ ok: true });

    const urls = fetchMock.mock.calls.map(([input]) => String(input));
    const refreshCalls = urls.filter((url) => url.includes('/auth/refresh'));
    expect(refreshCalls).toHaveLength(1);

    // Оба исходных запроса повторены, каждый в двух экземплярах (401 + ретрай),
    // ретраи несут новый токен.
    const retriedWithNewToken = fetchMock.mock.calls.filter(
      ([, init]) =>
        new Headers(init?.headers).get('Authorization') === 'Bearer new-token',
    );
    expect(retriedWithNewToken).toHaveLength(2);
    expect(getAccessToken()).toBe('new-token');
  });

  it('F-05: refresh вернул 401 — токен очищен, редирект на /login', async () => {
    setAccessToken('expired-token');
    const fetchMock = vi.fn(async () =>
      jsonResponse(401, {
        error: {
          code: 'INVALID_REFRESH_TOKEN',
          message: 'Токен обновления недействителен',
        },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const goSpy = vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});

    await expect(authorizedRequest('/api/v1/tasks')).rejects.toThrow();
    expect(goSpy).toHaveBeenCalledWith('/login');
    expect(getAccessToken()).toBeNull();
  });
});

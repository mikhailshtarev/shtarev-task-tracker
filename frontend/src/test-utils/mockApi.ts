import { vi } from 'vitest';

export interface MockRoute {
  method: string;
  path: string;
  status: number;
  body?: unknown;
}

export interface RecordedCall {
  method: string;
  url: string;
  body: string | null;
  authHeader: string | null;
}

export function jsonResponse(status: number, body?: unknown): Response {
  const init: ResponseInit = {
    status,
    headers: { 'Content-Type': 'application/json' },
  };
  return body === undefined
    ? new Response(null, init)
    : new Response(JSON.stringify(body), init);
}

// Мок глобального fetch: маршруты сопоставляются по method + подстроке path в URL.
export function stubFetch(routes: MockRoute[]) {
  const calls: RecordedCall[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url =
      typeof input === 'string'
        ? input
        : input instanceof URL
          ? input.toString()
          : input.url;
    const method = (init?.method ?? 'GET').toUpperCase();
    const headers = new Headers(init?.headers);
    calls.push({
      method,
      url,
      body: init?.body ? String(init.body) : null,
      authHeader: headers.get('Authorization'),
    });
    const route = routes.find((r) => r.method === method && url.includes(r.path));
    if (!route) {
      throw new Error(`Неожиданный запрос: ${method} ${url}`);
    }
    return jsonResponse(route.status, route.body);
  });
  vi.stubGlobal('fetch', fetchMock);
  return { fetchMock, calls };
}

import { afterEach, describe, expect, it, vi } from 'vitest';
import { setAccessToken, clearAccessToken } from '../auth/utils/tokenManager';
import { stubFetch } from '../test-utils/mockApi';
import { branchService } from './branchService';

afterEach(() => {
  clearAccessToken();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('branchService', () => {
  it('кодирует параметры списка и использует существующий Bearer-клиент', async () => {
    setAccessToken('access-token');
    const { calls } = stubFetch([
      {
        method: 'GET',
        path: '/branches?limit=20&cursor=cursor-value&q=%D0%94%D0%BE%D0%BC',
        status: 200,
        body: { items: [], nextCursor: null },
      },
    ]);

    await branchService.getBranches({ limit: 20, cursor: 'cursor-value', q: 'Дом' });

    expect(calls[0].authHeader).toBe('Bearer access-token');
  });

  it('отправляет только название при создании и использует archive endpoint', async () => {
    setAccessToken('access-token');
    const branchId = '11111111-1111-4111-8111-111111111111';
    const { calls } = stubFetch([
      { method: 'POST', path: `/branches/${branchId}/archive`, status: 204 },
      {
        method: 'POST',
        path: '/branches',
        status: 201,
        body: { id: branchId, name: 'Спорт', createdAt: '2026-09-16T10:00:00Z', updatedAt: '2026-09-16T10:00:00Z' },
      },
    ]);

    await branchService.createBranch('Спорт');
    await branchService.archiveBranch(branchId);

    expect(JSON.parse(calls[0].body ?? '{}')).toEqual({ name: 'Спорт' });
    expect(calls[1].url).toContain(`/branches/${branchId}/archive`);
  });

  it('отправляет parentId только для дочернего проекта', async () => {
    setAccessToken('access-token');
    const parentId = '11111111-1111-4111-8111-111111111111';
    const { calls } = stubFetch([
      {
        method: 'POST',
        path: '/branches',
        status: 201,
        body: { id: '22222222-2222-4222-8222-222222222222', name: 'Проект X', parentId, depth: 2 },
      },
    ]);

    await branchService.createBranch('Проект X', parentId);

    expect(JSON.parse(calls[0].body ?? '{}')).toEqual({ name: 'Проект X', parentId });
  });
});

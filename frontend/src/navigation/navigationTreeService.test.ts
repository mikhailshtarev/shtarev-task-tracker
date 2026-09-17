import { afterEach, describe, expect, it, vi } from 'vitest';
import { clearAccessToken, setAccessToken } from '../auth/utils/tokenManager';
import { stubFetch } from '../test-utils/mockApi';
import { navigationTreeService } from './navigationTreeService';

afterEach(() => {
  clearAccessToken();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('navigationTreeService', () => {
  it('FE-09: передаёт параметры раскрытого уровня через URLSearchParams', async () => {
    setAccessToken('access-token');
    const { calls } = stubFetch([
      {
        method: 'GET',
        path: '/navigation/tree?parentType=branch&parentId=11111111-1111-4111-8111-111111111111&limit=500&cursor=next-page',
        status: 200,
        body: { items: [], nextCursor: null },
      },
    ]);

    await navigationTreeService.getTree({
      parentType: 'branch',
      parentId: '11111111-1111-4111-8111-111111111111',
      limit: 500,
      cursor: 'next-page',
    });

    expect(calls[0].authHeader).toBe('Bearer access-token');
  });
});

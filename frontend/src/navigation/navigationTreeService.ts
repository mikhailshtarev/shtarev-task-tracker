import { authorizedRequest } from '../api/refreshInterceptor';

export type NavigationNodeType = 'branch' | 'work_plan' | 'task';

export interface NavigationTreeNode {
  id: string;
  type: NavigationNodeType;
  parentId: string | null;
  name: string;
  depth: number;
  hasChildren: boolean;
  archivedAt?: string | null;
}

export interface NavigationTreePage {
  items: NavigationTreeNode[];
  nextCursor: string | null;
}

export interface NavigationTreeParams {
  parentType?: Extract<NavigationNodeType, 'branch' | 'work_plan'>;
  parentId?: string;
  limit?: number;
  cursor?: string;
}

function withQuery(params: NavigationTreeParams): string {
  const search = new URLSearchParams();
  if (params.parentType) {
    search.set('parentType', params.parentType);
  }
  if (params.parentId) {
    search.set('parentId', params.parentId);
  }
  if (params.limit !== undefined) {
    search.set('limit', String(params.limit));
  }
  if (params.cursor) {
    search.set('cursor', params.cursor);
  }
  const query = search.toString();
  return query ? `/navigation/tree?${query}` : '/navigation/tree';
}

export const navigationTreeService = {
  getTree(params: NavigationTreeParams = {}, signal?: AbortSignal): Promise<NavigationTreePage> {
    return authorizedRequest<NavigationTreePage>(withQuery(params), { signal });
  },
};

import { authorizedRequest } from '../api/refreshInterceptor';

export interface Branch {
  id: string;
  name: string;
  parentId?: string | null;
  depth?: number;
  createdAt: string;
  updatedAt: string;
}

export interface BranchPage {
  items: Branch[];
  nextCursor: string | null;
}

export interface BranchInput {
  name: string;
  parentId?: string | null;
}

export type BranchErrorCode =
  | 'VALIDATION_ERROR'
  | 'UNAUTHORIZED'
  | 'NOT_FOUND'
  | 'UPSTREAM_UNAVAILABLE';

export interface GetBranchesParams {
  limit?: number;
  cursor?: string;
  q?: string;
}

function withQuery(endpoint: string, params: GetBranchesParams): string {
  const search = new URLSearchParams();
  if (params.limit !== undefined) {
    search.set('limit', String(params.limit));
  }
  if (params.cursor) {
    search.set('cursor', params.cursor);
  }
  if (params.q) {
    search.set('q', params.q);
  }
  const query = search.toString();
  return query ? `${endpoint}?${query}` : endpoint;
}

export const branchService = {
  getBranches(params: GetBranchesParams = {}, signal?: AbortSignal): Promise<BranchPage> {
    return authorizedRequest<BranchPage>(withQuery('/branches', params), { signal });
  },

  createBranch(name: string, parentId?: string | null): Promise<Branch> {
    return authorizedRequest<Branch>('/branches', {
      method: 'POST',
      body: JSON.stringify(parentId ? { name, parentId } : { name }),
    });
  },

  getBranch(branchId: string, signal?: AbortSignal): Promise<Branch> {
    return authorizedRequest<Branch>(`/branches/${encodeURIComponent(branchId)}`, { signal });
  },

  updateBranch(branchId: string, name: string): Promise<Branch> {
    return authorizedRequest<Branch>(`/branches/${encodeURIComponent(branchId)}`, {
      method: 'PUT',
      body: JSON.stringify({ name }),
    });
  },

  archiveBranch(branchId: string): Promise<void> {
    return authorizedRequest<void>(`/branches/${encodeURIComponent(branchId)}/archive`, {
      method: 'POST',
    });
  },
};

import { authorizedRequest } from '../api/refreshInterceptor';

export interface WorkPlan {
  id: string;
  branchId: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface WorkPlanPage {
  items: WorkPlan[];
  nextCursor: string | null;
}

export interface WorkPlanInput {
  name: string;
}

export type WorkPlanErrorCode =
  | 'VALIDATION_ERROR'
  | 'UNAUTHORIZED'
  | 'NOT_FOUND'
  | 'UPSTREAM_UNAVAILABLE';

export interface GetWorkPlansParams {
  limit?: number;
  cursor?: string;
  q?: string;
}

function withQuery(endpoint: string, params: GetWorkPlansParams): string {
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

export const workPlanService = {
  getWorkPlans(
    branchId: string,
    params: GetWorkPlansParams = {},
    signal?: AbortSignal,
  ): Promise<WorkPlanPage> {
    return authorizedRequest<WorkPlanPage>(
      withQuery(`/branches/${encodeURIComponent(branchId)}/plans`, params),
      { signal },
    );
  },

  createWorkPlan(branchId: string, name: string): Promise<WorkPlan> {
    return authorizedRequest<WorkPlan>(`/branches/${encodeURIComponent(branchId)}/plans`, {
      method: 'POST',
      body: JSON.stringify({ name }),
    });
  },

  getWorkPlan(planId: string, signal?: AbortSignal): Promise<WorkPlan> {
    return authorizedRequest<WorkPlan>(`/work-plans/${encodeURIComponent(planId)}`, { signal });
  },

  updateWorkPlan(planId: string, name: string): Promise<WorkPlan> {
    return authorizedRequest<WorkPlan>(`/work-plans/${encodeURIComponent(planId)}`, {
      method: 'PUT',
      body: JSON.stringify({ name }),
    });
  },

  archiveWorkPlan(planId: string): Promise<void> {
    return authorizedRequest<void>(`/work-plans/${encodeURIComponent(planId)}/archive`, {
      method: 'POST',
    });
  },
};

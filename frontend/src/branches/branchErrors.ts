import { ApiError } from '../api/client';
import { BRANCH_STRINGS } from './strings';

export function toBranchApiError(error: unknown, fallback: string): ApiError {
  if (error instanceof ApiError) {
    if (error.status === 502 || error.code === 'UPSTREAM_UNAVAILABLE') {
      return new ApiError(502, { message: BRANCH_STRINGS.upstreamUnavailable });
    }
    return error;
  }
  return new ApiError(0, { message: fallback });
}

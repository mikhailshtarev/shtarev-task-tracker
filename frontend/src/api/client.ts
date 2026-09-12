export interface ApiErrorDetail {
  field?: string;
  message: string;
}

export interface ApiErrorBody {
  code?: string;
  message?: string;
  details?: ApiErrorDetail[];
}

export class ApiError extends Error {
  status: number;
  code?: string;
  details?: ApiErrorDetail[];

  constructor(status: number, body?: ApiErrorBody | null) {
    super(body?.message ?? 'Ошибка сервера');
    this.name = 'ApiError';
    this.status = status;
    this.code = body?.code;
    this.details = body?.details;
  }
}

const BASE_URL = '/api/v1';

export async function request<T>(
  endpoint: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${BASE_URL}${endpoint}`, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, body?.error);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

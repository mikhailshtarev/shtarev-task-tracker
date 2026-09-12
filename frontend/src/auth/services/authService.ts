import { request } from '../../api/client';
import { authorizedRequest } from '../../api/refreshInterceptor';

export interface User {
  id: string;
  email: string;
  name: string | null;
}

export interface TokenResponse {
  accessToken: string;
}

export interface MessageResponse {
  message: string;
}

export const authService = {
  register(email: string, password: string): Promise<MessageResponse> {
    return request<MessageResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
  },

  confirm(token: string): Promise<MessageResponse> {
    return request<MessageResponse>(
      `/auth/confirm?token=${encodeURIComponent(token)}`,
    );
  },

  resendConfirmation(email: string): Promise<MessageResponse> {
    return request<MessageResponse>('/auth/resend-confirmation', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });
  },

  login(email: string, password: string): Promise<TokenResponse> {
    return request<TokenResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
  },

  me(): Promise<User> {
    return authorizedRequest<User>('/auth/me');
  },

  refresh(): Promise<TokenResponse> {
    return request<TokenResponse>('/auth/refresh', { method: 'POST' });
  },

  logout(): Promise<void> {
    return request<void>('/auth/logout', { method: 'POST' });
  },

  changePassword(currentPassword: string, newPassword: string): Promise<MessageResponse> {
    return authorizedRequest<MessageResponse>('/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    });
  },

  google(code: string): Promise<TokenResponse> {
    return request<TokenResponse>('/auth/google', {
      method: 'POST',
      body: JSON.stringify({ code }),
    });
  },

  forgotPassword(email: string): Promise<MessageResponse> {
    return request<MessageResponse>('/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });
  },

  resetPassword(token: string, password: string): Promise<MessageResponse> {
    return request<MessageResponse>('/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ token, password }),
    });
  },
};

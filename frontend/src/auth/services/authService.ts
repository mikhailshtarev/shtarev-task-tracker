import { request } from '../../api/client';
import { authorizedRequest } from '../../api/refreshInterceptor';

export interface UserProfile {
  id: string;
  email: string;
  name: string | null;
}

// Совместимый алиас для компонентов авторизации F-2.
export type User = UserProfile;

export type EstimationUnit = 'hours' | 'pomodoros';

export interface UserSettings {
  estimationUnit: EstimationUnit;
  pomodoroMinutes: number;
  gameModeEnabled: boolean;
  budgetHourCost: number;
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

  updateProfile(name: string | null): Promise<UserProfile> {
    return authorizedRequest<UserProfile>('/auth/me', {
      method: 'PUT',
      body: JSON.stringify({ name }),
    });
  },

  getSettings(): Promise<UserSettings> {
    return authorizedRequest<UserSettings>('/auth/settings');
  },

  updateSettings(settings: UserSettings): Promise<UserSettings> {
    return authorizedRequest<UserSettings>('/auth/settings', {
      method: 'PUT',
      body: JSON.stringify(settings),
    });
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

import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { authService, type User } from '../services/authService';
import { clearAccessToken, setAccessToken } from '../utils/tokenManager';
import { refreshAccessToken } from '../../api/refreshInterceptor';

export interface AuthContextValue {
  accessToken: string | null;
  user: User | null;
  isAuthLoading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  loginWithGoogle: (code: string) => Promise<void>;
  confirmEmail: (token: string) => Promise<void>;
  resendConfirmation: (email: string) => Promise<void>;
  forgotPassword: (email: string) => Promise<void>;
  resetPassword: (token: string, password: string) => Promise<void>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessTokenState] = useState<string | null>(null);
  const [isAuthLoading, setIsAuthLoading] = useState(true);

  // Silent refresh при старте (раздел 3.4 контракта): до завершения —
  // isAuthLoading = true и никаких редиректов.
  useEffect(() => {
    let cancelled = false;
    refreshAccessToken()
      .then(async (token) => {
        const me = await authService.me();
        if (cancelled) {
          return;
        }
        setAccessTokenState(token);
        setUser(me);
      })
      .catch(() => {
        // Нет валидной refresh-cookie — пользователь гость.
      })
      .finally(() => {
        if (!cancelled) {
          setIsAuthLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const { accessToken: token } = await authService.login(email, password);
    setAccessToken(token);
    const me = await authService.me();
    setAccessTokenState(token);
    setUser(me);
  }, []);

  const loginWithGoogle = useCallback(async (code: string) => {
    const { accessToken: token } = await authService.google(code);
    setAccessToken(token);
    const me = await authService.me();
    setAccessTokenState(token);
    setUser(me);
  }, []);

  const register = useCallback(async (email: string, password: string) => {
    await authService.register(email, password);
  }, []);

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } catch {
      // При 401 всё равно разлогиниваемся локально (раздел 2.7 контракта).
    }
    clearAccessToken();
    setAccessTokenState(null);
    setUser(null);
  }, []);

  const confirmEmail = useCallback(async (token: string) => {
    await authService.confirm(token);
  }, []);

  const resendConfirmation = useCallback(async (email: string) => {
    await authService.resendConfirmation(email);
  }, []);

  const forgotPassword = useCallback(async (email: string) => {
    await authService.forgotPassword(email);
  }, []);

  const resetPassword = useCallback(async (token: string, password: string) => {
    await authService.resetPassword(token, password);
  }, []);

  const changePassword = useCallback(
    async (currentPassword: string, newPassword: string) => {
      await authService.changePassword(currentPassword, newPassword);
      // После смены пароля все устройства разлогинены (раздел 2.8 контракта).
      clearAccessToken();
      setAccessTokenState(null);
      setUser(null);
    },
    [],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      accessToken,
      user,
      isAuthLoading,
      isAuthenticated: accessToken !== null,
      login,
      register,
      logout,
      loginWithGoogle,
      confirmEmail,
      resendConfirmation,
      forgotPassword,
      resetPassword,
      changePassword,
    }),
    [
      accessToken,
      user,
      isAuthLoading,
      login,
      register,
      logout,
      loginWithGoogle,
      confirmEmail,
      resendConfirmation,
      forgotPassword,
      resetPassword,
      changePassword,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

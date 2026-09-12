import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/hooks/useAuth';

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isAuthLoading } = useAuth();

  if (isAuthLoading) {
    // Silent refresh ещё выполняется — редирект запрещён (раздел 5.3 контракта).
    return <div className="app-loading" aria-label="Загрузка" />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

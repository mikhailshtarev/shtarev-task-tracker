import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/context/AuthContext';
import { useAuth } from './auth/hooks/useAuth';
import LoginForm from './auth/components/LoginForm';
import RegisterForm from './auth/components/RegisterForm';
import EmailConfirmPage from './auth/components/EmailConfirmPage';
import ForgotPasswordForm from './auth/components/ForgotPasswordForm';
import ResetPasswordForm from './auth/components/ResetPasswordForm';
import ChangePasswordForm from './auth/components/ChangePasswordForm';
import GoogleCallbackPage from './auth/components/GoogleCallbackPage';
import { ProtectedRoute } from './routes/ProtectedRoute';
import AuthPage from './components/AuthPage';
import DashboardPage from './pages/DashboardPage';
import NotFoundPage from './pages/NotFoundPage';
import ProfilePage from './pages/ProfilePage';
import SettingsPage from './pages/SettingsPage';

// Авторизованного пользователя на страницах входа/регистрации
// отправляем на дашборд (таблица роутинга, раздел 6 контракта).
function GuestOnlyPage({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isAuthLoading } = useAuth();

  if (!isAuthLoading && isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route
            path="/login"
            element={
              <GuestOnlyPage>
                <AuthPage>
                  <LoginForm />
                </AuthPage>
              </GuestOnlyPage>
            }
          />
          <Route
            path="/register"
            element={
              <GuestOnlyPage>
                <AuthPage>
                  <RegisterForm />
                </AuthPage>
              </GuestOnlyPage>
            }
          />
          <Route
            path="/confirm-email"
            element={
              <AuthPage>
                <EmailConfirmPage />
              </AuthPage>
            }
          />
          <Route
            path="/forgot-password"
            element={
              <AuthPage>
                <ForgotPasswordForm />
              </AuthPage>
            }
          />
          <Route
            path="/reset-password"
            element={
              <AuthPage>
                <ResetPasswordForm />
              </AuthPage>
            }
          />
          <Route path="/google-callback" element={<GoogleCallbackPage />} />
          <Route
            path="/change-password"
            element={
              <ProtectedRoute>
                <AuthPage>
                  <ChangePasswordForm />
                </AuthPage>
              </ProtectedRoute>
            }
          />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/profile"
            element={
              <ProtectedRoute>
                <ProfilePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <ProtectedRoute>
                <SettingsPage />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;

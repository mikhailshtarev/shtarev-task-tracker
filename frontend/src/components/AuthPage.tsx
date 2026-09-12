import type { ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import './AuthPage.css';

// Лейаут гостевых страниц авторизации: карточка с табами «Вход / Регистрация».
// Сами формы передаются через children (LoginForm, RegisterForm и т.д.).
function AuthPage({ children }: { children: ReactNode }) {
  const location = useLocation();

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-header">
          <h1 className="auth-title">Таск трекер</h1>
        </div>

        <div className="auth-tabs">
          <Link
            to="/login"
            className={`auth-tab ${location.pathname === '/login' ? 'auth-tab-active' : ''}`}
          >
            Вход
          </Link>
          <Link
            to="/register"
            className={`auth-tab ${location.pathname === '/register' ? 'auth-tab-active' : ''}`}
          >
            Регистрация
          </Link>
        </div>

        {children}
      </div>
    </div>
  );
}

export default AuthPage;

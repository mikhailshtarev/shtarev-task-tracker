import type { ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/hooks/useAuth';
import { BRANCH_STRINGS } from '../branches/strings';
import './AuthenticatedLayout.css';

interface AuthenticatedLayoutProps {
  title: string;
  children: ReactNode;
}

function AuthenticatedLayout({ title, children }: AuthenticatedLayoutProps) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app-shell">
      <header className="app-header">
        <NavLink to="/dashboard" className="app-brand">
          Таск трекер
        </NavLink>
        <div className="app-account">
          <nav className="app-account-navigation" aria-label="Навигация аккаунта">
            <NavLink to="/profile">Профиль</NavLink>
            <NavLink to="/settings">Настройки</NavLink>
          </nav>
          <span className="app-user-name">{user?.name || user?.email}</span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={handleLogout}>
            Выйти
          </button>
        </div>
      </header>
      <div className="app-body">
        <aside className="app-sidebar">
          <nav className="app-navigation" aria-label="Основная навигация">
            <NavLink to="/dashboard">Дашборд</NavLink>
            <NavLink to="/branches">{BRANCH_STRINGS.navigation}</NavLink>
          </nav>
        </aside>
        <main className="app-content">
          <h1 className="page-title">{title}</h1>
          {children}
        </main>
      </div>
    </div>
  );
}

export default AuthenticatedLayout;

import { useEffect, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent, type ReactNode, type KeyboardEvent as ReactKeyboardEvent } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/hooks/useAuth';
import { BRANCH_STRINGS } from '../branches/strings';
import { NavigationTree } from '../navigation/NavigationTree';
import './AuthenticatedLayout.css';

interface AuthenticatedLayoutProps {
  title: string;
  children: ReactNode;
  headerActions?: ReactNode;
}

const SIDEBAR_WIDTH_STORAGE_KEY = 'task-tracker.sidebar-width';
const DEFAULT_SIDEBAR_WIDTH = 260;
const MIN_SIDEBAR_WIDTH = 180;
const MAX_SIDEBAR_WIDTH = 480;

function readSidebarWidth(): number {
  try {
    const stored = Number.parseInt(window.localStorage.getItem(SIDEBAR_WIDTH_STORAGE_KEY) ?? '', 10);
    return Number.isFinite(stored)
      ? Math.min(MAX_SIDEBAR_WIDTH, Math.max(MIN_SIDEBAR_WIDTH, stored))
      : DEFAULT_SIDEBAR_WIDTH;
  } catch {
    return DEFAULT_SIDEBAR_WIDTH;
  }
}

function AuthenticatedLayout({ title, children, headerActions }: AuthenticatedLayoutProps) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sidebarWidth, setSidebarWidth] = useState(readSidebarWidth);
  const resizingRef = useRef(false);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  useEffect(() => {
    try {
      window.localStorage.setItem(SIDEBAR_WIDTH_STORAGE_KEY, String(sidebarWidth));
    } catch {
      // Storage can be unavailable in a restricted browser context.
    }
  }, [sidebarWidth]);

  useEffect(() => {
    const handlePointerMove = (event: PointerEvent) => {
      if (!resizingRef.current) {
        return;
      }
      setSidebarWidth(Math.min(MAX_SIDEBAR_WIDTH, Math.max(MIN_SIDEBAR_WIDTH, event.clientX)));
    };
    const stopResize = () => {
      resizingRef.current = false;
      document.body.classList.remove('is-resizing-sidebar');
    };
    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', stopResize);
    window.addEventListener('pointercancel', stopResize);
    return () => {
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', stopResize);
      window.removeEventListener('pointercancel', stopResize);
    };
  }, []);

  const startResize = (event: ReactPointerEvent<HTMLButtonElement>) => {
    event.preventDefault();
    resizingRef.current = true;
    document.body.classList.add('is-resizing-sidebar');
  };

  const handleResizeKeyDown = (event: ReactKeyboardEvent<HTMLButtonElement>) => {
    const step = event.shiftKey ? 40 : 10;
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault();
      setSidebarWidth((current) => Math.min(
        MAX_SIDEBAR_WIDTH,
        Math.max(MIN_SIDEBAR_WIDTH, current + (event.key === 'ArrowRight' ? step : -step)),
      ));
    } else if (event.key === 'Home') {
      event.preventDefault();
      setSidebarWidth(MIN_SIDEBAR_WIDTH);
    } else if (event.key === 'End') {
      event.preventDefault();
      setSidebarWidth(MAX_SIDEBAR_WIDTH);
    }
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
      <div className="app-body" style={{ '--sidebar-width': `${sidebarWidth}px` } as CSSProperties}>
        <aside className="app-sidebar">
          <nav className="app-navigation" aria-label="Основная навигация">
            <NavLink to="/dashboard">Дашборд</NavLink>
            <NavLink to="/branches">{BRANCH_STRINGS.navigation}</NavLink>
          </nav>
          <NavigationTree />
          <button
            type="button"
            className="app-sidebar-resizer"
            aria-label="Изменить ширину левой панели"
            aria-orientation="vertical"
            aria-valuemin={MIN_SIDEBAR_WIDTH}
            aria-valuemax={MAX_SIDEBAR_WIDTH}
            aria-valuenow={sidebarWidth}
            title="Перетащите, чтобы изменить ширину панели"
            onPointerDown={startResize}
            onKeyDown={handleResizeKeyDown}
          />
        </aside>
        <main className="app-content">
          <div className="app-content-header">
            <h1 className="page-title">{title}</h1>
            {headerActions && <div className="page-header-actions">{headerActions}</div>}
          </div>
          {children}
        </main>
      </div>
    </div>
  );
}

export default AuthenticatedLayout;

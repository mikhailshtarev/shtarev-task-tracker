import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/hooks/useAuth';

// Заглушка дашборда — защищённый маршрут за ProtectedRoute (этап 6 плана).
function DashboardPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-header">
          <h1 className="auth-title">Дашборд</h1>
        </div>
        {user && (
          <p className="auth-status">
            Вы вошли как <strong>{user.email}</strong>
          </p>
        )}
        <div className="auth-links">
          <Link to="/change-password">Сменить пароль</Link>
        </div>
        <button type="button" className="btn btn-secondary btn-block" onClick={handleLogout}>
          Выйти
        </button>
      </div>
    </div>
  );
}

export default DashboardPage;

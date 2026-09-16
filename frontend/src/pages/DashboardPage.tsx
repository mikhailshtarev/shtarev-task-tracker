import { Link } from 'react-router-dom';
import { useAuth } from '../auth/hooks/useAuth';
import AuthenticatedLayout from '../components/AuthenticatedLayout';

// Заглушка дашборда — защищённый маршрут за ProtectedRoute (этап 6 плана).
function DashboardPage() {
  const { user } = useAuth();

  return (
    <AuthenticatedLayout title="Дашборд">
      <div className="settings-card">
        {user && (
          <p className="auth-status">
            Вы вошли как <strong>{user.name || user.email}</strong>
          </p>
        )}
        <div className="auth-links">
          <Link to="/change-password">Сменить пароль</Link>
        </div>
      </div>
    </AuthenticatedLayout>
  );
}

export default DashboardPage;

import { Link } from 'react-router-dom';

function NotFoundPage() {
  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-header">
          <h1 className="auth-title">404</h1>
        </div>
        <p className="auth-status">Страница не найдена</p>
        <Link to="/" className="btn btn-primary btn-block btn-lg">
          На главную
        </Link>
      </div>
    </div>
  );
}

export default NotFoundPage;

import { useState, type FormEvent } from 'react';
import './AuthPage.css';

type AuthMode = 'login' | 'register';

function AuthPage() {
  const [mode, setMode] = useState<AuthMode>('login');

  // Login state
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');

  // Register state
  const [regEmail, setRegEmail] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [regPasswordConfirm, setRegPasswordConfirm] = useState('');

  const handleLogin = (e: FormEvent) => {
    e.preventDefault();
    // TODO: implement login
    console.log('Login:', { email: loginEmail, password: loginPassword });
  };

  const handleRegister = (e: FormEvent) => {
    e.preventDefault();
    // TODO: implement register
    console.log('Register:', { email: regEmail, password: regPassword });
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-header">
          <h1 className="auth-title">Таск трекер</h1>
        </div>

        <div className="auth-tabs">
          <button
            type="button"
            className={`auth-tab ${mode === 'login' ? 'auth-tab-active' : ''}`}
            onClick={() => setMode('login')}
          >
            Вход
          </button>
          <button
            type="button"
            className={`auth-tab ${mode === 'register' ? 'auth-tab-active' : ''}`}
            onClick={() => setMode('register')}
          >
            Регистрация
          </button>
        </div>

        {mode === 'login' ? (
          <form className="auth-form" onSubmit={handleLogin}>
            <div className="input-group">
              <label className="input-label" htmlFor="login-email">
                Email
              </label>
              <input
                id="login-email"
                type="email"
                className="input"
                placeholder="example@mail.ru"
                value={loginEmail}
                onChange={(e) => setLoginEmail(e.target.value)}
                required
              />
            </div>
            <div className="input-group">
              <label className="input-label" htmlFor="login-password">
                Пароль
              </label>
              <input
                id="login-password"
                type="password"
                className="input"
                placeholder="••••••••"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                required
              />
            </div>
            <button type="submit" className="btn btn-primary btn-block btn-lg">
              Войти
            </button>
          </form>
        ) : (
          <form className="auth-form" onSubmit={handleRegister}>
            <div className="input-group">
              <label className="input-label" htmlFor="reg-email">
                Email
              </label>
              <input
                id="reg-email"
                type="email"
                className="input"
                placeholder="example@mail.ru"
                value={regEmail}
                onChange={(e) => setRegEmail(e.target.value)}
                required
              />
            </div>
            <div className="input-group">
              <label className="input-label" htmlFor="reg-password">
                Пароль
              </label>
              <input
                id="reg-password"
                type="password"
                className="input"
                placeholder="••••••••"
                value={regPassword}
                onChange={(e) => setRegPassword(e.target.value)}
                required
              />
            </div>
            <div className="input-group">
              <label className="input-label" htmlFor="reg-password-confirm">
                Подтверждение пароля
              </label>
              <input
                id="reg-password-confirm"
                type="password"
                className="input"
                placeholder="••••••••"
                value={regPasswordConfirm}
                onChange={(e) => setRegPasswordConfirm(e.target.value)}
                required
              />
            </div>
            <button type="submit" className="btn btn-primary btn-block btn-lg">
              Зарегистрироваться
            </button>
          </form>
        )}
      </div>
    </div>
  );
}

export default AuthPage;

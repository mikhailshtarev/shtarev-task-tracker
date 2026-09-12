import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../hooks/useAuth';
import { GOOGLE_STATE_KEY } from './GoogleLoginButton';

function GoogleCallbackPage() {
  const [searchParams] = useSearchParams();
  const { loginWithGoogle } = useAuth();
  const navigate = useNavigate();

  const [error, setError] = useState<string | null>(null);
  const startedRef = useRef(false);

  useEffect(() => {
    // StrictMode вызывает эффект дважды — код Google одноразовый.
    if (startedRef.current) {
      return;
    }
    startedRef.current = true;

    const state = searchParams.get('state');
    const code = searchParams.get('code');
    const savedState = sessionStorage.getItem(GOOGLE_STATE_KEY);
    sessionStorage.removeItem(GOOGLE_STATE_KEY);

    // Проверяем state ДО отправки code в API (раздел 2.9 контракта).
    if (!state || !savedState || state !== savedState) {
      setError('Ошибка безопасности: недействительный параметр state. Повторите вход.');
      return;
    }
    if (!code) {
      setError('Не получен код авторизации Google. Повторите вход.');
      return;
    }

    loginWithGoogle(code)
      .then(() => navigate('/dashboard', { replace: true }))
      .catch((err: unknown) => {
        if (err instanceof ApiError && err.code === 'EMAIL_CONFLICT') {
          setError('Email уже зарегистрирован. Войдите через email');
        } else if (err instanceof ApiError && err.code === 'INVALID_GOOGLE_CODE') {
          setError(err.message);
        } else {
          setError('Не удалось выполнить вход через Google. Повторите попытку.');
        }
      });
  }, [searchParams, loginWithGoogle, navigate]);

  if (error) {
    return (
      <div className="auth-form">
        <div className="error-message" role="alert">
          {error}
        </div>
        <Link to="/login" className="btn btn-primary btn-block btn-lg">
          Перейти ко входу
        </Link>
      </div>
    );
  }

  return <p className="auth-status">Выполняем вход через Google…</p>;
}

export default GoogleCallbackPage;

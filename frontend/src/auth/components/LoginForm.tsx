import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../hooks/useAuth';
import { validateEmail } from '../utils/validators';
import { ErrorDisplay } from './ErrorDisplay';
import { GoogleLoginButton } from './GoogleLoginButton';

const LOCK_DURATION_MS = 15 * 60 * 1000;
const LOCK_UNTIL_KEY = 'login_lock_until';

function readLockUntil(): number | null {
  const raw = sessionStorage.getItem(LOCK_UNTIL_KEY);
  if (!raw) {
    return null;
  }
  const until = Number(raw);
  if (Number.isNaN(until) || until <= Date.now()) {
    sessionStorage.removeItem(LOCK_UNTIL_KEY);
    return null;
  }
  return until;
}

function LoginForm() {
  const { login, resendConfirmation } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({});
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [emailNotConfirmed, setEmailNotConfirmed] = useState(false);
  const [resendSent, setResendSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [lockUntil, setLockUntil] = useState<number | null>(readLockUntil);

  const isLocked = lockUntil !== null && Date.now() < lockUntil;

  // Разблокируем кнопку, когда истекает блокировка после 429.
  useEffect(() => {
    if (lockUntil === null) {
      return;
    }
    const timer = window.setInterval(() => {
      if (Date.now() >= lockUntil) {
        sessionStorage.removeItem(LOCK_UNTIL_KEY);
        setLockUntil(null);
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [lockUntil]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError(null);
    setEmailNotConfirmed(false);
    setResendSent(false);

    // Для входа проверяем только формат email и непустой пароль —
    // полная политика пароля применяется при регистрации.
    const errors = {
      email: validateEmail(email) ?? undefined,
      password: password ? undefined : 'Введите пароль',
    };
    setFieldErrors(errors);
    if (errors.email || errors.password) {
      return;
    }

    setSubmitting(true);
    try {
      await login(email, password);
      navigate('/dashboard', { replace: true });
    } catch (error) {
      if (error instanceof ApiError) {
        setApiError(error);
        if (error.code === 'EMAIL_NOT_CONFIRMED') {
          setEmailNotConfirmed(true);
        }
        if (error.code === 'RATE_LIMITED') {
          const until = Date.now() + LOCK_DURATION_MS;
          sessionStorage.setItem(LOCK_UNTIL_KEY, String(until));
          setLockUntil(until);
        }
      } else {
        setApiError(new ApiError(0, { message: 'Не удалось выполнить вход' }));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleResend = async () => {
    setApiError(null);
    try {
      await resendConfirmation(email);
      setResendSent(true);
    } catch (error) {
      if (error instanceof ApiError) {
        setApiError(error);
      }
    }
  };

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <div className="input-group">
        <label className="input-label" htmlFor="login-email">
          Email
        </label>
        <input
          id="login-email"
          type="email"
          className={`input ${fieldErrors.email ? 'error' : ''}`}
          placeholder="example@mail.ru"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        {fieldErrors.email && <div className="input-error">{fieldErrors.email}</div>}
      </div>
      <div className="input-group">
        <label className="input-label" htmlFor="login-password">
          Пароль
        </label>
        <input
          id="login-password"
          type="password"
          className={`input ${fieldErrors.password ? 'error' : ''}`}
          placeholder="••••••••"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {fieldErrors.password && <div className="input-error">{fieldErrors.password}</div>}
      </div>

      <ErrorDisplay error={apiError} />

      {emailNotConfirmed && !resendSent && (
        <button type="button" className="btn btn-secondary btn-block" onClick={handleResend}>
          Отправить письмо повторно
        </button>
      )}
      {resendSent && (
        <p className="auth-success" role="status">
          Если email зарегистрирован и не подтверждён, вы получите письмо
        </p>
      )}

      <button
        type="submit"
        className="btn btn-primary btn-block btn-lg"
        disabled={submitting || isLocked}
      >
        {isLocked ? 'Вход заблокирован' : 'Войти'}
      </button>

      <div className="auth-links">
        <Link to="/forgot-password">Забыли пароль?</Link>
      </div>

      <GoogleLoginButton />
    </form>
  );
}

export default LoginForm;

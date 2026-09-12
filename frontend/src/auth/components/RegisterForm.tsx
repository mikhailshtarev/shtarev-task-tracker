import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../hooks/useAuth';
import { validateEmail, validatePassword } from '../utils/validators';
import { ErrorDisplay } from './ErrorDisplay';
import { GoogleLoginButton } from './GoogleLoginButton';

function RegisterForm() {
  const { register } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{
    email?: string;
    password?: string;
    passwordConfirm?: string;
  }>({});
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [registered, setRegistered] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError(null);

    const errors = {
      email: validateEmail(email) ?? undefined,
      password: validatePassword(password) ?? undefined,
      passwordConfirm:
        passwordConfirm === password ? undefined : 'Пароли не совпадают',
    };
    setFieldErrors(errors);
    if (errors.email || errors.password || errors.passwordConfirm) {
      return;
    }

    setSubmitting(true);
    try {
      await register(email, password);
      setRegistered(true);
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.code === 'EMAIL_ALREADY_EXISTS') {
          setFieldErrors({ email: 'Пользователь с таким email уже существует' });
        } else {
          setApiError(error);
        }
      } else {
        setApiError(new ApiError(0, { message: 'Не удалось зарегистрироваться' }));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (registered) {
    return (
      <div className="auth-form">
        <p className="auth-success" role="status">
          Проверьте email для подтверждения
        </p>
        <Link to="/login" className="btn btn-primary btn-block btn-lg">
          Перейти ко входу
        </Link>
      </div>
    );
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <div className="input-group">
        <label className="input-label" htmlFor="reg-email">
          Email
        </label>
        <input
          id="reg-email"
          type="email"
          className={`input ${fieldErrors.email ? 'error' : ''}`}
          placeholder="example@mail.ru"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        {fieldErrors.email && <div className="input-error">{fieldErrors.email}</div>}
      </div>
      <div className="input-group">
        <label className="input-label" htmlFor="reg-password">
          Пароль
        </label>
        <input
          id="reg-password"
          type="password"
          className={`input ${fieldErrors.password ? 'error' : ''}`}
          placeholder="••••••••"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {fieldErrors.password && <div className="input-error">{fieldErrors.password}</div>}
      </div>
      <div className="input-group">
        <label className="input-label" htmlFor="reg-password-confirm">
          Подтверждение пароля
        </label>
        <input
          id="reg-password-confirm"
          type="password"
          className={`input ${fieldErrors.passwordConfirm ? 'error' : ''}`}
          placeholder="••••••••"
          value={passwordConfirm}
          onChange={(e) => setPasswordConfirm(e.target.value)}
        />
        {fieldErrors.passwordConfirm && (
          <div className="input-error">{fieldErrors.passwordConfirm}</div>
        )}
      </div>

      <ErrorDisplay error={apiError} />

      <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={submitting}>
        Зарегистрироваться
      </button>

      <GoogleLoginButton />
    </form>
  );
}

export default RegisterForm;

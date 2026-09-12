import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../hooks/useAuth';
import { validatePassword } from '../utils/validators';
import { ErrorDisplay } from './ErrorDisplay';

function ResetPasswordForm() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const { resetPassword } = useAuth();
  const navigate = useNavigate();

  const [password, setPassword] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError(null);

    const error = validatePassword(password);
    setFieldError(error);
    if (error) {
      return;
    }

    if (!token) {
      setApiError(
        new ApiError(400, { message: 'Неверный или истёкший токен' }),
      );
      return;
    }

    setSubmitting(true);
    try {
      await resetPassword(token, password);
      navigate('/login', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'PASSWORD_TOO_RECENT') {
          setFieldError(err.message);
        } else {
          setApiError(err);
        }
      } else {
        setApiError(new ApiError(0, { message: 'Не удалось изменить пароль' }));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <div className="input-group">
        <label className="input-label" htmlFor="reset-password">
          Новый пароль
        </label>
        <input
          id="reset-password"
          type="password"
          className={`input ${fieldError ? 'error' : ''}`}
          placeholder="••••••••"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {fieldError && <div className="input-error">{fieldError}</div>}
      </div>

      <ErrorDisplay error={apiError} />

      <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={submitting}>
        Сохранить пароль
      </button>
    </form>
  );
}

export default ResetPasswordForm;

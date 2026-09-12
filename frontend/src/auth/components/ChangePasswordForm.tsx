import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../hooks/useAuth';
import { validatePassword } from '../utils/validators';
import { ErrorDisplay } from './ErrorDisplay';

function ChangePasswordForm() {
  const { changePassword } = useAuth();
  const navigate = useNavigate();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{
    currentPassword?: string;
    newPassword?: string;
  }>({});
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError(null);

    const errors = {
      currentPassword: currentPassword ? undefined : 'Введите текущий пароль',
      newPassword: validatePassword(newPassword) ?? undefined,
    };
    setFieldErrors(errors);
    if (errors.currentPassword || errors.newPassword) {
      return;
    }

    setSubmitting(true);
    try {
      await changePassword(currentPassword, newPassword);
      // После смены пароля все устройства разлогинены — редирект на /login.
      navigate('/login', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'INVALID_CURRENT_PASSWORD') {
          setFieldErrors({ currentPassword: err.message });
        } else if (err.code === 'PASSWORD_TOO_RECENT') {
          setFieldErrors({ newPassword: err.message });
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
        <label className="input-label" htmlFor="current-password">
          Текущий пароль
        </label>
        <input
          id="current-password"
          type="password"
          className={`input ${fieldErrors.currentPassword ? 'error' : ''}`}
          placeholder="••••••••"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
        />
        {fieldErrors.currentPassword && (
          <div className="input-error">{fieldErrors.currentPassword}</div>
        )}
      </div>
      <div className="input-group">
        <label className="input-label" htmlFor="new-password">
          Новый пароль
        </label>
        <input
          id="new-password"
          type="password"
          className={`input ${fieldErrors.newPassword ? 'error' : ''}`}
          placeholder="••••••••"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />
        {fieldErrors.newPassword && (
          <div className="input-error">{fieldErrors.newPassword}</div>
        )}
      </div>

      <ErrorDisplay error={apiError} />

      <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={submitting}>
        Сменить пароль
      </button>
    </form>
  );
}

export default ChangePasswordForm;

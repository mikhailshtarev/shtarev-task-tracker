import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../hooks/useAuth';
import { validateEmail } from '../utils/validators';
import { ErrorDisplay } from './ErrorDisplay';

function ForgotPasswordForm() {
  const { forgotPassword } = useAuth();

  const [email, setEmail] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [sent, setSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError(null);

    const error = validateEmail(email);
    setFieldError(error);
    if (error) {
      return;
    }

    setSubmitting(true);
    try {
      await forgotPassword(email);
      setSent(true);
    } catch (err) {
      if (err instanceof ApiError) {
        setApiError(err);
      } else {
        setApiError(new ApiError(0, { message: 'Не удалось отправить запрос' }));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (sent) {
    // Нейтральное сообщение — не раскрываем факт регистрации (раздел 2.10 контракта).
    return (
      <div className="auth-form">
        <p className="auth-success" role="status">
          Если email зарегистрирован, вы получите ссылку для сброса пароля
        </p>
        <Link to="/login" className="btn btn-primary btn-block btn-lg">
          Вернуться ко входу
        </Link>
      </div>
    );
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <div className="input-group">
        <label className="input-label" htmlFor="forgot-email">
          Email
        </label>
        <input
          id="forgot-email"
          type="email"
          className={`input ${fieldError ? 'error' : ''}`}
          placeholder="example@mail.ru"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        {fieldError && <div className="input-error">{fieldError}</div>}
      </div>

      <ErrorDisplay error={apiError} />

      <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={submitting}>
        Отправить ссылку для сброса
      </button>

      <div className="auth-links">
        <Link to="/login">Вернуться ко входу</Link>
      </div>
    </form>
  );
}

export default ForgotPasswordForm;

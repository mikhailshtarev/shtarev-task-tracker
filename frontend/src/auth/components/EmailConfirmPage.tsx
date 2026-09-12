import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../hooks/useAuth';
import { validateEmail } from '../utils/validators';
import { ErrorDisplay } from './ErrorDisplay';

type ConfirmState = 'loading' | 'success' | 'error';

function EmailConfirmPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const { confirmEmail, resendConfirmation } = useAuth();
  const navigate = useNavigate();

  const [state, setState] = useState<ConfirmState>('loading');
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [email, setEmail] = useState('');
  const [resendSent, setResendSent] = useState(false);
  const [resending, setResending] = useState(false);

  useEffect(() => {
    if (!token) {
      setState('error');
      setApiError(
        new ApiError(400, {
          code: 'INVALID_TOKEN',
          message: 'Неверный или истёкший токен подтверждения',
        }),
      );
      return;
    }
    setState('loading');
    confirmEmail(token)
      .then(() => {
        setState('success');
        navigate('/login', { replace: true });
      })
      .catch((error: unknown) => {
        setState('error');
        if (error instanceof ApiError) {
          setApiError(error);
        } else {
          setApiError(
            new ApiError(0, { message: 'Не удалось подтвердить email' }),
          );
        }
      });
  }, [token, confirmEmail, navigate]);

  const handleResend = async () => {
    setApiError(null);
    const emailError = validateEmail(email);
    if (emailError) {
      setApiError(new ApiError(400, { message: emailError }));
      return;
    }
    setResending(true);
    try {
      await resendConfirmation(email);
      setResendSent(true);
    } catch (error) {
      if (error instanceof ApiError) {
        setApiError(error);
      }
    } finally {
      setResending(false);
    }
  };

  if (state === 'loading') {
    return <p className="auth-status">Подтверждаем email…</p>;
  }

  if (state === 'success') {
    return null;
  }

  return (
    <div className="auth-form">
      <ErrorDisplay error={apiError} />
      {resendSent ? (
        <p className="auth-success" role="status">
          Если email зарегистрирован и не подтверждён, вы получите письмо
        </p>
      ) : (
        <>
          <p className="auth-status">Введите email, на который отправить письмо повторно:</p>
          <div className="input-group">
            <label className="input-label" htmlFor="confirm-email">
              Email
            </label>
            <input
              id="confirm-email"
              type="email"
              className="input"
              placeholder="example@mail.ru"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          <button
            type="button"
            className="btn btn-secondary btn-block"
            onClick={handleResend}
            disabled={resending}
          >
            Отправить письмо повторно
          </button>
        </>
      )}
    </div>
  );
}

export default EmailConfirmPage;

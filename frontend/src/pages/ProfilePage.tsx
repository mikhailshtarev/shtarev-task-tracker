import { useLayoutEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { ErrorDisplay } from '../auth/components/ErrorDisplay';
import { useAuth } from '../auth/hooks/useAuth';
import { validateProfileName } from '../auth/utils/validators';
import AuthenticatedLayout from '../components/AuthenticatedLayout';

function ProfilePage() {
  const { user, updateProfile } = useAuth();
  const [name, setName] = useState(user?.name ?? '');
  const [nameError, setNameError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [saved, setSaved] = useState(false);

  useLayoutEffect(() => {
    setName(user?.name ?? '');
  }, [user?.name]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setApiError(null);
    setSaved(false);

    const validationError = validateProfileName(name);
    setNameError(validationError);
    if (validationError) {
      return;
    }

    setSubmitting(true);
    try {
      const normalizedName = name.trim() || null;
      await updateProfile(normalizedName);
      setSaved(true);
    } catch (error) {
      if (error instanceof ApiError) {
        const fieldError = error.details?.find((detail) => detail.field === 'name')?.message;
        if (fieldError) {
          setNameError(fieldError);
        } else {
          setApiError(error);
        }
      } else {
        setApiError(new ApiError(0, { message: 'Не удалось сохранить профиль' }));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthenticatedLayout title="Профиль">
      <form className="settings-card" onSubmit={handleSubmit} noValidate>
        <div className="input-group">
          <span className="input-label">Email</span>
          <div className="read-only-value">{user?.email}</div>
        </div>

        <div className="input-group">
          <label className="input-label" htmlFor="profile-name">
            Имя
          </label>
          <input
            id="profile-name"
            className={`input ${nameError ? 'error' : ''}`}
            value={name}
            maxLength={101}
            onChange={(event) => {
              setName(event.target.value);
              setNameError(null);
              setSaved(false);
            }}
            disabled={submitting}
          />
          <p className="input-hint">До 100 символов. Поле можно оставить пустым.</p>
          {nameError && <div className="input-error">{nameError}</div>}
        </div>

        <ErrorDisplay error={apiError} />

        <div className="settings-actions">
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Сохраняем…' : 'Сохранить'}
          </button>
          {saved && <span className="settings-success" role="status">Профиль сохранён</span>}
        </div>

        <div className="auth-links">
          <Link to="/change-password">Сменить пароль</Link>
        </div>
      </form>
    </AuthenticatedLayout>
  );
}

export default ProfilePage;

import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { ErrorDisplay } from '../auth/components/ErrorDisplay';
import {
  authService,
  type EstimationUnit,
  type UserSettings,
} from '../auth/services/authService';
import {
  validateBudgetHourCost,
  validateEstimationUnit,
  validatePomodoroMinutes,
} from '../auth/utils/validators';
import AuthenticatedLayout from '../components/AuthenticatedLayout';

type SettingsField = keyof UserSettings;
type FieldErrors = Partial<Record<SettingsField, string>>;

interface SettingsFormState {
  estimationUnit: EstimationUnit;
  pomodoroMinutes: string;
  gameModeEnabled: boolean;
  budgetHourCost: string;
}

function toFormState(settings: UserSettings): SettingsFormState {
  return {
    estimationUnit: settings.estimationUnit,
    pomodoroMinutes: String(settings.pomodoroMinutes),
    gameModeEnabled: settings.gameModeEnabled,
    budgetHourCost: String(settings.budgetHourCost),
  };
}

function SettingsPage() {
  const [form, setForm] = useState<SettingsFormState | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [loadError, setLoadError] = useState<ApiError | null>(null);
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [saved, setSaved] = useState(false);

  const loadSettings = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const settings = await authService.getSettings();
      setForm(toFormState(settings));
    } catch (error) {
      setForm(null);
      setLoadError(
        error instanceof ApiError
          ? error
          : new ApiError(0, { message: 'Не удалось загрузить настройки' }),
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSettings();
  }, [loadSettings]);

  const updateField = <K extends keyof SettingsFormState>(
    field: K,
    value: SettingsFormState[K],
  ) => {
    setForm((current) => current ? { ...current, [field]: value } : current);
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setSaved(false);
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!form) {
      return;
    }

    setApiError(null);
    setSaved(false);

    const settings: UserSettings = {
      estimationUnit: form.estimationUnit,
      pomodoroMinutes: Number(form.pomodoroMinutes),
      gameModeEnabled: form.gameModeEnabled,
      budgetHourCost: Number(form.budgetHourCost),
    };
    const errors: FieldErrors = {
      estimationUnit: validateEstimationUnit(settings.estimationUnit) ?? undefined,
      pomodoroMinutes: validatePomodoroMinutes(settings.pomodoroMinutes) ?? undefined,
      budgetHourCost: validateBudgetHourCost(settings.budgetHourCost) ?? undefined,
    };
    setFieldErrors(errors);
    if (Object.values(errors).some(Boolean)) {
      return;
    }

    setSubmitting(true);
    try {
      const updated = await authService.updateSettings(settings);
      setForm(toFormState(updated));
      setSaved(true);
    } catch (error) {
      if (error instanceof ApiError) {
        const serverErrors: FieldErrors = {};
        error.details?.forEach((detail) => {
          if (detail.field && detail.field in settings) {
            serverErrors[detail.field as SettingsField] = detail.message;
          }
        });
        if (Object.keys(serverErrors).length > 0) {
          setFieldErrors(serverErrors);
        } else {
          setApiError(error);
        }
      } else {
        setApiError(new ApiError(0, { message: 'Не удалось сохранить настройки' }));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthenticatedLayout title="Настройки">
      {loading && <p className="settings-loading" role="status">Загружаем настройки…</p>}

      {!loading && loadError && (
        <div className="settings-card">
          <ErrorDisplay error={loadError} />
          <button type="button" className="btn btn-secondary" onClick={loadSettings}>
            Попробовать снова
          </button>
        </div>
      )}

      {!loading && form && (
        <form className="settings-card" onSubmit={handleSubmit} noValidate>
          <section className="settings-section" aria-labelledby="estimation-title">
            <h2 id="estimation-title" className="settings-section-title">Оценка</h2>
            <div className="choice-group" role="radiogroup" aria-label="Единица оценки">
              <label className="choice-label">
                <input
                  type="radio"
                  name="estimationUnit"
                  value="hours"
                  checked={form.estimationUnit === 'hours'}
                  onChange={() => updateField('estimationUnit', 'hours')}
                  disabled={submitting}
                />
                Часы
              </label>
              <label className="choice-label">
                <input
                  type="radio"
                  name="estimationUnit"
                  value="pomodoros"
                  checked={form.estimationUnit === 'pomodoros'}
                  onChange={() => updateField('estimationUnit', 'pomodoros')}
                  disabled={submitting}
                />
                Помидоры
              </label>
            </div>
            {fieldErrors.estimationUnit && (
              <div className="input-error">{fieldErrors.estimationUnit}</div>
            )}

            {form.estimationUnit === 'pomodoros' && (
              <div className="input-group">
                <label className="input-label" htmlFor="pomodoro-minutes">
                  Длительность помидора (мин)
                </label>
                <input
                  id="pomodoro-minutes"
                  type="number"
                  min="5"
                  max="120"
                  step="5"
                  className={`input ${fieldErrors.pomodoroMinutes ? 'error' : ''}`}
                  value={form.pomodoroMinutes}
                  onChange={(event) => updateField('pomodoroMinutes', event.target.value)}
                  disabled={submitting}
                />
                <p className="input-hint">От 5 до 120 минут, с шагом 5 минут.</p>
                {fieldErrors.pomodoroMinutes && (
                  <div className="input-error">{fieldErrors.pomodoroMinutes}</div>
                )}
              </div>
            )}
            {form.estimationUnit === 'hours' && fieldErrors.pomodoroMinutes && (
              <div className="input-error">
                Переключитесь на «Помидоры» и исправьте длительность: {fieldErrors.pomodoroMinutes}
              </div>
            )}
          </section>

          <section className="settings-section" aria-labelledby="game-title">
            <h2 id="game-title" className="settings-section-title">Игровой режим</h2>
            <div className="input-group">
              <label className="choice-label">
                <input
                  type="checkbox"
                  checked={form.gameModeEnabled}
                  onChange={(event) => updateField('gameModeEnabled', event.target.checked)}
                  disabled={submitting}
                />
                Игровой режим включён
              </label>
            </div>

            <div className="input-group">
              <label className="input-label" htmlFor="budget-hour-cost">
                Стоимость часа для бюджета (z)
              </label>
              <input
                id="budget-hour-cost"
                type="number"
                min="1"
                max="1000"
                step="1"
                className={`input ${fieldErrors.budgetHourCost ? 'error' : ''}`}
                value={form.budgetHourCost}
                onChange={(event) => updateField('budgetHourCost', event.target.value)}
                disabled={submitting}
              />
              <p className="input-hint">Целое число от 1 до 1000.</p>
              {fieldErrors.budgetHourCost && (
                <div className="input-error">{fieldErrors.budgetHourCost}</div>
              )}
            </div>
          </section>

          <ErrorDisplay error={apiError} />

          <div className="settings-actions">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Сохраняем…' : 'Сохранить'}
            </button>
            {saved && <span className="settings-success" role="status">Настройки сохранены</span>}
          </div>
        </form>
      )}
    </AuthenticatedLayout>
  );
}

export default SettingsPage;

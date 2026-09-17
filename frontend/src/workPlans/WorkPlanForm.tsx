import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { ErrorDisplay } from '../auth/components/ErrorDisplay';
import { toWorkPlanApiError } from './workPlanErrors';
import { normalizeWorkPlanText, validateWorkPlanName } from './workPlanValidation';
import { WORK_PLAN_STRINGS } from './strings';

interface WorkPlanFormProps {
  initialName?: string;
  submitLabel: string;
  onSubmit: (name: string) => Promise<void>;
  onCancel?: () => void;
}

export function WorkPlanForm({ initialName = '', submitLabel, onSubmit, onCancel }: WorkPlanFormProps) {
  const [name, setName] = useState(initialName);
  const [nameError, setNameError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setName(initialName);
  }, [initialName]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const validationError = validateWorkPlanName(name);
    setNameError(validationError);
    setApiError(null);
    if (validationError) {
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit(normalizeWorkPlanText(name));
      if (!initialName) {
        setName('');
      }
    } catch (error) {
      const apiFailure = toWorkPlanApiError(error, WORK_PLAN_STRINGS.saveError);
      const fieldError = apiFailure.details?.find((detail) => detail.field === 'name')?.message;
      if (fieldError) {
        setNameError(fieldError);
      } else {
        setApiError(apiFailure);
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="work-plan-form" onSubmit={handleSubmit} noValidate>
      <div className="input-group">
        <label className="input-label" htmlFor="work-plan-name">
          {WORK_PLAN_STRINGS.nameLabel}
        </label>
        <input
          id="work-plan-name"
          className={`input ${nameError ? 'error' : ''}`}
          value={name}
          placeholder={WORK_PLAN_STRINGS.namePlaceholder}
          onChange={(event) => {
            setName(event.target.value);
            setNameError(null);
          }}
          disabled={submitting}
        />
        {nameError && <div className="input-error">{nameError}</div>}
      </div>
      <ErrorDisplay error={apiError} />
      <div className="branch-form-actions">
        <button type="submit" className="btn btn-primary" disabled={submitting}>
          {submitting ? WORK_PLAN_STRINGS.loadingMore : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={submitting}>
            {WORK_PLAN_STRINGS.cancelAction}
          </button>
        )}
      </div>
    </form>
  );
}

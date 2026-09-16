import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { ErrorDisplay } from '../auth/components/ErrorDisplay';
import { toBranchApiError } from './branchErrors';
import { normalizeBranchText, validateBranchName } from './branchValidation';
import { BRANCH_STRINGS } from './strings';

interface BranchFormProps {
  initialName?: string;
  submitLabel: string;
  onSubmit: (name: string) => Promise<void>;
  onCancel?: () => void;
}

export function BranchForm({ initialName = '', submitLabel, onSubmit, onCancel }: BranchFormProps) {
  const [name, setName] = useState(initialName);
  const [nameError, setNameError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setName(initialName);
  }, [initialName]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const validationError = validateBranchName(name);
    setNameError(validationError);
    setApiError(null);
    if (validationError) {
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit(normalizeBranchText(name));
      if (!initialName) {
        setName('');
      }
    } catch (error) {
      const apiFailure = toBranchApiError(error, BRANCH_STRINGS.saveError);
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
    <form className="branch-form" onSubmit={handleSubmit} noValidate>
      <div className="input-group">
        <label className="input-label" htmlFor="branch-name">
          {BRANCH_STRINGS.nameLabel}
        </label>
        <input
          id="branch-name"
          className={`input ${nameError ? 'error' : ''}`}
          value={name}
          placeholder={BRANCH_STRINGS.namePlaceholder}
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
          {submitting ? BRANCH_STRINGS.loadingMore : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={submitting}>
            {BRANCH_STRINGS.cancelAction}
          </button>
        )}
      </div>
    </form>
  );
}

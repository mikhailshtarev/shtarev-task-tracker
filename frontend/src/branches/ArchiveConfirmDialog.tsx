import { useEffect, useRef } from 'react';
import { BRANCH_STRINGS } from './strings';

interface ArchiveConfirmDialogProps {
  open: boolean;
  submitting: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ArchiveConfirmDialog({
  open,
  submitting,
  onConfirm,
  onCancel,
}: ArchiveConfirmDialogProps) {
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) {
      cancelButtonRef.current?.focus();
    }
  }, [open]);

  if (!open) {
    return null;
  }

  return (
    <div className="dialog-backdrop" onMouseDown={() => !submitting && onCancel()}>
      <div
        className="dialog-card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="archive-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
        onKeyDown={(event) => {
          if (event.key === 'Escape' && !submitting) {
            onCancel();
          }
        }}
      >
        <h2 id="archive-dialog-title" className="dialog-title">
          {BRANCH_STRINGS.archiveDialogTitle}
        </h2>
        <p className="dialog-description">{BRANCH_STRINGS.archiveDialogDescription}</p>
        <div className="dialog-actions">
          <button
            ref={cancelButtonRef}
            type="button"
            className="btn btn-secondary"
            onClick={onCancel}
            disabled={submitting}
          >
            {BRANCH_STRINGS.cancelAction}
          </button>
          <button
            type="button"
            className="btn btn-danger"
            onClick={onConfirm}
            disabled={submitting}
          >
            {submitting ? BRANCH_STRINGS.loadingMore : BRANCH_STRINGS.archiveConfirmAction}
          </button>
        </div>
      </div>
    </div>
  );
}

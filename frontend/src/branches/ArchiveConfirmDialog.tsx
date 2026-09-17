import { useEffect, useRef } from 'react';
interface ArchiveConfirmDialogProps {
  open: boolean;
  submitting: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel: string;
  submittingLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ArchiveConfirmDialog({
  open,
  submitting,
  title,
  description,
  confirmLabel,
  cancelLabel,
  submittingLabel,
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
          {title}
        </h2>
        <p className="dialog-description">{description}</p>
        <div className="dialog-actions">
          <button
            ref={cancelButtonRef}
            type="button"
            className="btn btn-secondary"
            onClick={onCancel}
            disabled={submitting}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            className="btn btn-danger"
            onClick={onConfirm}
            disabled={submitting}
          >
            {submitting ? submittingLabel : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

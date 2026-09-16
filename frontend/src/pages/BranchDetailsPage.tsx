import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { ErrorDisplay } from '../auth/components/ErrorDisplay';
import { ArchiveConfirmDialog } from '../branches/ArchiveConfirmDialog';
import { BranchForm } from '../branches/BranchForm';
import { toBranchApiError } from '../branches/branchErrors';
import { branchService, type Branch } from '../branches/branchService';
import { isBranchId } from '../branches/branchValidation';
import { BRANCH_STRINGS } from '../branches/strings';
import AuthenticatedLayout from '../components/AuthenticatedLayout';

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

function BranchDetailsPage() {
  const { branchId = '' } = useParams();
  const navigate = useNavigate();
  const [branch, setBranch] = useState<Branch | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [showRenameForm, setShowRenameForm] = useState(false);
  const [showArchiveDialog, setShowArchiveDialog] = useState(false);
  const [archiving, setArchiving] = useState(false);
  const [reloadVersion, setReloadVersion] = useState(0);
  const archiveButtonRef = useRef<HTMLButtonElement>(null);
  const archivingRef = useRef(false);

  useEffect(() => {
    if (!isBranchId(branchId)) {
      setNotFound(true);
      setLoading(false);
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    setBranch(null);
    setNotFound(false);
    setApiError(null);
    void branchService
      .getBranch(branchId, controller.signal)
      .then((result) => setBranch(result))
      .catch((error: unknown) => {
        if (isAbortError(error)) {
          return;
        }
        if (error instanceof ApiError && error.status === 404) {
          setNotFound(true);
        } else {
          setApiError(toBranchApiError(error, BRANCH_STRINGS.loadError));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, [branchId, reloadVersion]);

  const handleRename = async (name: string) => {
    const updated = await branchService.updateBranch(branchId, name);
    setBranch(updated);
    setShowRenameForm(false);
  };

  const closeArchiveDialog = () => {
    if (archiving) {
      return;
    }
    setShowArchiveDialog(false);
    requestAnimationFrame(() => archiveButtonRef.current?.focus());
  };

  const handleArchive = async () => {
    if (archivingRef.current) {
      return;
    }
    archivingRef.current = true;
    setArchiving(true);
    setApiError(null);
    try {
      await branchService.archiveBranch(branchId);
      navigate('/branches', { replace: true, state: { notice: BRANCH_STRINGS.archiveSuccess } });
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        setShowArchiveDialog(false);
        setNotFound(true);
      } else {
        setApiError(toBranchApiError(error, BRANCH_STRINGS.archiveError));
      }
    } finally {
      archivingRef.current = false;
      setArchiving(false);
    }
  };

  if (loading) {
    return (
      <AuthenticatedLayout title={BRANCH_STRINGS.detailsTitle}>
        <p className="settings-loading" role="status">{BRANCH_STRINGS.loading}</p>
      </AuthenticatedLayout>
    );
  }

  if (notFound) {
    return (
      <AuthenticatedLayout title={BRANCH_STRINGS.detailsTitle}>
        <section className="settings-card branches-empty-state">
          <h2 className="settings-section-title">{BRANCH_STRINGS.notFoundTitle}</h2>
          <p>{BRANCH_STRINGS.notFoundDescription}</p>
          <Link to="/branches" className="btn btn-secondary">
            {BRANCH_STRINGS.backToBranchesAction}
          </Link>
        </section>
      </AuthenticatedLayout>
    );
  }

  if (!branch) {
    return (
      <AuthenticatedLayout title={BRANCH_STRINGS.detailsTitle}>
        <section className="settings-card branches-empty-state">
          <ErrorDisplay error={apiError} />
          <button type="button" className="btn btn-secondary" onClick={() => setReloadVersion((v) => v + 1)}>
            {BRANCH_STRINGS.retryAction}
          </button>
          <Link to="/branches" className="btn btn-secondary">
            {BRANCH_STRINGS.backToBranchesAction}
          </Link>
        </section>
      </AuthenticatedLayout>
    );
  }

  return (
    <AuthenticatedLayout title={branch.name}>
      <section className="settings-card branch-details-card">
        <p className="branch-placeholder-title">{BRANCH_STRINGS.plansPlaceholder}</p>
        <p className="branch-placeholder-description">{BRANCH_STRINGS.plansDescription}</p>

        <ErrorDisplay error={apiError} />

        {showRenameForm ? (
          <section className="branch-rename-section" aria-labelledby="rename-branch-title">
            <h2 id="rename-branch-title" className="settings-section-title">
              {BRANCH_STRINGS.renameTitle}
            </h2>
            <BranchForm
              initialName={branch.name}
              submitLabel={BRANCH_STRINGS.saveAction}
              onSubmit={handleRename}
              onCancel={() => setShowRenameForm(false)}
            />
          </section>
        ) : (
          <div className="branch-details-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowRenameForm(true)}>
              {BRANCH_STRINGS.renameTitle}
            </button>
            <button
              ref={archiveButtonRef}
              type="button"
              className="btn btn-danger"
              onClick={() => setShowArchiveDialog(true)}
            >
              {BRANCH_STRINGS.archiveAction}
            </button>
          </div>
        )}
      </section>

      <ArchiveConfirmDialog
        open={showArchiveDialog}
        submitting={archiving}
        onConfirm={handleArchive}
        onCancel={closeArchiveDialog}
      />
    </AuthenticatedLayout>
  );
}

export default BranchDetailsPage;

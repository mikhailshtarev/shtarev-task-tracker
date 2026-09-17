import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { ErrorDisplay } from '../auth/components/ErrorDisplay';
import { ArchiveConfirmDialog } from '../branches/ArchiveConfirmDialog';
import { branchService, type Branch } from '../branches/branchService';
import { isBranchId } from '../branches/branchValidation';
import AuthenticatedLayout from '../components/AuthenticatedLayout';
import { WorkPlanForm } from '../workPlans/WorkPlanForm';
import { toWorkPlanApiError } from '../workPlans/workPlanErrors';
import { workPlanService, type WorkPlan } from '../workPlans/workPlanService';
import { WORK_PLAN_STRINGS } from '../workPlans/strings';
import { removeNavigationTreeNode } from '../navigation/navigationTreeEvents';

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

function WorkPlanDetailsPage() {
  const { planId = '' } = useParams();
  const navigate = useNavigate();
  const [plan, setPlan] = useState<WorkPlan | null>(null);
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
    if (!isBranchId(planId)) {
      setNotFound(true);
      setLoading(false);
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    setPlan(null);
    setBranch(null);
    setNotFound(false);
    setApiError(null);

    void (async () => {
      try {
        const workPlan = await workPlanService.getWorkPlan(planId, controller.signal);
        const parentBranch = await branchService.getBranch(workPlan.branchId, controller.signal);
        if (controller.signal.aborted) {
          return;
        }
        setPlan(workPlan);
        setBranch(parentBranch);
      } catch (error) {
        if (isAbortError(error)) {
          return;
        }
        if (error instanceof ApiError && error.status === 404) {
          setNotFound(true);
        } else {
          setApiError(toWorkPlanApiError(error, WORK_PLAN_STRINGS.loadError));
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    })();

    return () => controller.abort();
  }, [planId, reloadVersion]);

  const handleRename = async (name: string) => {
    const updated = await workPlanService.updateWorkPlan(planId, name);
    setPlan(updated);
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
    if (archivingRef.current || !plan) {
      return;
    }
    archivingRef.current = true;
    setArchiving(true);
    setApiError(null);
    try {
      await workPlanService.archiveWorkPlan(planId);
      removeNavigationTreeNode(planId);
      navigate(`/branches/${plan.branchId}`, {
        replace: true,
        state: { notice: WORK_PLAN_STRINGS.archiveSuccess },
      });
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        setShowArchiveDialog(false);
        setNotFound(true);
      } else {
        setApiError(toWorkPlanApiError(error, WORK_PLAN_STRINGS.archiveError));
      }
    } finally {
      archivingRef.current = false;
      setArchiving(false);
    }
  };

  if (loading) {
    return (
      <AuthenticatedLayout title={WORK_PLAN_STRINGS.detailsTitle}>
        <p className="settings-loading" role="status">{WORK_PLAN_STRINGS.loading}</p>
      </AuthenticatedLayout>
    );
  }

  if (notFound) {
    return (
      <AuthenticatedLayout title={WORK_PLAN_STRINGS.detailsTitle}>
        <section className="settings-card branches-empty-state">
          <h2 className="settings-section-title">{WORK_PLAN_STRINGS.notFoundTitle}</h2>
          <p>{WORK_PLAN_STRINGS.notFoundDescription}</p>
          <Link to="/branches" className="btn btn-secondary">
            {WORK_PLAN_STRINGS.backToBranchAction}
          </Link>
        </section>
      </AuthenticatedLayout>
    );
  }

  if (!plan || !branch) {
    return (
      <AuthenticatedLayout title={WORK_PLAN_STRINGS.detailsTitle}>
        <section className="settings-card branches-empty-state">
          <ErrorDisplay error={apiError} />
          <button type="button" className="btn btn-secondary" onClick={() => setReloadVersion((v) => v + 1)}>
            {WORK_PLAN_STRINGS.retryAction}
          </button>
          <Link to="/branches" className="btn btn-secondary">
            {WORK_PLAN_STRINGS.backToBranchAction}
          </Link>
        </section>
      </AuthenticatedLayout>
    );
  }

  return (
    <AuthenticatedLayout
      title={plan.name}
      headerActions={(
        <button
          ref={archiveButtonRef}
          type="button"
          className="btn btn-danger"
          onClick={() => setShowArchiveDialog(true)}
        >
          {WORK_PLAN_STRINGS.archiveAction}
        </button>
      )}
    >
      <section className="settings-card work-plan-details-card">
        <p className="work-plan-parent">
          <span>{WORK_PLAN_STRINGS.parentBranchLabel}: </span>
          <Link to={`/branches/${branch.id}`}>{branch.name}</Link>
        </p>
        <p className="branch-placeholder-title">{WORK_PLAN_STRINGS.tasksPlaceholder}</p>
        <p className="branch-placeholder-description">{WORK_PLAN_STRINGS.tasksDescription}</p>

        <ErrorDisplay error={apiError} />

        {showRenameForm ? (
          <section className="branch-rename-section" aria-labelledby="rename-work-plan-title">
            <h2 id="rename-work-plan-title" className="settings-section-title">
              {WORK_PLAN_STRINGS.renameTitle}
            </h2>
            <WorkPlanForm
              initialName={plan.name}
              submitLabel={WORK_PLAN_STRINGS.saveAction}
              onSubmit={handleRename}
              onCancel={() => setShowRenameForm(false)}
            />
          </section>
        ) : (
          <div className="branch-details-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowRenameForm(true)}>
              {WORK_PLAN_STRINGS.renameTitle}
            </button>
          </div>
        )}
      </section>

      <ArchiveConfirmDialog
        open={showArchiveDialog}
        submitting={archiving}
        title={WORK_PLAN_STRINGS.archiveDialogTitle}
        description={WORK_PLAN_STRINGS.archiveDialogDescription}
        confirmLabel={WORK_PLAN_STRINGS.archiveConfirmAction}
        cancelLabel={WORK_PLAN_STRINGS.cancelAction}
        submittingLabel={WORK_PLAN_STRINGS.loadingMore}
        onConfirm={handleArchive}
        onCancel={closeArchiveDialog}
      />
    </AuthenticatedLayout>
  );
}

export default WorkPlanDetailsPage;

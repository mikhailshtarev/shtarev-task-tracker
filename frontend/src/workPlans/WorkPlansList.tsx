import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { ErrorDisplay } from '../auth/components/ErrorDisplay';
import { WorkPlanForm } from './WorkPlanForm';
import { toWorkPlanApiError } from './workPlanErrors';
import { workPlanService, type WorkPlan } from './workPlanService';
import { normalizeWorkPlanText, validateWorkPlanQuery } from './workPlanValidation';
import { WORK_PLAN_STRINGS } from './strings';

interface WorkPlansListProps {
  branchId: string;
  notice?: string | null;
  onBranchNotFound: () => void;
}

function mergeUniqueWorkPlans(current: WorkPlan[], incoming: WorkPlan[]): WorkPlan[] {
  const ids = new Set(current.map((plan) => plan.id));
  return [...current, ...incoming.filter((plan) => !ids.has(plan.id))];
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

export function WorkPlansList({ branchId, notice, onBranchNotFound }: WorkPlansListProps) {
  const [plans, setPlans] = useState<WorkPlan[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [queryError, setQueryError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [reloadVersion, setReloadVersion] = useState(0);
  const [createdNotice, setCreatedNotice] = useState<string | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const requestVersionRef = useRef(0);
  const preservePlansOnReloadRef = useRef(false);

  useEffect(() => {
    const validationError = validateWorkPlanQuery(search);
    const normalizedQuery = normalizeWorkPlanText(search);
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const requestVersion = ++requestVersionRef.current;
    const preservePlans = preservePlansOnReloadRef.current;
    preservePlansOnReloadRef.current = false;

    if (!preservePlans) {
      setPlans([]);
    }
    setNextCursor(null);
    setLoading(false);
    setLoadingMore(false);
    setApiError(null);
    setQueryError(validationError);

    if (validationError) {
      return () => controller.abort();
    }

    setLoading(true);
    void workPlanService
      .getWorkPlans(branchId, { q: normalizedQuery || undefined }, controller.signal)
      .then((page) => {
        if (requestVersion !== requestVersionRef.current) {
          return;
        }
        setPlans(page.items);
        setNextCursor(page.nextCursor);
      })
      .catch((error: unknown) => {
        if (requestVersion !== requestVersionRef.current || isAbortError(error)) {
          return;
        }
        if (error instanceof ApiError && error.status === 404) {
          onBranchNotFound();
          return;
        }
        setApiError(toWorkPlanApiError(error, WORK_PLAN_STRINGS.loadError));
      })
      .finally(() => {
        if (requestVersion === requestVersionRef.current) {
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, [branchId, onBranchNotFound, reloadVersion, search]);

  const handleLoadMore = async () => {
    if (!nextCursor || loadingMore) {
      return;
    }

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const requestVersion = ++requestVersionRef.current;
    const normalizedQuery = normalizeWorkPlanText(search);
    setLoadingMore(true);
    setApiError(null);

    try {
      const page = await workPlanService.getWorkPlans(
        branchId,
        { cursor: nextCursor, q: normalizedQuery || undefined },
        controller.signal,
      );
      if (requestVersion !== requestVersionRef.current) {
        return;
      }
      setPlans((current) => mergeUniqueWorkPlans(current, page.items));
      setNextCursor(page.nextCursor);
    } catch (error) {
      if (requestVersion === requestVersionRef.current && !isAbortError(error)) {
        if (error instanceof ApiError && error.status === 404) {
          onBranchNotFound();
        } else {
          setApiError(toWorkPlanApiError(error, WORK_PLAN_STRINGS.loadError));
        }
      }
    } finally {
      if (requestVersion === requestVersionRef.current) {
        setLoadingMore(false);
      }
    }
  };

  const handleCreated = async (name: string) => {
    const plan = await workPlanService.createWorkPlan(branchId, name);
    const hasActiveSearch = normalizeWorkPlanText(search).length > 0;
    setPlans((current) => [plan, ...current.filter((item) => item.id !== plan.id)]);
    setNextCursor(null);
    setCreatedNotice(WORK_PLAN_STRINGS.createdSuccess(plan.name));
    setShowCreateForm(false);
    setSearch('');
    if (hasActiveSearch) {
      return;
    }
    preservePlansOnReloadRef.current = true;
    setReloadVersion((version) => version + 1);
  };

  const normalizedSearch = normalizeWorkPlanText(search);
  const isSearching = normalizedSearch.length > 0;
  const hasNoResults = !loading && !apiError && !queryError && plans.length === 0;

  return (
    <section className="work-plans-section" aria-labelledby="work-plans-title">
      <div className="branches-toolbar">
        <div className="branches-search">
          <h2 id="work-plans-title" className="visually-hidden">{WORK_PLAN_STRINGS.listTitle}</h2>
          <label className="input-label" htmlFor="work-plans-search">
            {WORK_PLAN_STRINGS.searchLabel}
          </label>
          <input
            id="work-plans-search"
            className={`input ${queryError ? 'error' : ''}`}
            value={search}
            placeholder={WORK_PLAN_STRINGS.searchPlaceholder}
            onChange={(event) => setSearch(event.target.value)}
          />
          {queryError && <div className="input-error">{queryError}</div>}
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setShowCreateForm(true)}>
          {WORK_PLAN_STRINGS.createAction}
        </button>
      </div>

      {(notice ?? createdNotice) && <p className="branches-notice" role="status">{notice ?? createdNotice}</p>}

      {showCreateForm && (
        <section className="settings-card branch-form-card" aria-labelledby="create-work-plan-title">
          <h2 id="create-work-plan-title" className="settings-section-title">
            {WORK_PLAN_STRINGS.createTitle}
          </h2>
          <WorkPlanForm
            submitLabel={WORK_PLAN_STRINGS.createAction}
            onSubmit={handleCreated}
            onCancel={() => setShowCreateForm(false)}
          />
        </section>
      )}

      {loading && <p className="settings-loading" role="status">{WORK_PLAN_STRINGS.loading}</p>}
      {!loading && <ErrorDisplay error={apiError} />}

      {hasNoResults && !isSearching && (
        <section className="settings-card branches-empty-state">
          <h2 className="settings-section-title">{WORK_PLAN_STRINGS.emptyTitle}</h2>
          <p>{WORK_PLAN_STRINGS.emptyDescription}</p>
          <button type="button" className="btn btn-primary" onClick={() => setShowCreateForm(true)}>
            {WORK_PLAN_STRINGS.createAction}
          </button>
        </section>
      )}

      {hasNoResults && isSearching && (
        <section className="settings-card branches-empty-state">
          <h2 className="settings-section-title">{WORK_PLAN_STRINGS.noResultsTitle}</h2>
          <p>{WORK_PLAN_STRINGS.noResultsDescription}</p>
          <button type="button" className="btn btn-secondary" onClick={() => setSearch('')}>
            {WORK_PLAN_STRINGS.clearSearchAction}
          </button>
        </section>
      )}

      {!loading && !apiError && plans.length > 0 && (
        <ul className="branches-list" aria-label={WORK_PLAN_STRINGS.listLabel}>
          {plans.map((plan) => (
            <li key={plan.id} className="branch-card">
              <Link to={`/work-plans/${plan.id}`} className="branch-card-link">
                {plan.name}
              </Link>
            </li>
          ))}
        </ul>
      )}

      {!loading && apiError && (
        <button type="button" className="btn btn-secondary" onClick={() => setReloadVersion((v) => v + 1)}>
          {WORK_PLAN_STRINGS.retryAction}
        </button>
      )}

      {nextCursor && !loading && !apiError && (
        <div className="branches-load-more">
          <button type="button" className="btn btn-secondary" onClick={handleLoadMore} disabled={loadingMore}>
            {loadingMore ? WORK_PLAN_STRINGS.loadingMore : WORK_PLAN_STRINGS.loadMoreAction}
          </button>
        </div>
      )}
    </section>
  );
}

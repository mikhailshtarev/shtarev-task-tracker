import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { ErrorDisplay } from '../auth/components/ErrorDisplay';
import { BranchForm } from '../branches/BranchForm';
import { toBranchApiError } from '../branches/branchErrors';
import { branchService, type Branch } from '../branches/branchService';
import { normalizeBranchText, validateBranchQuery } from '../branches/branchValidation';
import { BRANCH_STRINGS } from '../branches/strings';
import AuthenticatedLayout from '../components/AuthenticatedLayout';

interface BranchesLocationState {
  notice?: string;
}

function mergeUniqueBranches(current: Branch[], incoming: Branch[]): Branch[] {
  const ids = new Set(current.map((branch) => branch.id));
  return [...current, ...incoming.filter((branch) => !ids.has(branch.id))];
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

function BranchesPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [branches, setBranches] = useState<Branch[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [queryError, setQueryError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [apiError, setApiError] = useState<ApiError | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [reloadVersion, setReloadVersion] = useState(0);
  const [notice, setNotice] = useState<string | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const requestVersionRef = useRef(0);

  useEffect(() => {
    const state = location.state as BranchesLocationState | null;
    if (state?.notice) {
      setNotice(state.notice);
      navigate(location.pathname, { replace: true, state: null });
    }
  }, [location.pathname, location.state, navigate]);

  useEffect(() => {
    const validationError = validateBranchQuery(search);
    const normalizedQuery = normalizeBranchText(search);
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const requestVersion = ++requestVersionRef.current;

    setBranches([]);
    setNextCursor(null);
    setLoading(false);
    setLoadingMore(false);
    setApiError(null);
    setQueryError(validationError);

    if (validationError) {
      return () => controller.abort();
    }

    setLoading(true);
    void branchService
      .getBranches({ q: normalizedQuery || undefined }, controller.signal)
      .then((page) => {
        if (requestVersion !== requestVersionRef.current) {
          return;
        }
        setBranches(page.items);
        setNextCursor(page.nextCursor);
      })
      .catch((error: unknown) => {
        if (requestVersion !== requestVersionRef.current || isAbortError(error)) {
          return;
        }
        setApiError(toBranchApiError(error, BRANCH_STRINGS.loadError));
      })
      .finally(() => {
        if (requestVersion === requestVersionRef.current) {
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, [search, reloadVersion]);

  const handleLoadMore = async () => {
    if (!nextCursor || loadingMore) {
      return;
    }

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const requestVersion = ++requestVersionRef.current;
    const normalizedQuery = normalizeBranchText(search);
    setLoadingMore(true);
    setApiError(null);

    try {
      const page = await branchService.getBranches(
        { cursor: nextCursor, q: normalizedQuery || undefined },
        controller.signal,
      );
      if (requestVersion !== requestVersionRef.current) {
        return;
      }
      setBranches((current) => mergeUniqueBranches(current, page.items));
      setNextCursor(page.nextCursor);
    } catch (error) {
      if (requestVersion === requestVersionRef.current && !isAbortError(error)) {
        setApiError(toBranchApiError(error, BRANCH_STRINGS.loadError));
      }
    } finally {
      if (requestVersion === requestVersionRef.current) {
        setLoadingMore(false);
      }
    }
  };

  const handleCreated = async (name: string) => {
    const branch = await branchService.createBranch(name);
    setNotice(BRANCH_STRINGS.createdSuccess(branch.name));
    setShowCreateForm(false);
    setBranches((current) => [branch, ...current.filter((item) => item.id !== branch.id)]);
    setNextCursor(null);
    setSearch('');
    setReloadVersion((version) => version + 1);
  };

  const normalizedSearch = normalizeBranchText(search);
  const isSearching = normalizedSearch.length > 0;
  const hasNoResults = !loading && !apiError && !queryError && branches.length === 0;

  return (
    <AuthenticatedLayout title={BRANCH_STRINGS.listTitle}>
      <section className="branches-toolbar" aria-label={BRANCH_STRINGS.listTitle}>
        <div className="branches-search">
          <label className="input-label" htmlFor="branches-search">
            {BRANCH_STRINGS.searchLabel}
          </label>
          <input
            id="branches-search"
            className={`input ${queryError ? 'error' : ''}`}
            value={search}
            placeholder={BRANCH_STRINGS.searchPlaceholder}
            onChange={(event) => setSearch(event.target.value)}
          />
          {queryError && <div className="input-error">{queryError}</div>}
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setShowCreateForm(true)}>
          {BRANCH_STRINGS.createAction}
        </button>
      </section>

      {notice && <p className="branches-notice" role="status">{notice}</p>}

      {showCreateForm && (
        <section className="settings-card branch-form-card" aria-labelledby="create-branch-title">
          <h2 id="create-branch-title" className="settings-section-title">
            {BRANCH_STRINGS.createTitle}
          </h2>
          <BranchForm
            submitLabel={BRANCH_STRINGS.createAction}
            onSubmit={handleCreated}
            onCancel={() => setShowCreateForm(false)}
          />
        </section>
      )}

      {loading && <p className="settings-loading" role="status">{BRANCH_STRINGS.loading}</p>}
      {!loading && <ErrorDisplay error={apiError} />}

      {hasNoResults && !isSearching && (
        <section className="settings-card branches-empty-state">
          <h2 className="settings-section-title">{BRANCH_STRINGS.emptyTitle}</h2>
          <p>{BRANCH_STRINGS.emptyDescription}</p>
          <button type="button" className="btn btn-primary" onClick={() => setShowCreateForm(true)}>
            {BRANCH_STRINGS.createAction}
          </button>
        </section>
      )}

      {hasNoResults && isSearching && (
        <section className="settings-card branches-empty-state">
          <h2 className="settings-section-title">{BRANCH_STRINGS.noResultsTitle}</h2>
          <p>{BRANCH_STRINGS.noResultsDescription}</p>
          <button type="button" className="btn btn-secondary" onClick={() => setSearch('')}>
            {BRANCH_STRINGS.clearSearchAction}
          </button>
        </section>
      )}

      {!loading && !apiError && branches.length > 0 && (
        <ul className="branches-list" aria-label={BRANCH_STRINGS.listLabel}>
          {branches.map((branch) => (
            <li key={branch.id} className="branch-card">
              <Link to={`/branches/${branch.id}`} className="branch-card-link">
                {branch.name}
              </Link>
            </li>
          ))}
        </ul>
      )}

      {!loading && apiError && (
        <button type="button" className="btn btn-secondary" onClick={() => setReloadVersion((v) => v + 1)}>
          {BRANCH_STRINGS.retryAction}
        </button>
      )}

      {nextCursor && !loading && !apiError && (
        <div className="branches-load-more">
          <button type="button" className="btn btn-secondary" onClick={handleLoadMore} disabled={loadingMore}>
            {loadingMore ? BRANCH_STRINGS.loadingMore : BRANCH_STRINGS.loadMoreAction}
          </button>
        </div>
      )}
    </AuthenticatedLayout>
  );
}

export default BranchesPage;

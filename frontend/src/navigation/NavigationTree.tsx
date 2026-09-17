import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { ErrorDisplay } from '../auth/components/ErrorDisplay';
import { useAuth } from '../auth/hooks/useAuth';
import { BranchForm } from '../branches/BranchForm';
import { branchService, type Branch } from '../branches/branchService';
import { toBranchApiError } from '../branches/branchErrors';
import {
  NAVIGATION_TREE_CHANGED_EVENT,
} from './navigationTreeEvents';
import {
  navigationTreeService,
  type NavigationNodeType,
  type NavigationTreeNode,
} from './navigationTreeService';
import { NAVIGATION_TREE_STRINGS } from './strings';

type ParentType = Extract<NavigationNodeType, 'branch' | 'work_plan'>;

interface TreeState {
  nodesById: Record<string, NavigationTreeNode>;
  childrenByKey: Record<string, string[]>;
  nextCursorByKey: Record<string, string | null>;
  loadedKeys: Record<string, boolean>;
  loadingKeys: Record<string, boolean>;
  errorsByKey: Record<string, ApiError | null>;
}

interface CreateContext {
  parentId: string | null;
  parentDepth: number;
}

const ROOT_KEY = 'root';

function createInitialState(): TreeState {
  return {
    nodesById: {},
    childrenByKey: {},
    nextCursorByKey: {},
    loadedKeys: {},
    loadingKeys: {},
    errorsByKey: {},
  };
}

function getChildrenKey(type?: ParentType, id?: string): string {
  return type && id ? `${type}:${id}` : ROOT_KEY;
}

function toTreeNode(branch: Branch, parentId: string | null, depth: number): NavigationTreeNode {
  return {
    id: branch.id,
    type: 'branch',
    parentId: branch.parentId ?? parentId,
    name: branch.name,
    depth: branch.depth ?? depth,
    hasChildren: false,
  };
}

async function loadNavigationLevel(
  params: { parentType?: ParentType; parentId?: string; cursor?: string },
  signal: AbortSignal,
  forceLegacy: boolean,
  onLegacyFallback: () => void,
): Promise<{ items: NavigationTreeNode[]; nextCursor: string | null }> {
  const loadLegacyBranches = async () => {
    const page = await branchService.getBranches({ limit: 500, cursor: params.cursor }, signal);
    const parentId = params.parentType === 'branch' ? params.parentId ?? null : null;
    return {
      items: page.items
        .filter((branch) => (branch.parentId ?? null) === parentId)
        .map((branch) => ({
          ...toTreeNode(branch, parentId, branch.depth ?? (parentId ? 2 : 1)),
          hasChildren: page.items.some((candidate) => candidate.parentId === branch.id),
        })),
      nextCursor: page.nextCursor,
    };
  };

  if (forceLegacy) {
    return loadLegacyBranches();
  }

  try {
    return await navigationTreeService.getTree(params, signal);
  } catch (error) {
    // Keep existing projects visible while an older Gateway is being restarted
    // and does not yet expose the navigation route. The branches endpoint is
    // also scoped to the current user by the backend.
    if (!(error instanceof ApiError) || ![400, 404].includes(error.status) || params.parentType === 'work_plan') {
      throw error;
    }
    onLegacyFallback();
    return loadLegacyBranches();
  }
}

function mergeNodeIds(current: string[], incoming: string[]): string[] {
  const ids = new Set(current);
  return [...current, ...incoming.filter((id) => !ids.has(id))];
}

function removeNodeAndDescendants(state: TreeState, nodeId: string): TreeState {
  if (!state.nodesById[nodeId]) {
    return state;
  }

  const removedIds = new Set<string>();
  const collect = (id: string) => {
    if (removedIds.has(id)) {
      return;
    }
    removedIds.add(id);
    const node = state.nodesById[id];
    if (!node || node.type === 'task') {
      return;
    }
    state.childrenByKey[getChildrenKey(node.type, node.id)]?.forEach(collect);
  };
  collect(nodeId);

  const nodesById = { ...state.nodesById };
  removedIds.forEach((id) => delete nodesById[id]);
  const childrenByKey = Object.fromEntries(
    Object.entries(state.childrenByKey)
      .filter(([key]) => !removedIds.has(key.split(':')[1] ?? ''))
      .map(([key, ids]) => [key, ids.filter((id) => !removedIds.has(id))]),
  );

  return {
    ...state,
    nodesById,
    childrenByKey,
  };
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

function toNavigationError(error: unknown): ApiError {
  if (error instanceof ApiError && (error.status === 502 || error.code === 'UPSTREAM_UNAVAILABLE')) {
    return new ApiError(502, { message: NAVIGATION_TREE_STRINGS.unavailable });
  }
  if (error instanceof ApiError && error.status === 400) {
    return new ApiError(400, { message: NAVIGATION_TREE_STRINGS.loadError });
  }
  return error instanceof ApiError
    ? error
    : new ApiError(0, { message: NAVIGATION_TREE_STRINGS.loadError });
}

export function NavigationTree() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [tree, setTree] = useState<TreeState>(createInitialState);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(() => new Set());
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [createContext, setCreateContext] = useState<CreateContext | null>(null);
  const [depthError, setDepthError] = useState<string | null>(null);
  const controllersRef = useRef(new Map<string, AbortController>());
  const loadingKeysRef = useRef(new Set<string>());
  const legacyKeysRef = useRef(new Set<string>());
  const menuButtonRef = useRef<HTMLButtonElement>(null);

  const loadLevel = useCallback(async (parentType?: ParentType, parentId?: string, cursor?: string) => {
    const key = getChildrenKey(parentType, parentId);
    if (loadingKeysRef.current.has(key)) {
      return;
    }

    const controller = new AbortController();
    controllersRef.current.set(key, controller);
    loadingKeysRef.current.add(key);
    setTree((current) => ({
      ...current,
      loadingKeys: { ...current.loadingKeys, [key]: true },
      errorsByKey: { ...current.errorsByKey, [key]: null },
    }));

    try {
      const page = await loadNavigationLevel(
        { parentType, parentId, cursor },
        controller.signal,
        legacyKeysRef.current.has(key),
        () => legacyKeysRef.current.add(key),
      );
      if (controller.signal.aborted) {
        return;
      }
      const activeItems = page.items.filter((item) => !item.archivedAt);
      setTree((current) => {
        const nodesById = { ...current.nodesById };
        activeItems.forEach((item) => {
          nodesById[item.id] = item;
        });
        const incomingIds = activeItems.map((item) => item.id);
        const currentIds = current.childrenByKey[key] ?? [];
        return {
          ...current,
          nodesById,
          childrenByKey: {
            ...current.childrenByKey,
            [key]: cursor ? mergeNodeIds(currentIds, incomingIds) : incomingIds,
          },
          nextCursorByKey: { ...current.nextCursorByKey, [key]: page.nextCursor },
          loadedKeys: { ...current.loadedKeys, [key]: true },
        };
      });
    } catch (error) {
      if (!isAbortError(error)) {
        setTree((current) => ({
          ...current,
          errorsByKey: { ...current.errorsByKey, [key]: toNavigationError(error) },
        }));
      }
    } finally {
      loadingKeysRef.current.delete(key);
      if (controllersRef.current.get(key) === controller) {
        controllersRef.current.delete(key);
      }
      if (!controller.signal.aborted) {
        setTree((current) => ({
          ...current,
          loadingKeys: { ...current.loadingKeys, [key]: false },
        }));
      }
    }
  }, []);

  useEffect(() => {
    if (!user) {
      return;
    }
    controllersRef.current.forEach((controller) => controller.abort());
    controllersRef.current.clear();
    loadingKeysRef.current.clear();
    legacyKeysRef.current.clear();
    setTree(createInitialState());
    setExpandedIds(new Set());
    void loadLevel();
  }, [loadLevel, user?.id]);

  useEffect(() => () => {
    controllersRef.current.forEach((controller) => controller.abort());
    controllersRef.current.clear();
  }, []);

  useEffect(() => {
    const handleTreeChanged = (event: Event) => {
      const nodeId = (event as CustomEvent<{ nodeId?: string }>).detail?.nodeId;
      if (nodeId) {
        setTree((current) => removeNodeAndDescendants(current, nodeId));
      }
    };
    window.addEventListener(NAVIGATION_TREE_CHANGED_EVENT, handleTreeChanged);
    return () => window.removeEventListener(NAVIGATION_TREE_CHANGED_EVENT, handleTreeChanged);
  }, []);

  useEffect(() => {
    if (!openMenuId) {
      return;
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        const menuButton = menuButtonRef.current;
        setOpenMenuId(null);
        requestAnimationFrame(() => menuButton?.focus());
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [openMenuId]);

  const toggleNode = (node: NavigationTreeNode) => {
    if (!node.hasChildren || node.type === 'task') {
      return;
    }
    const key = getChildrenKey(node.type, node.id);
    const isExpanded = expandedIds.has(node.id);
    setExpandedIds((current) => {
      const next = new Set(current);
      if (isExpanded) {
        next.delete(node.id);
      } else {
        next.add(node.id);
      }
      return next;
    });
    if (!isExpanded && !tree.loadedKeys[key]) {
      void loadLevel(node.type, node.id);
    }
  };

  const openCreateForm = (parentId: string | null, parentDepth: number) => {
    setCreateContext({ parentId, parentDepth });
    setOpenMenuId(null);
    setDepthError(null);
  };

  const handleCreate = async (name: string) => {
    if (!createContext) {
      return;
    }
    if (createContext.parentDepth >= 7) {
      setDepthError(NAVIGATION_TREE_STRINGS.depthLimit);
      return;
    }
    const branch = await branchService.createBranch(name, createContext.parentId);
    const key = createContext.parentId ? getChildrenKey('branch', createContext.parentId) : ROOT_KEY;
    const node = toTreeNode(branch, createContext.parentId, createContext.parentDepth + 1);
    setTree((current) => ({
      ...current,
      nodesById: {
        ...current.nodesById,
        [node.id]: node,
        ...(createContext.parentId && current.nodesById[createContext.parentId]
          ? {
              [createContext.parentId]: {
                ...current.nodesById[createContext.parentId],
                hasChildren: true,
              },
            }
          : {}),
      },
      childrenByKey: {
        ...current.childrenByKey,
        [key]: mergeNodeIds(current.childrenByKey[key] ?? [], [node.id]),
      },
      loadedKeys: { ...current.loadedKeys, [key]: true },
    }));
    if (createContext.parentId) {
      setExpandedIds((current) => new Set(current).add(createContext.parentId!));
    }
    setCreateContext(null);
    navigate(`/branches/${branch.id}`);
  };

  const renderCreateForm = (context: CreateContext) => (
    <section className="navigation-tree-form" aria-label={NAVIGATION_TREE_STRINGS.newProject}>
      {depthError && <p className="input-error" role="alert">{depthError}</p>}
      <BranchForm
        submitLabel={NAVIGATION_TREE_STRINGS.newProject}
        onSubmit={async (name) => {
          try {
            await handleCreate(name);
          } catch (error) {
            throw toBranchApiError(error, NAVIGATION_TREE_STRINGS.createError);
          }
        }}
        onCancel={() => {
          setCreateContext(null);
          setDepthError(null);
        }}
      />
    </section>
  );

  const renderNodes = (parentType?: ParentType, parentId?: string) => {
    const key = getChildrenKey(parentType, parentId);
    const nodeIds = tree.childrenByKey[key] ?? [];
    return (
      <>
        {nodeIds.length > 0 && (
          <ul className="navigation-tree-list" role={parentId ? 'group' : 'tree'}>
            {nodeIds.map((id) => {
              const node = tree.nodesById[id];
              if (!node) {
                return null;
              }
              const childParentType: ParentType | null = node.type === 'task' ? null : node.type;
              const nodeKey = childParentType ? getChildrenKey(childParentType, node.id) : null;
              const isExpanded = expandedIds.has(node.id);
              const isMenuOpen = openMenuId === node.id;
              return (
                <li key={node.id} className={`navigation-tree-item navigation-tree-item-${node.type}`} role="treeitem" aria-level={node.depth} aria-expanded={node.hasChildren ? isExpanded : undefined}>
                  <div className="navigation-tree-row">
                    {node.hasChildren && node.type !== 'task' ? (
                      <button
                        type="button"
                        className="navigation-tree-toggle"
                        aria-label={isExpanded ? NAVIGATION_TREE_STRINGS.collapseProject(node.name) : NAVIGATION_TREE_STRINGS.expandProject(node.name)}
                        onClick={() => toggleNode(node)}
                      >
                        {isExpanded ? '⌄' : '›'}
                      </button>
                    ) : (
                      <span className="navigation-tree-spacer" aria-hidden="true" />
                    )}
                    <span className="navigation-tree-icon" aria-hidden="true">
                      {node.type === 'branch' ? '□' : node.type === 'work_plan' ? '≡' : '•'}
                    </span>
                    {node.type === 'branch' ? (
                      <Link to={`/branches/${node.id}`} className="navigation-tree-link">{node.name}</Link>
                    ) : node.type === 'work_plan' ? (
                      <Link to={`/work-plans/${node.id}`} className="navigation-tree-link">{node.name}</Link>
                    ) : (
                      <span className="navigation-tree-task" title={NAVIGATION_TREE_STRINGS.taskNode}>{node.name}</span>
                    )}
                    {node.type === 'branch' && (
                      <button
                        ref={isMenuOpen ? menuButtonRef : undefined}
                        type="button"
                        className="navigation-tree-add"
                        aria-label={NAVIGATION_TREE_STRINGS.projectActions(node.name)}
                        aria-expanded={isMenuOpen}
                        aria-controls={`project-actions-${node.id}`}
                        onClick={() => {
                          setDepthError(null);
                          setOpenMenuId((current) => current === node.id ? null : node.id);
                        }}
                      >
                        +
                      </button>
                    )}
                  </div>
                  {isMenuOpen && node.type === 'branch' && (
                    <div id={`project-actions-${node.id}`} className="navigation-tree-menu" role="menu" aria-label={NAVIGATION_TREE_STRINGS.actionsLabel}>
                      <button
                        type="button"
                        role="menuitem"
                        onClick={() => {
                          if (node.depth >= 7) {
                            setDepthError(NAVIGATION_TREE_STRINGS.depthLimit);
                            setOpenMenuId(null);
                            return;
                          }
                          openCreateForm(node.id, node.depth);
                        }}
                      >
                        {NAVIGATION_TREE_STRINGS.newChildProject}
                      </button>
                    </div>
                  )}
                  {createContext?.parentId === node.id && renderCreateForm(createContext)}
                  {isExpanded && childParentType && renderNodes(childParentType, node.id)}
                  {isExpanded && nodeKey && tree.errorsByKey[nodeKey] && (
                    <div className="navigation-tree-error">
                      <ErrorDisplay error={tree.errorsByKey[nodeKey]} />
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadLevel(childParentType!, node.id)}>
                        {NAVIGATION_TREE_STRINGS.retry}
                      </button>
                    </div>
                  )}
                  {isExpanded && nodeKey && tree.nextCursorByKey[nodeKey] && (
                    <button type="button" className="btn btn-ghost btn-sm navigation-tree-more" onClick={() => void loadLevel(childParentType!, node.id, tree.nextCursorByKey[nodeKey] ?? undefined)}>
                      {NAVIGATION_TREE_STRINGS.loadMore}
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </>
    );
  };

  const rootError = tree.errorsByKey[ROOT_KEY];
  const rootLoading = tree.loadingKeys[ROOT_KEY];
  const rootItems = tree.childrenByKey[ROOT_KEY] ?? [];

  return (
    <section className="navigation-tree" aria-label={NAVIGATION_TREE_STRINGS.treeLabel}>
      {rootLoading && rootItems.length === 0 && <p className="navigation-tree-loading" role="status">{NAVIGATION_TREE_STRINGS.loading}</p>}
      {rootError && (
        <div className="navigation-tree-error">
          <ErrorDisplay error={rootError} />
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadLevel()}>
            {NAVIGATION_TREE_STRINGS.retry}
          </button>
        </div>
      )}
      {depthError && !createContext && <p className="input-error" role="alert">{depthError}</p>}
      {!rootLoading && !rootError && rootItems.length === 0 && <p className="navigation-tree-empty">{NAVIGATION_TREE_STRINGS.empty}</p>}
      {renderNodes()}
      {tree.nextCursorByKey[ROOT_KEY] && (
        <button type="button" className="btn btn-ghost btn-sm navigation-tree-more" onClick={() => void loadLevel(undefined, undefined, tree.nextCursorByKey[ROOT_KEY] ?? undefined)}>
          {NAVIGATION_TREE_STRINGS.loadMore}
        </button>
      )}
      {createContext?.parentId === null ? renderCreateForm(createContext) : !createContext && (
        <button type="button" className="navigation-tree-create-root" onClick={() => openCreateForm(null, 0)}>
          + {NAVIGATION_TREE_STRINGS.newProject}
        </button>
      )}
    </section>
  );
}

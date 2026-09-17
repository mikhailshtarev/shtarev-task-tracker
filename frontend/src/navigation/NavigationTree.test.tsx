import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../auth/context/AuthContext';
import { redirectHandler } from '../api/refreshInterceptor';
import { branchService } from '../branches/branchService';
import { stubFetch, type MockRoute } from '../test-utils/mockApi';
import { NavigationTree } from './NavigationTree';
import { navigationTreeService } from './navigationTreeService';
import { NAVIGATION_TREE_STRINGS } from './strings';
import { ApiError } from '../api/client';

const ROOT_PROJECT = {
  id: '11111111-1111-4111-8111-111111111111',
  type: 'branch' as const,
  parentId: null,
  name: 'Работа',
  depth: 1,
  hasChildren: true,
};
const CHILD_PROJECT = {
  id: '22222222-2222-4222-8222-222222222222',
  type: 'branch' as const,
  parentId: ROOT_PROJECT.id,
  name: 'Проект X',
  depth: 2,
  hasChildren: false,
};
const AUTH_ROUTES: MockRoute[] = [
  { method: 'POST', path: '/auth/refresh', status: 200, body: { accessToken: 'token' } },
  { method: 'GET', path: '/auth/me', status: 200, body: { id: 'u-1', email: 'user@example.com', name: 'Иван' } },
];

function renderTree() {
  vi.spyOn(redirectHandler, 'go').mockImplementation(() => {});
  stubFetch(AUTH_ROUTES);
  render(
    <AuthProvider>
      <MemoryRouter>
        <NavigationTree />
      </MemoryRouter>
    </AuthProvider>,
  );
}

beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('NavigationTree', () => {
  it('FE-01: показывает пустое дерево и плашку создания корневого проекта', async () => {
    vi.spyOn(navigationTreeService, 'getTree').mockResolvedValue({ items: [], nextCursor: null });
    renderTree();

    expect(await screen.findByText(NAVIGATION_TREE_STRINGS.empty)).toBeTruthy();
    expect(screen.getByRole('button', { name: `+ ${NAVIGATION_TREE_STRINGS.newProject}` })).toBeTruthy();
  });

  it('FE-04: раскрывает проект один раз и создаёт дочерний узел в его контексте', async () => {
    vi.spyOn(navigationTreeService, 'getTree')
      .mockResolvedValueOnce({ items: [ROOT_PROJECT], nextCursor: null })
      .mockResolvedValueOnce({ items: [], nextCursor: null });
    const create = vi.spyOn(branchService, 'createBranch').mockResolvedValue({
      id: CHILD_PROJECT.id,
      name: CHILD_PROJECT.name,
      parentId: ROOT_PROJECT.id,
      depth: 2,
      createdAt: '2026-09-17T10:00:00Z',
      updatedAt: '2026-09-17T10:00:00Z',
    });
    renderTree();

    await screen.findByText(ROOT_PROJECT.name);
    const toggle = screen.getByRole('button', { name: NAVIGATION_TREE_STRINGS.expandProject(ROOT_PROJECT.name) });
    fireEvent.click(toggle);
    await waitFor(() => expect(vi.mocked(navigationTreeService.getTree).mock.calls).toHaveLength(2));
    fireEvent.click(screen.getByRole('button', { name: NAVIGATION_TREE_STRINGS.collapseProject(ROOT_PROJECT.name) }));
    fireEvent.click(screen.getByRole('button', { name: NAVIGATION_TREE_STRINGS.expandProject(ROOT_PROJECT.name) }));
    expect(vi.mocked(navigationTreeService.getTree).mock.calls).toHaveLength(2);

    fireEvent.click(screen.getByRole('button', { name: NAVIGATION_TREE_STRINGS.projectActions(ROOT_PROJECT.name) }));
    fireEvent.click(screen.getByRole('menuitem', { name: NAVIGATION_TREE_STRINGS.newChildProject }));
    fireEvent.change(screen.getByLabelText('Название проекта'), { target: { value: CHILD_PROJECT.name } });
    fireEvent.submit(screen.getByLabelText('Название проекта').closest('form')!);

    expect(await screen.findByText(CHILD_PROJECT.name)).toBeTruthy();
    expect(create).toHaveBeenCalledWith(CHILD_PROJECT.name, ROOT_PROJECT.id);
  });

  it('FE-06 и FE-10: ограничивает глубину и закрывает меню по Escape', async () => {
    vi.spyOn(navigationTreeService, 'getTree').mockResolvedValue({
      items: [{ ...ROOT_PROJECT, depth: 7 }],
      nextCursor: null,
    });
    const create = vi.spyOn(branchService, 'createBranch');
    renderTree();

    await screen.findByText(ROOT_PROJECT.name);
    const addButton = screen.getByRole('button', { name: NAVIGATION_TREE_STRINGS.projectActions(ROOT_PROJECT.name) });
    fireEvent.click(addButton);
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('menu')).toBeNull();
    await waitFor(() => expect(document.activeElement).toBe(addButton));

    fireEvent.click(addButton);
    fireEvent.click(screen.getByRole('menuitem', { name: NAVIGATION_TREE_STRINGS.newChildProject }));
    expect(await screen.findByText(NAVIGATION_TREE_STRINGS.depthLimit)).toBeTruthy();
    expect(create).not.toHaveBeenCalled();
  });

  it('FE-09: догружает корневую страницу без дубликатов', async () => {
    vi.spyOn(navigationTreeService, 'getTree')
      .mockResolvedValueOnce({ items: [ROOT_PROJECT], nextCursor: 'next-page' })
      .mockResolvedValueOnce({ items: [ROOT_PROJECT, CHILD_PROJECT], nextCursor: null });
    renderTree();

    await screen.findByText(ROOT_PROJECT.name);
    fireEvent.click(screen.getByRole('button', { name: NAVIGATION_TREE_STRINGS.loadMore }));
    expect(await screen.findByText(CHILD_PROJECT.name)).toBeTruthy();
    expect(screen.getAllByText(ROOT_PROJECT.name)).toHaveLength(1);
  });

  it('показывает существующие корневые проекты через совместимый endpoint при 404 дерева', async () => {
    vi.spyOn(navigationTreeService, 'getTree').mockRejectedValue(new ApiError(404));
    vi.spyOn(branchService, 'getBranches').mockResolvedValue({
      items: [
        {
          id: ROOT_PROJECT.id,
          name: ROOT_PROJECT.name,
          parentId: null,
          depth: 1,
          createdAt: '2026-09-17T10:00:00Z',
          updatedAt: '2026-09-17T10:00:00Z',
        },
        {
          id: CHILD_PROJECT.id,
          name: CHILD_PROJECT.name,
          parentId: ROOT_PROJECT.id,
          depth: 2,
          createdAt: '2026-09-17T10:00:00Z',
          updatedAt: '2026-09-17T10:00:00Z',
        },
      ],
      nextCursor: null,
    });

    renderTree();

    expect(await screen.findByText(ROOT_PROJECT.name)).toBeTruthy();
    expect(screen.getByRole('button', { name: NAVIGATION_TREE_STRINGS.expandProject(ROOT_PROJECT.name) })).toBeTruthy();
  });

  it('не отправляет cursor совместимого endpoint обратно в navigation API', async () => {
    vi.spyOn(navigationTreeService, 'getTree').mockRejectedValue(new ApiError(404));
    const secondRoot = { ...ROOT_PROJECT, id: '33333333-3333-4333-8333-333333333333', name: 'Личный проект', parentId: null };
    const branches = vi.spyOn(branchService, 'getBranches')
      .mockResolvedValueOnce({ items: [{ ...ROOT_PROJECT, createdAt: '2026-09-17T10:00:00Z', updatedAt: '2026-09-17T10:00:00Z' }], nextCursor: 'branches-cursor' })
      .mockResolvedValueOnce({ items: [{ ...secondRoot, createdAt: '2026-09-17T10:00:00Z', updatedAt: '2026-09-17T10:00:00Z' }], nextCursor: null });

    renderTree();

    await screen.findByText(ROOT_PROJECT.name);
    fireEvent.click(screen.getByRole('button', { name: NAVIGATION_TREE_STRINGS.loadMore }));
    await screen.findByText(secondRoot.name);
    expect(branches).toHaveBeenNthCalledWith(2, { limit: 500, cursor: 'branches-cursor' }, expect.any(AbortSignal));
    expect(navigationTreeService.getTree).toHaveBeenCalledTimes(1);
  });

  it('не показывает техническую валидацию, если navigation API вернул 400', async () => {
    vi.spyOn(navigationTreeService, 'getTree').mockRejectedValue(new ApiError(400));
    vi.spyOn(branchService, 'getBranches').mockResolvedValue({
      items: [{
        id: ROOT_PROJECT.id,
        name: ROOT_PROJECT.name,
        parentId: null,
        depth: 1,
        createdAt: '2026-09-17T10:00:00Z',
        updatedAt: '2026-09-17T10:00:00Z',
      }],
      nextCursor: null,
    });

    renderTree();

    expect(await screen.findByText(ROOT_PROJECT.name)).toBeTruthy();
    expect(screen.queryByText('Проверьте введённые данные')).toBeNull();
  });
});

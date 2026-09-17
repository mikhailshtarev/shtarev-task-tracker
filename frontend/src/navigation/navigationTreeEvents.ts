export const NAVIGATION_TREE_CHANGED_EVENT = 'navigation-tree-changed';

export function removeNavigationTreeNode(nodeId: string) {
  window.dispatchEvent(new CustomEvent(NAVIGATION_TREE_CHANGED_EVENT, { detail: { nodeId } }));
}

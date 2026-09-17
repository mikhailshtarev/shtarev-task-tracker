package ru.shatrev.tasks.navigation;

import java.util.UUID;

public record TreeNode(UUID id, String type, UUID parentId, String name, short depth, boolean hasChildren) {
}

package ru.shatrev.tasks.navigation;

import java.util.List;

public record TreePage(List<TreeNode> items, String nextCursor) {
}

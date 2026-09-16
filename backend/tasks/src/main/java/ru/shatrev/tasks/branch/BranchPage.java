package ru.shatrev.tasks.branch;

import java.util.List;

public record BranchPage(List<BranchResponse> items, String nextCursor) {
}

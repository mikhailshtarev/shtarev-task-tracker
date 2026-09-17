package ru.shatrev.tasks.navigation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shatrev.tasks.branch.ApiFailure;
import ru.shatrev.tasks.branch.BranchRepository;

import java.util.List;
import java.util.UUID;

@Service
public class TreeService {
    private final TreeReader reader;
    private final BranchRepository branches;

    public TreeService(TreeReader reader, BranchRepository branches) {
        this.reader = reader;
        this.branches = branches;
    }

    @Transactional(readOnly = true)
    public TreePage page(UUID userId, String parentType, String rawParentId, String rawLimit, String rawCursor) {
        if ((parentType == null) != (rawParentId == null)) {
            throw ApiFailure.validation("parentId", "Укажите тип и идентификатор родителя вместе");
        }
        if (parentType != null && !parentType.equals("branch") && !parentType.equals("work_plan")) {
            throw ApiFailure.validation("parentType", "Некорректный тип родителя");
        }
        UUID parentId = rawParentId == null ? null : parseId(rawParentId);
        int limit = parseLimit(rawLimit);
        TreeCursor cursor = TreeCursor.decode(rawCursor, userId, parentType, parentId);
        List<TreeReader.Row> rows;
        if (parentType == null) {
            rows = reader.root(userId, cursor, limit + 1);
        } else if (parentType.equals("branch")) {
            branches.findByIdAndUserIdAndArchivedAtIsNull(parentId, userId)
                    .orElseThrow(ApiFailure::resourceNotFound);
            rows = reader.children(userId, parentId, cursor, limit + 1);
        } else {
            if (!reader.activePlan(userId, parentId)) throw ApiFailure.resourceNotFound();
            rows = List.of(); // Task children become available when F-6 introduces the tasks table.
        }
        boolean more = rows.size() > limit;
        List<TreeReader.Row> visible = more ? rows.subList(0, limit) : rows;
        String next = null;
        if (more) {
            TreeReader.Row last = visible.getLast();
            next = new TreeCursor(last.rank(), last.createdAt(), last.node().id())
                    .encode(userId, parentType, parentId);
        }
        return new TreePage(visible.stream().map(TreeReader.Row::node).toList(), next);
    }

    private static UUID parseId(String raw) {
        try {
            UUID id = UUID.fromString(raw);
            if (id.toString().equals(raw)) return id;
        } catch (IllegalArgumentException ignored) {
        }
        throw ApiFailure.validation("parentId", "Некорректный идентификатор родителя");
    }

    private static int parseLimit(String raw) {
        if (raw == null) return 500;
        if (!raw.matches("[1-9][0-9]{0,2}") || Integer.parseInt(raw) > 500) {
            throw ApiFailure.validation("limit", "Лимит должен быть целым числом от 1 до 500");
        }
        return Integer.parseInt(raw);
    }
}

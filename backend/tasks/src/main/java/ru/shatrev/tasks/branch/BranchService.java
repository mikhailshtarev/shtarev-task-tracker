package ru.shatrev.tasks.branch;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shatrev.tasks.workplan.WorkPlanCascade;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BranchService {
    private final BranchRepository branches;
    private final BranchPageReader pages;
    private final BranchAudit audit;
    private final WorkPlanCascade workPlans;

    public BranchService(BranchRepository branches, BranchPageReader pages, BranchAudit audit,
                         WorkPlanCascade workPlans) {
        this.branches = branches;
        this.pages = pages;
        this.audit = audit;
        this.workPlans = workPlans;
    }

    @Transactional(readOnly = true)
    public BranchPage list(UUID userId, String rawQuery, String rawLimit, String rawCursor) {
        String query = BranchText.query(rawQuery);
        int limit = parseLimit(rawLimit);
        BranchCursor cursor = BranchCursor.decode(rawCursor, query);
        List<BranchResponse> rows = pages.read(userId, query, cursor, limit + 1);
        boolean more = rows.size() > limit;
        List<BranchResponse> items = more ? rows.subList(0, limit) : rows;
        String next = more ? new BranchCursor(items.getLast().createdAt(), items.getLast().id()).encode(query) : null;
        return new BranchPage(items, next);
    }

    @Transactional
    public BranchResponse create(UUID userId, BranchInput input) {
        String name = BranchText.name(input == null ? null : input.name());
        Instant now = now();
        Branch branch = branches.saveAndFlush(new Branch(userId, name, now));
        audit.record(userId, branch.getId(), "created", "name", null, name, now);
        return BranchResponse.from(branch);
    }

    @Transactional(readOnly = true)
    public BranchResponse get(UUID userId, UUID id) {
        return BranchResponse.from(branches.findByIdAndUserIdAndArchivedAtIsNull(id, userId)
                .orElseThrow(ApiFailure::notFound));
    }

    @Transactional
    public BranchResponse rename(UUID userId, UUID id, BranchInput input) {
        String name = BranchText.name(input == null ? null : input.name());
        Branch branch = branches.lockOwned(id, userId).orElseThrow(ApiFailure::notFound);
        if (branch.getArchivedAt() != null) throw ApiFailure.notFound();
        if (!branch.getName().equals(name)) {
            String old = branch.getName();
            Instant now = now();
            branch.rename(name, now);
            audit.record(userId, id, "renamed", "name", old, name, now);
        }
        return BranchResponse.from(branch);
    }

    @Transactional
    public void archive(UUID userId, UUID id) {
        Branch branch = branches.lockOwned(id, userId).orElseThrow(ApiFailure::notFound);
        if (branch.getArchivedAt() == null) {
            Instant now = now();
            branch.archive(now);
            workPlans.archiveActive(userId, id, now);
            audit.record(userId, id, "archived", "archivedAt", null, now.toString(), now);
        }
    }

    private static int parseLimit(String raw) {
        if (raw == null) return 50;
        if (!raw.matches("[1-9][0-9]?") || Integer.parseInt(raw) > 50) {
            throw ApiFailure.validation("limit", "Лимит должен быть целым числом от 1 до 50");
        }
        return Integer.parseInt(raw);
    }

    private static Instant now() {
        // PostgreSQL timestamptz has microsecond precision; responses and audit then match persisted values.
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}

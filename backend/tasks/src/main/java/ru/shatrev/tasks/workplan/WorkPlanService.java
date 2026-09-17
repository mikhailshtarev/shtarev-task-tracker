package ru.shatrev.tasks.workplan;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shatrev.tasks.branch.ApiFailure;
import ru.shatrev.tasks.branch.Branch;
import ru.shatrev.tasks.branch.BranchRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class WorkPlanService {
    private final BranchRepository branches;
    private final WorkPlanRepository plans;
    private final WorkPlanReader reader;
    private final WorkPlanAudit audit;

    public WorkPlanService(BranchRepository branches, WorkPlanRepository plans,
                           WorkPlanReader reader, WorkPlanAudit audit) {
        this.branches = branches;
        this.plans = plans;
        this.reader = reader;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public WorkPlanPage list(UUID userId, UUID branchId, String rawQuery, String rawLimit, String rawCursor) {
        activeBranch(userId, branchId);
        String query = WorkPlanText.query(rawQuery);
        int limit = parseLimit(rawLimit);
        WorkPlanCursor cursor = WorkPlanCursor.decode(rawCursor, branchId, query);
        List<WorkPlanResponse> rows = reader.page(userId, branchId, query, cursor, limit + 1);
        boolean more = rows.size() > limit;
        List<WorkPlanResponse> items = more ? rows.subList(0, limit) : rows;
        String next = more
                ? new WorkPlanCursor(branchId, items.getLast().createdAt(), items.getLast().id()).encode(query)
                : null;
        return new WorkPlanPage(items, next);
    }

    @Transactional
    public WorkPlanResponse create(UUID userId, UUID branchId, WorkPlanInput input) {
        String name = WorkPlanText.name(input == null ? null : input.name());
        lockActiveBranch(userId, branchId);
        Instant now = now();
        WorkPlan plan = plans.saveAndFlush(new WorkPlan(branchId, name, now));
        audit.created(userId, plan.getId(), branchId, name, now);
        return WorkPlanResponse.from(plan);
    }

    @Transactional(readOnly = true)
    public WorkPlanResponse get(UUID userId, UUID planId) {
        return reader.active(userId, planId).orElseThrow(ApiFailure::resourceNotFound);
    }

    @Transactional
    public WorkPlanResponse rename(UUID userId, UUID planId, WorkPlanInput input) {
        String name = WorkPlanText.name(input == null ? null : input.name());
        WorkPlan plan = lockOwnedPlan(userId, planId);
        if (plan.getArchivedAt() != null) throw ApiFailure.resourceNotFound();
        if (!plan.getName().equals(name)) {
            String old = plan.getName();
            Instant now = now();
            plan.rename(name, now);
            audit.renamed(userId, planId, old, name, now);
        }
        return WorkPlanResponse.from(plan);
    }

    @Transactional
    public void archive(UUID userId, UUID planId) {
        WorkPlan plan = lockOwnedPlan(userId, planId);
        if (plan.getArchivedAt() == null) {
            Instant now = now();
            plan.archive(now);
            audit.archived(userId, List.of(planId), now);
        }
    }

    private Branch activeBranch(UUID userId, UUID branchId) {
        return branches.findByIdAndUserIdAndArchivedAtIsNull(branchId, userId)
                .orElseThrow(ApiFailure::resourceNotFound);
    }

    private Branch lockActiveBranch(UUID userId, UUID branchId) {
        Branch branch = branches.lockOwned(branchId, userId).orElseThrow(ApiFailure::resourceNotFound);
        if (branch.getArchivedAt() != null) throw ApiFailure.resourceNotFound();
        return branch;
    }

    private WorkPlan lockOwnedPlan(UUID userId, UUID planId) {
        UUID branchId = reader.branchIdOf(planId).orElseThrow(ApiFailure::resourceNotFound);
        lockActiveBranch(userId, branchId);
        return plans.lockInBranch(planId, branchId).orElseThrow(ApiFailure::resourceNotFound);
    }

    private static int parseLimit(String raw) {
        if (raw == null) return 50;
        if (!raw.matches("[1-9][0-9]?") || Integer.parseInt(raw) > 50) {
            throw ApiFailure.validation("limit", "Лимит должен быть целым числом от 1 до 50");
        }
        return Integer.parseInt(raw);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}

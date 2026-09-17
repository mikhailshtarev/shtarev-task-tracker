package ru.shatrev.tasks.workplan;

import org.junit.jupiter.api.Test;
import ru.shatrev.tasks.branch.ApiFailure;
import ru.shatrev.tasks.branch.Branch;
import ru.shatrev.tasks.branch.BranchRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkPlanServiceTest {
    private final BranchRepository branches = mock(BranchRepository.class);
    private final WorkPlanRepository plans = mock(WorkPlanRepository.class);
    private final WorkPlanReader reader = mock(WorkPlanReader.class);
    private final WorkPlanAudit audit = mock(WorkPlanAudit.class);
    private final WorkPlanService service = new WorkPlanService(branches, plans, reader, audit);

    @Test
    void writeLocksParentFirstAndNoOpDoesNotAudit() {
        UUID owner = UUID.randomUUID();
        Branch branch = new Branch(owner, "Ветка", Instant.parse("2026-09-17T09:00:00Z"));
        WorkPlan plan = new WorkPlan(branch.getId(), "План", Instant.parse("2026-09-17T10:00:00Z"));
        when(reader.branchIdOf(plan.getId())).thenReturn(Optional.of(branch.getId()));
        when(branches.lockOwned(branch.getId(), owner)).thenReturn(Optional.of(branch));
        when(plans.lockInBranch(plan.getId(), branch.getId())).thenReturn(Optional.of(plan));

        Instant original = plan.getUpdatedAt();
        assertEquals("План", service.rename(owner, plan.getId(), new WorkPlanInput(" План ")).name());
        assertEquals(original, plan.getUpdatedAt());
        verifyNoInteractions(audit);
        var order = inOrder(reader, branches, plans);
        order.verify(reader).branchIdOf(plan.getId());
        order.verify(branches).lockOwned(branch.getId(), owner);
        order.verify(plans).lockInBranch(plan.getId(), branch.getId());

        service.archive(owner, plan.getId());
        Instant archivedAt = plan.getArchivedAt();
        service.archive(owner, plan.getId());
        assertEquals(archivedAt, plan.getArchivedAt());
        verify(audit, times(1)).archived(owner, List.of(plan.getId()), archivedAt);
    }

    @Test
    void archivedOrForeignBranchBlocksPlanWrites() {
        UUID owner = UUID.randomUUID();
        Branch branch = new Branch(owner, "Ветка", Instant.now());
        WorkPlan plan = new WorkPlan(branch.getId(), "План", Instant.now());
        when(reader.branchIdOf(plan.getId())).thenReturn(Optional.of(branch.getId()));
        when(branches.lockOwned(branch.getId(), owner)).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", assertThrows(ApiFailure.class,
                () -> service.archive(owner, plan.getId())).code());

        branch.archive(Instant.now());
        when(branches.lockOwned(branch.getId(), owner)).thenReturn(Optional.of(branch));
        assertEquals("NOT_FOUND", assertThrows(ApiFailure.class,
                () -> service.rename(owner, plan.getId(), new WorkPlanInput("Другой"))).code());
        assertEquals("NOT_FOUND", assertThrows(ApiFailure.class,
                () -> service.archive(owner, plan.getId())).code());
        verifyNoInteractions(plans, audit);
    }

    @Test
    void pageReadsLimitPlusOneWithinOwnedBranch() {
        UUID owner = UUID.randomUUID();
        Branch branch = new Branch(owner, "Ветка", Instant.now());
        Instant time = Instant.parse("2026-09-17T10:00:00Z");
        var first = new WorkPlanResponse(UUID.randomUUID(), branch.getId(), "Первый", time, time);
        var extra = new WorkPlanResponse(UUID.randomUUID(), branch.getId(), "Второй", time.minusSeconds(1), time);
        when(branches.findByIdAndUserIdAndArchivedAtIsNull(branch.getId(), owner)).thenReturn(Optional.of(branch));
        when(reader.page(eq(owner), eq(branch.getId()), eq("q"), isNull(), eq(2)))
                .thenReturn(List.of(first, extra));
        WorkPlanPage page = service.list(owner, branch.getId(), " q ", "1", null);
        assertEquals(List.of(first), page.items());
        assertEquals(new WorkPlanCursor(branch.getId(), time, first.id()),
                WorkPlanCursor.decode(page.nextCursor(), branch.getId(), "q"));
    }
}

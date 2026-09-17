package ru.shatrev.tasks.branch;

import org.junit.jupiter.api.Test;
import ru.shatrev.tasks.workplan.WorkPlanCascade;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BranchServiceTest {
    private final BranchRepository repository = mock(BranchRepository.class);
    private final BranchPageReader pages = mock(BranchPageReader.class);
    private final BranchAudit audit = mock(BranchAudit.class);
    private final WorkPlanCascade cascade = mock(WorkPlanCascade.class);
    private final BranchHierarchy hierarchy = mock(BranchHierarchy.class);
    private final BranchService service = new BranchService(repository, pages, audit, cascade, hierarchy);

    @Test
    void noOpRenameAndRepeatedArchiveDoNotUpdateOrAudit() {
        UUID owner = UUID.randomUUID();
        Branch branch = new Branch(owner, "Спорт", Instant.parse("2026-09-16T10:00:00Z"));
        when(repository.lockOwned(branch.getId(), owner)).thenReturn(Optional.of(branch));
        when(hierarchy.lockSubtree(owner, branch.getId())).thenReturn(List.of(branch.getId()));
        Instant original = branch.getUpdatedAt();
        assertEquals("Спорт", service.rename(owner, branch.getId(), new BranchInput("Спорт")).name());
        assertEquals(original, branch.getUpdatedAt());
        verifyNoInteractions(audit);

        service.archive(owner, branch.getId());
        Instant archivedAt = branch.getArchivedAt();
        service.archive(owner, branch.getId());
        assertEquals(archivedAt, branch.getArchivedAt());
        verify(cascade, times(1)).archiveActive(owner, List.of(branch.getId()), archivedAt);
        verify(audit, times(1)).record(eq(owner), eq(branch.getId()), eq("archived"),
                eq("archivedAt"), isNull(), anyString(), any(Instant.class));
        assertThrows(ApiFailure.class,
                () -> service.rename(owner, branch.getId(), new BranchInput("Здоровье")));
    }

    @Test
    void paginationUsesLimitPlusOneAndLastVisibleCursor() {
        UUID owner = UUID.randomUUID();
        Instant time = Instant.parse("2026-09-16T10:00:00Z");
        var first = new BranchResponse(UUID.randomUUID(), "А", time, time);
        var second = new BranchResponse(UUID.randomUUID(), "Б", time.minusSeconds(1), time);
        when(pages.read(eq(owner), eq("q"), isNull(), eq(2))).thenReturn(List.of(first, second));
        BranchPage page = service.list(owner, " q ", "1", null);
        assertEquals(List.of(first), page.items());
        assertEquals(new BranchCursor(first.createdAt(), first.id()), BranchCursor.decode(page.nextCursor(), "q"));
    }

    @Test
    void foreignBranchIsNotDisclosed() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(repository.lockOwned(id, owner)).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", assertThrows(ApiFailure.class, () -> service.archive(owner, id)).code());
    }

    @Test
    void childUsesLockedParentAndRejectsEighthLevel() {
        UUID owner = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        when(hierarchy.lockActiveParent(owner, parent)).thenReturn((short) 3);
        when(repository.saveAndFlush(any(Branch.class))).thenAnswer(call -> call.getArgument(0));
        BranchResponse created = service.create(owner, new BranchInput("Дочерняя", parent));
        assertEquals(parent, created.parentId());
        assertEquals(3, created.depth());
        verify(hierarchy).lockActiveParent(owner, parent);

        when(hierarchy.lockActiveParent(owner, parent))
                .thenThrow(ApiFailure.validation("parentId", "Максимальная глубина проектов — 7 уровней"));
        assertEquals("VALIDATION_ERROR", assertThrows(ApiFailure.class,
                () -> service.create(owner, new BranchInput("Глубоко", parent))).code());
    }

    @Test
    void archiveAuditsOnlyActiveDescendants() {
        UUID owner = UUID.randomUUID();
        Branch root = new Branch(owner, "Корень", Instant.now());
        UUID active = UUID.randomUUID();
        UUID alreadyArchived = UUID.randomUUID();
        when(repository.lockOwned(root.getId(), owner)).thenReturn(Optional.of(root));
        when(hierarchy.lockSubtree(owner, root.getId())).thenReturn(List.of(root.getId(), active, alreadyArchived));
        when(hierarchy.archiveActiveDescendants(anyList(), eq(root.getId()), any(Instant.class)))
                .thenReturn(List.of(active));
        service.archive(owner, root.getId());
        verify(cascade).archiveActive(eq(owner), eq(List.of(root.getId(), active, alreadyArchived)), any());
        verify(audit).record(eq(owner), eq(active), eq("archived"), anyString(), isNull(), anyString(), any());
        verify(audit, never()).record(eq(owner), eq(alreadyArchived), anyString(), anyString(), any(), any(), any());
    }
}

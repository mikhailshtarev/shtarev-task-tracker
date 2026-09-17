package ru.shatrev.tasks.navigation;

import org.junit.jupiter.api.Test;
import ru.shatrev.tasks.branch.ApiFailure;
import ru.shatrev.tasks.branch.BranchRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TreeServiceTest {
    private final TreeReader reader = mock(TreeReader.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final TreeService service = new TreeService(reader, branches);

    @Test
    void rootPageAndCursorAreBoundToOwnerAndParent() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Instant time = Instant.parse("2026-09-17T10:00:00Z");
        var first = new TreeReader.Row(new TreeNode(UUID.randomUUID(), "branch", null, "Первый", (short) 1, true),
                0, time);
        var second = new TreeReader.Row(new TreeNode(UUID.randomUUID(), "branch", null, "Второй", (short) 1, false),
                0, time.minusSeconds(1));
        when(reader.root(eq(owner), isNull(), eq(2))).thenReturn(List.of(first, second));
        TreePage page = service.page(owner, null, null, "1", null);
        assertEquals(List.of(first.node()), page.items());
        assertNotNull(page.nextCursor());
        assertEquals("VALIDATION_ERROR", assertThrows(ApiFailure.class,
                () -> service.page(other, null, null, "1", page.nextCursor())).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ApiFailure.class,
                () -> service.page(owner, "branch", UUID.randomUUID().toString(), "1", page.nextCursor())).code());
    }

    @Test
    void invalidContextAndForeignParentAreRejected() {
        UUID owner = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        assertEquals("VALIDATION_ERROR", assertThrows(ApiFailure.class,
                () -> service.page(owner, "branch", null, null, null)).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ApiFailure.class,
                () -> service.page(owner, "task", parent.toString(), null, null)).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ApiFailure.class,
                () -> service.page(owner, null, null, "501", null)).code());
        assertEquals("NOT_FOUND", assertThrows(ApiFailure.class,
                () -> service.page(owner, "branch", parent.toString(), null, null)).code());
        verify(reader, never()).children(any(), any(), any(), anyInt());
    }

    @Test
    void activePlanCanBeExpandedWithoutTasksTable() {
        UUID owner = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        when(reader.activePlan(owner, plan)).thenReturn(true);
        assertEquals(List.of(), service.page(owner, "work_plan", plan.toString(), null, null).items());
        assertNull(service.page(owner, "work_plan", plan.toString(), null, null).nextCursor());
    }
}

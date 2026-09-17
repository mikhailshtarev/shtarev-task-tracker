package ru.shatrev.tasks.workplan;

import org.junit.jupiter.api.Test;
import ru.shatrev.tasks.branch.ApiFailure;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkPlanTextCursorTest {
    @Test
    void usesF4UnicodeNormalizationAndCodePointBounds() {
        assertEquals("é😀", WorkPlanText.name("\u00A0e\u0301😀\u2003"));
        assertEquals("😀".repeat(150), WorkPlanText.name("😀".repeat(150)));
        assertThrows(ApiFailure.class, () -> WorkPlanText.name("😀"));
        assertThrows(ApiFailure.class, () -> WorkPlanText.name("😀".repeat(151)));
        assertEquals("", WorkPlanText.query("\u00A0\u2003"));
        assertThrows(ApiFailure.class, () -> WorkPlanText.query("q".repeat(151)));
    }

    @Test
    void cursorBindsCanonicalBranchAndCaseSensitiveQuery() {
        UUID branch = UUID.randomUUID();
        var cursor = new WorkPlanCursor(branch, Instant.parse("2026-09-17T10:00:00Z"), UUID.randomUUID());
        String encoded = cursor.encode("Test");
        assertEquals(cursor, WorkPlanCursor.decode(encoded, branch, "Test"));
        assertEquals("cursor", assertThrows(ApiFailure.class,
                () -> WorkPlanCursor.decode(encoded, UUID.randomUUID(), "Test")).details().getFirst().field());
        assertThrows(ApiFailure.class, () -> WorkPlanCursor.decode(encoded, branch, "test"));
        assertThrows(ApiFailure.class, () -> WorkPlanCursor.decode("broken", branch, "Test"));
    }
}

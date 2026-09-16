package ru.shatrev.tasks.branch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BranchTextTest {
    @Test
    void normalizesNfcAndOnlySpecifiedEdgeWhitespace() {
        assertEquals("é😀", BranchText.name("\u00A0e\u0301😀\u2003"));
        assertEquals("a\u200Bb", BranchText.name("\uFEFFa\u200Bb\u3000"));
        assertEquals("", BranchText.query("\u00A0\u2003"));
    }

    @Test
    void countsCodePointsAndRejectsInvalidLengths() {
        assertEquals("😀😀", BranchText.name("😀😀"));
        assertThrows(ApiFailure.class, () -> BranchText.name("😀"));
        assertEquals(100, BranchText.name("😀".repeat(100)).codePointCount(0, 200));
        assertThrows(ApiFailure.class, () -> BranchText.name("😀".repeat(101)));
        assertThrows(ApiFailure.class, () -> BranchText.query("q".repeat(101)));
    }

    @Test
    void cursorIsBoundToExactNormalizedQuery() {
        BranchCursor cursor = new BranchCursor(java.time.Instant.parse("2026-09-16T10:00:00Z"),
                java.util.UUID.randomUUID());
        String token = cursor.encode("Test");
        assertEquals(cursor, BranchCursor.decode(token, "Test"));
        assertThrows(ApiFailure.class, () -> BranchCursor.decode(token, "test"));
        assertThrows(ApiFailure.class, () -> BranchCursor.decode("broken", "Test"));
    }
}

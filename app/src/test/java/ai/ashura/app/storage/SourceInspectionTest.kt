package ai.ashura.app.storage

import org.junit.Assert.*
import org.junit.Test

class SourceInspectionTest {
    @Test fun streamedWindowsHandleBoundariesAndClipLongLines() {
        val source = "skip\r\n" + "x".repeat(2000) + "\rnext\n"
        val window = SourceInspection.window(source.reader(), 2, 2)
        assertEquals("x".repeat(512) + "\nnext", window.getString("content"))
        assertTrue(window.getBoolean("has_more"))
        assertTrue(window.getBoolean("long_lines_clipped"))
        val tail = SourceInspection.window(source.reader(), 4, 1)
        assertFalse(tail.getBoolean("has_more"))
        assertEquals(4, tail.getInt("total_lines"))
        assertTrue(runCatching { SourceInspection.window(source.reader(), 5, 1) }.isFailure)
    }

    @Test fun successfulExitAloneCannotVerifyDecodeOutput() {
        assertEquals("failed", SourceInspection.decodeStatus(0, 0, true))
        assertEquals("complete", SourceInspection.decodeStatus(0, 8, true))
        assertEquals("partial", SourceInspection.decodeStatus(-1, 8, true))
        assertEquals("unverified", SourceInspection.decodeStatus(0, 8, false))
    }

    @Test fun windowsUseEditorLineNumbers() {
        val window = SourceInspection.lines("first\r\nsecond\r\nthird", 2, 60)
        assertEquals("second\nthird", window.getString("content"))
        assertEquals(3, window.getInt("end_line"))
        assertEquals(3, window.getInt("total_lines"))
        assertTrue(runCatching { SourceInspection.lines("one", 2, 60) }.isFailure)
        assertTrue(runCatching { SourceInspection.lines("one", 1, 201) }.isFailure)
    }

    @Test fun presenceAndAbsenceAreCaseSensitive() {
        val content = "Before\r\nAfter"
        assertEquals(2, SourceInspection.check(content, "After", true).getInt("line"))
        assertTrue(SourceInspection.check(content, "after", false).getBoolean("passed"))
        assertFalse(SourceInspection.check(content, "After", false).getBoolean("passed"))
        assertFalse(SourceInspection.check(content, "after", true).getBoolean("passed"))
    }
}

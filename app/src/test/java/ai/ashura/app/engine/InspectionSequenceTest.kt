package ai.ashura.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionSequenceTest {
    @Test fun sourceReviewContinuesPastTheOldLimit() {
        val sequence = InspectionSequence()
        repeat(100) {
            assertFalse(sequence.shouldStopAfter("list_files"))
            assertFalse(sequence.shouldStopAfter("search_text"))
            assertFalse(sequence.shouldStopAfter("read_file"))
        }
    }

    @Test fun mixedShellAndFileSequenceStillStops() {
        val sequence = InspectionSequence()
        assertFalse(sequence.shouldStopAfter("shell_exec"))
        repeat(23) { assertFalse(sequence.shouldStopAfter("read_file")) }
        assertTrue(sequence.shouldStopAfter("read_file"))
    }
}

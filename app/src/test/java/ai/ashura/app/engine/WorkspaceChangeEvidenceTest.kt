package ai.ashura.app.engine

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceChangeEvidenceTest {
    private val args = JSONObject().put("path", "src/Main.kt")

    @Test fun onlySuccessfulMutationOfTheRequestedPathCounts() {
        val evidence = WorkspaceChangeEvidence()
        val edited = JSONObject().put("path", "src/Main.kt").put("status", "edited")
        evidence.record("read_file", args, edited)
        evidence.record("edit_file", args, JSONObject().put("path", "other.kt").put("status", "edited"))
        assertFalse(evidence.hasVerifiedChange)
        evidence.record("edit_file", args, edited)
        assertTrue(evidence.hasVerifiedChange)
        assertTrue(evidence.summary().contains("src/Main.kt"))
    }

    @Test fun denialAndNoOpAreNotChanges() {
        val evidence = WorkspaceChangeEvidence()
        evidence.record("write_file", args, JSONObject().put("path", "src/Main.kt").put("status", "unchanged"))
        assertTrue(evidence.hasMatchingFile)
        evidence.record("edit_file", args, JSONObject().put("error", "Denied").put("denied", true))
        assertTrue(evidence.denied)
        assertFalse(evidence.hasVerifiedChange)
    }

    @Test fun continueInheritsTheLatestTask() {
        assertTrue(WorkspaceChangeEvidence.continuedChange(listOf("Fix the import", "continue", "continue kro")))
        assertFalse(WorkspaceChangeEvidence.continuedChange(listOf("Fix the import", "Review the code", "continue")))
        assertTrue(WorkspaceChangeEvidence.claimed("MainActivity.java mein import registration confirm ki:"))
    }

    @Test fun distinguishesChangeRequestsFromReadOnlyReviews() {
        assertTrue(WorkspaceChangeEvidence.requested("thora changes kro header mein"))
        assertTrue(WorkspaceChangeEvidence.requested("Update the header spacing"))
        assertFalse(WorkspaceChangeEvidence.requested("Review the header and suggest changes"))
        assertTrue(WorkspaceChangeEvidence.claimed("I updated the header."))
        assertTrue(WorkspaceChangeEvidence.claimed("Done."))
        assertFalse(WorkspaceChangeEvidence.claimed("Here is a suggestion for the header."))
    }
}

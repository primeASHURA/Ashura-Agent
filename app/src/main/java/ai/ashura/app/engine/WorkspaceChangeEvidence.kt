package ai.ashura.app.engine

import org.json.JSONObject

internal class WorkspaceChangeEvidence {
    private val changed = linkedSetOf<String>()
    private val unchanged = linkedSetOf<String>()
    var denied = false
        private set

    fun record(tool: String, arguments: JSONObject, result: JSONObject?) {
        val statuses = mutationStatuses[tool] ?: return
        if (result == null) return
        if (result.optBoolean("denied")) denied = true
        if (result.has("error")) return
        val path = result.optString("path")
        val target = arguments.optString(if (tool == "move_path") "to" else "path")
        if (path.isBlank() || path != target) return
        val status = result.optString("status")
        if (status in statuses) changed.add(path)
        if (status == "unchanged" || status == "already_exists") unchanged.add(path)
    }

    val hasVerifiedChange: Boolean get() = changed.isNotEmpty()
    val hasMatchingFile: Boolean get() = unchanged.isNotEmpty()

    fun summary(): String = when {
        hasVerifiedChange -> "Workspace changes reported by tools: " + changed.joinToString(", ") { "`$it`" }
        denied -> "The workspace change was denied. No file change was verified."
        hasMatchingFile -> "No files were changed. The requested content or destination was already present."
        else -> "No file change was verified. The requested update is still pending."
    }

    companion object {
        private val mutationStatuses = mapOf(
            "replace_asset" to setOf("updated"),
            "write_block" to setOf("updated"),
            "write_file" to setOf("written"),
            "edit_file" to setOf("edited"),
            "create_directory" to setOf("created"),
            "move_path" to setOf("moved"),
            "delete_path" to setOf("file_deleted", "folder_deleted")
        )

        private val requestAction = Regex(
            """(?:^|\b(?:please|ab|isko|ye|thoda|thora|and)\s+)(?:fix|edit|modify|update|change|improve|replace|implement|refactor|add|remove|rename|move|delete|create|write|build)\b|\b(?:fix|edit|update|changes?|modify|improve|implement|add|remove)\s+(?:kar|kr|karo|kro|karna|krna)\b""",
            RegexOption.IGNORE_CASE
        )
        private val advisory = Regex(
            """^\s*(?:how\b|what\b|why\b|explain\b|suggest\b|review\b|analy[sz]e\b|plan\b|show\s+me\b)|\b(?:read[- ]only|don.t (?:edit|change|modify)|without (?:editing|changing))\b""",
            RegexOption.IGNORE_CASE
        )
        private val completedClaim = Regex(
            """(?:\b(?:i(?: have|'ve)?|we(?: have|'ve)?)\s+(?:successfully\s+)?(?:updated|edited|modified|fixed|wrote|written|created|renamed|deleted|changed)\b|\b(?:changes? (?:are |have been )?(?:applied|saved)|(?:updated|edited|fixed|created|wrote)\s+(?:the |your )?(?:file|source|code)\b|(?:registration|import|change|fix)\s+confirm\s+(?:ki|kiya)|ho\s*gaya|(?:kar|kr)\s*di(?:ya|yaa)|(?:task|kaam)\s+(?:complete|done))\b|^\s*(?:done|completed)[.!\s]*(?:$|\n))""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )

        private val continuation = Regex(
            """(?:please\s+)?(?:continue|resume|carry on|go on|aage badho)(?:\s+(?:please|karo|kro))?[.!\s]*""",
            RegexOption.IGNORE_CASE
        )

        fun continuedChange(requests: List<String>): Boolean {
            val task = requests.lastOrNull { it.isNotBlank() && !continuation.matches(it.trim()) } ?: return false
            return requested(task)
        }
        fun requested(text: String): Boolean = !advisory.containsMatchIn(text) && requestAction.containsMatchIn(text)

        fun claimed(text: String): Boolean =
            completedClaim.containsMatchIn(text.replace(Regex("""(?s)```.*?(?:```|$)"""), ""))
    }
}

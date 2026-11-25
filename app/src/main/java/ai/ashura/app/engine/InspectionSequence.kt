package ai.ashura.app.engine

/** Preserves the command guard while allowing source reads to complete a review. */
internal class InspectionSequence {
    private var steps = 0
    private var includesCommand = false

    fun shouldStopAfter(tool: String): Boolean {
        when (tool) {
            "shell_exec", "ssh_exec" -> {
                includesCommand = true
                steps++
            }
            "grep", "glob", "list_files", "read_file", "read_lines", "check_source_text", "search_text", "apk_strings" -> steps++
            else -> {
                steps = 0
                includesCommand = false
            }
        }
        return includesCommand && steps >= 25
    }
}

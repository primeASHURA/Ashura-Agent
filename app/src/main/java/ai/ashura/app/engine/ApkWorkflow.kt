package ai.ashura.app.engine

internal enum class ApkWorkflow(val title: String) {
    INSPECT("Inspect without decompiling"),
    DECOMPILE("Decompile to source");

    fun allows(tool: String): Boolean = tool in sharedTools ||
        (this == DECOMPILE && tool in setOf("ssh_tools", "ssh_decompile"))

    companion object {
        private val sharedTools = setOf("list_files", "read_file", "read_lines", "search_text", "apk_manifest",
            "apk_overview", "apk_resources", "apk_compare", "apk_dex", "check_source_text", "plan_source_checks",
            "create_directory", "write_file", "edit_file")
        fun continuation(request: String, previous: String?): ApkWorkflow? {
            val follows = Regex("""\s*(?:ok(?:ay)?\s+)?(?:continue|proceed|carry on|aage badho|aage karo|continue kro|continue karo)[.!\s]*""", RegexOption.IGNORE_CASE)
            return entries.firstOrNull { it.name == previous && follows.matches(request) }
        }
        private val apkWork = Regex(
            """\b(?:apks?|jadx|apktool|decompil\w*|reverse[ -]?eng\w*|reverce[ -]?eng\w*)\b|\b(?:app|application)\b.{0,32}\b(?:clone|cloning|recreate|rebrand|duplicate)\b|\b(?:clone|cloning|recreate|rebrand|duplicate)\b.{0,32}\b(?:app|application)\b""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        fun needsChoice(request: String): Boolean {
            if (Regex("""^\s*git\s+clone\b""", RegexOption.IGNORE_CASE).containsMatchIn(request)) return false
            val packaging = Regex("""\b(?:build|install|sign|assemble|package)\b""", RegexOption.IGNORE_CASE)
            val inspection = Regex("""analy|inspect|compar|permission|manifest|resource|clone|cloning|recreat|revers|reverc|decompil""", RegexOption.IGNORE_CASE)
            return apkWork.containsMatchIn(request) && (!packaging.containsMatchIn(request) || inspection.containsMatchIn(request))
        }
    }
}

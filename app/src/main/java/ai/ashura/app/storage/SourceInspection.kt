package ai.ashura.app.storage

import org.json.JSONObject

internal object SourceInspection {
    fun decodeStatus(exit: Int, nonEmptySources: Int, scanComplete: Boolean): String = when {
        !scanComplete -> "unverified"
        nonEmptySources == 0 -> "failed"
        exit != 0 -> "partial"
        else -> "complete"
    }

    fun lines(content: String, start: Int, count: Int): JSONObject {
        require(start >= 1 && count in 1..200) { "Use a positive start_line and a line_count from 1 to 200" }
        val lines = content.lineSequence().toList()
        require(start <= lines.size) { "start_line is past the end of the file (${lines.size} lines)" }
        val selected = lines.drop(start - 1).take(count)
        val end = start - 1 + selected.size
        return JSONObject().put("start_line", start).put("end_line", end)
            .put("total_lines", lines.size).put("content", selected.joinToString("\n"))
    }

    /** Reads one window without storing skipped lines or unbounded minified text. */
    fun window(reader: java.io.Reader, start: Int, count: Int): JSONObject {
        require(start >= 1 && count in 1..200) { "Use a positive start_line and a line_count from 1 to 200" }
        val end = start.toLong() + count - 1
        val buffer = CharArray(8192)
        val content = StringBuilder()
        var line = 1
        var scanned = 0L
        var previousCR = false
        var width = 0
        var clipped = false
        fun result(more: Boolean) = JSONObject().put("start_line", start).put("end_line", minOf(line.toLong(), end))
            .put("content", content.toString()).put("has_more", more).put("long_lines_clipped", clipped)
            .apply { if (!more) put("total_lines", line) }
        while (true) {
            val size = reader.read(buffer)
            if (size < 0) break
            scanned += size
            require(scanned <= 32L * 1024 * 1024) { "Line window exceeds the 32 Mi-character scan limit" }
            for (i in 0 until size) {
                val c = buffer[i]
                require(c != '\u0000') { "Binary files are not supported" }
                if (previousCR && c == '\n') { previousCR = false; continue }
                previousCR = c == '\r'
                if (previousCR || c == '\n') {
                    if (line.toLong() == end) return result(true)
                    if (line >= start) content.append('\n')
                    line++
                    width = 0
                } else if (line >= start) {
                    if (width < 512) content.append(c) else clipped = true
                    width++
                }
            }
        }
        require(start <= line) { "start_line is past the end of the file ($line lines)" }
        return result(false)
    }

    fun check(content: String, expected: String, shouldExist: Boolean): JSONObject {
        require(expected.isNotEmpty()) { "Expected text must be non-empty" }
        val index = content.indexOf(expected)
        return JSONObject().put("check", "literal text").put("found", index >= 0)
            .put("passed", (index >= 0) == shouldExist)
            .apply { if (index >= 0) put("line", content.substring(0, index).lineSequence().count()) }
    }
}

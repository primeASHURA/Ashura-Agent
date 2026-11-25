package ai.ashura.app.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import ai.ashura.app.engine.WorkspaceTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

 data class WorkspaceEntry(val name: String, val path: String, val isDirectory: Boolean, val size: Long)

/** The caller persists the ACTION_OPEN_DOCUMENT_TREE grant before constructing this store. */
class WorkspaceStore(
    private val context: Context,
    private val treeUri: Uri,
    private val autoApprove: () -> Boolean = { false },
    private val approve: suspend (String) -> Boolean = { false }
) : WorkspaceTools {
    private val resolver = context.applicationContext.contentResolver
    private val root = requireNotNull(DocumentFile.fromTreeUri(context.applicationContext, treeUri)) { "Invalid workspace tree" }
    private val mutex = Mutex()
    private data class SavedEdit(val path: String, val uri: Uri, val before: String?, val after: ByteArray)
    private var lastEdit: SavedEdit? = null

    suspend fun undoEdit(): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val saved = lastEdit ?: error("No targeted edit to undo in this workspace session")
            val parts = PathPolicy.segments(saved.path)
            val parent = if (saved.uri.scheme == "file") {
                val start = DocumentFile.fromFile(checkNotNull(directWorkspaceRoot()) { "Direct storage permission is unavailable" })
                checkNotNull(workspaceDirectory(parts.dropLast(1), createMissing = false, start = start))
            } else resolve(parts.dropLast(1))
            val file = checkNotNull(child(parent, parts.last())) { "File is missing" }
            require(file.uri == saved.uri && hasSourceContent(file, saved.after)) {
                "Undo conflict: the file changed after the agent edit"
            }
            if (saved.before == null) {
                check(file.delete() && child(parent, parts.last()) == null) { "Could not undo file creation" }
            } else {
                val bytes = saved.before.toByteArray(Charsets.UTF_8)
                writeBytes(parent, file, bytes)
                verifySourceWrite(parent, parts.last(), saved.path, bytes)
            }
            lastEdit = null
            saved.path
        }
    }

    /** Set from Settings so the agent can run Termux commands over SSH. */
    @Volatile var sshConfig: SshConfig? = null

    init {
        require(treeUri.scheme == "content" && DocumentsContract.isTreeUri(treeUri)) { "Select a document tree" }
    }

    fun label(): String = root.name ?: "Workspace"

    override fun description(): String = "User-selected workspace: ${label()}. Use relative paths for file tools. Listings are limited to $MAX_ENTRIES entries and UTF-8 files to $MAX_FILE_BYTES bytes. Android work stops at source editing, inspection and SSH decompilation. Static website uploads are available for explicit web deployment requests."

    /** True when this mutation may proceed: automatic mode, or an explicit approval. */
    private suspend fun permitted(summary: String, textEdit: Boolean = false): Boolean = (textEdit && autoApprove()) || approve(summary)

    suspend fun list(path: String = ""): List<WorkspaceEntry> = mutex.withLock {
        withContext(Dispatchers.IO) { listInternal(path).first }
    }

    /** Reads a workspace text file, or null when it is missing or unreadable. */
    suspend fun readTextOrNull(path: String): String? = mutex.withLock {
        withContext(Dispatchers.IO) { runCatching { readText(path) }.getOrNull() }
    }

    /** Writes a root-level text file such as AGENTS.md without touching user content elsewhere. */
    suspend fun writeRootText(name: String, content: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                require(PathPolicy.segments(name).size == 1) { "Use a root file name" }
                val parent = root
                if (child(parent, name) != null) return@runCatching false
                saveChild(parent, name, "application/octet-stream", content.toByteArray(Charsets.UTF_8))
                true
            }.getOrDefault(false)
        }
    }

    override suspend fun execute(name: String, arguments: JSONObject): String = mutex.withLock {
        if (!toolAvailable(name)) {
            return@withLock JSONObject().put("error", "Tool unavailable in source-only mode: $name").toString()
        }
        try {
            withContext(Dispatchers.IO) {
                if (name in additionalWorkspaceMutations) {
                    val target = arguments.optString("output").ifBlank {
                        arguments.optString("path").ifBlank { arguments.optString("name") }
                    }
                    if (!permitted("${name.replace('_', ' ')}: $target")) return@withContext denied()
                }
                when (name) {
                    "list_files" -> {
                        val path = if (arguments.has("path")) rootAlias(text(arguments, "path")) else ""
                        val offset = if (arguments.has("offset")) arguments.getInt("offset") else 0
                        require(offset >= 0) { "offset must be non-negative" }
                        val (entries, truncated) = listInternal(path, offset)
                        JSONObject().put("entries", JSONArray().apply {
                            entries.forEach { put(JSONObject().put("name", it.name).put("path", it.path)
                                .put("isDirectory", it.isDirectory).put("size", it.size)) }
                        }).put("truncated", truncated).apply {
                            if (truncated) put("next_offset", offset + entries.size)
                        }.toString()
                    }
                    "read_file" -> {
                        val path = text(arguments, "path")
                        readFileReport(path).toString()
                    }
                    "read_lines" -> {
                        val path = text(arguments, "path")
                        val start = arguments.getInt("start_line")
                        val count = if (arguments.has("line_count")) arguments.getInt("line_count") else 60
                        PathPolicy.requireReadable(path, allowSecrets = false)
                        val file = workspaceReadFile(path)
                        require(file.isFile) { "Path is not a file" }
                        val report = if (file.length() in 1..MAX_FILE_BYTES.toLong()) {
                            SourceInspection.lines(readText(file), start, count)
                        } else {
                            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            checkNotNull(resolver.openInputStream(file.uri)).use {
                                SourceInspection.window(java.io.InputStreamReader(it, decoder), start, count)
                            }
                        }
                        report.put("path", path).toString()
                    }
                    "plan_source_checks" -> {
                        val checks = arguments.getJSONArray("checks")
                        require(checks.length() in 1..12) { "Provide 1 to 12 checks" }
                        for (i in 0 until checks.length()) {
                            val item = checks.getJSONObject(i)
                            PathPolicy.segments(text(item, "path"))
                            require(text(item, "label").isNotBlank() && text(item, "label").length <= 120)
                            require(text(item, "text").length in 1..2000 && item.get("should_exist") is Boolean)
                        }
                        JSONObject().put("registered", checks.length()).toString()
                    }
                    "check_source_text" -> {
                        val path = text(arguments, "path")
                        SourceInspection.check(readText(path), text(arguments, "text"), arguments.getBoolean("should_exist"))
                            .put("path", path).toString()
                    }
                    "create_directory" -> createDirectory(text(arguments, "path"))
                    "write_file" -> {
                        val overwrite = if (arguments.has("overwrite")) {
                            require(arguments.get("overwrite") is Boolean) { "overwrite must be boolean" }
                            arguments.getBoolean("overwrite")
                        } else true
                        writeFile(text(arguments, "path"), text(arguments, "content"), overwrite)
                    }
                    "edit_file" -> editFile(text(arguments, "path"), text(arguments, "old_text"), text(arguments, "new_text"))
                    "delete_path" -> deletePath(text(arguments, "path"))
                    "move_path" -> movePath(text(arguments, "from"), text(arguments, "to"))
                    "search_text" -> {
                        val query = text(arguments, "query").trim()
                        require(query.isNotEmpty() && query.length <= 200) { "query must be 1-200 characters" }
                        val path = if (arguments.has("path")) rootAlias(text(arguments, "path")) else ""
                        searchText(query, path)
                    }
                    "apk_overview" -> withApk(text(arguments, "path")) { apk, file ->
                        JSONObject().put("path", text(arguments, "path"))
                            .put("bytes", file.length())
                            .put("layout", apk.layoutSummary())
                            .put("dexFiles", JSONArray(apk.dexFiles()))
                            .put("entries", JSONArray().apply {
                                apk.entries(300).forEach { put(JSONObject().put("name", it.name).put("size", it.size)) }
                            }).toString()
                    }
                    "apk_resources" -> withApk(text(arguments, "path")) { apk, file ->
                        when {
                            arguments.has("layout") -> {
                                val requested = text(arguments, "layout")
                                val candidates = apk.xmlResourceCandidates(requested)
                                val entry = candidates.singleOrNull()
                                    ?: return@withApk apk.resourceInventory().put("requestedLayout", requested)
                                        .put("status", if (candidates.isEmpty()) "not_found" else "ambiguous")
                                        .put("candidates", JSONArray(candidates)).toString()
                                val bytes = apk.readEntry(entry, 1024 * 1024)
                                    ?: return@withApk apk.resourceInventory().put("layout", entry)
                                        .put("status", if (apk.hasEntry(entry)) "too_large" else "not_found").toString()
                                val elements = BinaryXml.parse(bytes)
                                require(elements.isNotEmpty()) { "No binary XML elements decoded" }
                                JSONObject().put("layout", entry).put("elementCount", elements.size)
                                    .put("elements", JSONArray().apply { elements.take(200).forEach {
                                        put(JSONObject().put("name", it.name).put("attributes", JSONObject(it.attributes)))
                                    } }).toString()
                            }
                            arguments.has("string_name") -> {
                                val name = text(arguments, "string_name")
                                require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_.]*"))) { "Invalid string resource name" }
                                val pm = context.packageManager
                                val pkg = requireNotNull(pm.getPackageArchiveInfo(file.path, 0)) { "Unreadable resource package" }
                                val info = requireNotNull(pkg.applicationInfo)
                                info.sourceDir = file.path
                                info.publicSourceDir = file.path
                                val resources = pm.getResourcesForApplication(info)
                                val config = android.content.res.Configuration(resources.configuration)
                                if (arguments.has("locale")) config.setLocale(java.util.Locale.Builder()
                                    .setLanguageTag(text(arguments, "locale")).build())
                                val localized = android.content.res.Resources(resources.assets, resources.displayMetrics, config)
                                val id = localized.getIdentifier(name, "string", pkg.packageName)
                                if (id == 0) return@withApk apk.resourceInventory().put("name", name).put("status", "not_found").toString()
                                JSONObject().put("name", name).put("requestedLocale", config.locales[0].toLanguageTag())
                                    .put("resolvedValue", localized.getString(id)).toString()
                            }
                            else -> apk.resourceInventory().toString()
                        }
                    }
                    "apk_compare" -> {
                        fun inspect(key: String) = withApk(text(arguments, key)) { apk, file -> apk.manifest() to file.length() }
                        val (old, oldSize) = inspect("before")
                        val (new, newSize) = inspect("after")
                        require(old.packageName.isNotBlank() && new.packageName.isNotBlank()) { "Cannot identify both APK packages" }
                        fun diff(a: List<String>, b: List<String>) = JSONObject()
                            .put("added", JSONArray((b.toSet() - a.toSet()).sorted()))
                            .put("removed", JSONArray((a.toSet() - b.toSet()).sorted()))
                        fun pair(a: String, b: String) = JSONObject().put("before", a).put("after", b)
                        JSONObject().put("package", pair(old.packageName, new.packageName))
                            .put("versionName", pair(old.versionName, new.versionName)).put("versionCode", pair(old.versionCode, new.versionCode))
                            .put("minSdk", pair(old.minSdk, new.minSdk)).put("targetSdk", pair(old.targetSdk, new.targetSdk))
                            .put("sizeDeltaBytes", newSize - oldSize).put("permissions", diff(old.permissions, new.permissions))
                            .put("activities", diff(old.activities, new.activities)).put("services", diff(old.services, new.services))
                            .put("providers", diff(old.providers, new.providers)).put("receivers", diff(old.receivers, new.receivers)).toString()
                    }
                    "apk_manifest" -> withApk(text(arguments, "path")) { apk, _ ->
                        val m = apk.manifest()
                        JSONObject()
                            .put("package", m.packageName).put("versionName", m.versionName).put("versionCode", m.versionCode)
                            .put("minSdk", m.minSdk).put("targetSdk", m.targetSdk)
                            .put("applicationLabel", m.applicationLabel).put("applicationName", m.applicationName)
                            .put("permissions", JSONArray(m.permissions))
                            .put("activities", JSONArray(m.activities))
                            .put("services", JSONArray(m.services))
                            .put("receivers", JSONArray(m.receivers))
                            .put("providers", JSONArray(m.providers))
                            .toString()
                    }
                    "apk_dex" -> withApk(text(arguments, "path")) { apk, _ ->
                        val target = if (arguments.has("dex")) text(arguments, "dex") else apk.dexFiles().firstOrNull()
                            ?: error("APK has no dex files")
                        val query = if (arguments.has("query")) text(arguments, "query") else null
                        val info = apk.dexInfo(target, query)
                        JSONObject().put("dex", info.name).put("classes", info.classes)
                            .put("methods", info.methods).put("strings", info.strings)
                            .put("classList", JSONArray(info.classNames))
                            .put("matchedMethods", JSONArray(info.matchedMethods))
                            .toString()
                    }
                    "apk_strings" -> withApk(text(arguments, "path")) { apk, _ ->
                        val query = text(arguments, "query")
                        JSONObject().put("query", query)
                            .put("hits", JSONArray(apk.searchStrings(query))).toString()
                    }
                    "apk_entry_write" -> withApk(text(arguments, "path")) { apk, _ ->
                        val source = text(arguments, "source")
                        val entry = text(arguments, "entry")
                        val content = if (source.endsWith(".txt") || source.endsWith(".xml") || source.endsWith(".json") || source.endsWith(".smali"))
                            readText(source).toByteArray(Charsets.UTF_8) else readBytes(source)
                        val outName = text(arguments, "output")
                        val touched = writeApkOutput(outName) { target -> apk.rewrite(target, mapOf(entry to content), emptySet()) }
                        JSONObject().put("output", outName).put("changes", JSONArray(touched))
                            .put("note", "Unsigned APK: re-sign before installing").toString()
                    }
                    "apk_entry_delete" -> withApk(text(arguments, "path")) { apk, _ ->
                        val entry = text(arguments, "entry")
                        val outName = text(arguments, "output")
                        val touched = writeApkOutput(outName) { target -> apk.rewrite(target, emptyMap(), setOf(entry)) }
                        JSONObject().put("output", outName).put("changes", JSONArray(touched))
                            .put("note", "Unsigned APK: re-sign before installing").toString()
                    }
                    "dex_smali" -> {
                        val path = text(arguments, "path")
                        val className = text(arguments, "class")
                        val requestedDex = if (arguments.has("dex")) text(arguments, "dex") else null
                        val needle = className.replace('.', '/').removePrefix("L").removeSuffix(";")
                        val dexBytes: ByteArray
                        val sourceLabel: String
                        if (path.endsWith(".dex")) {
                            dexBytes = readBytes(path); sourceLabel = path
                        } else {
                            val bytes = readBytes(path)
                            val temp = java.io.File.createTempFile("ashura-apk-", ".apk", context.cacheDir)
                            try {
                                temp.writeBytes(bytes)
                                val inspector = ApkInspector(temp)
                                val candidates = if (requestedDex != null) listOf(requestedDex) else inspector.dexFiles()
                                val entry = candidates.firstOrNull { dex ->
                                    runCatching { inspector.dexClasses(dex, 200000).any { it.contains(needle) } }.getOrDefault(false)
                                } ?: error("Class $className not found in ${candidates.joinToString()}")
                                dexBytes = inspector.readEntry(entry) ?: error("Missing $entry")
                                sourceLabel = "$path!$entry"
                            } finally { temp.delete() }
                        }
                        val dexFile = java.io.File.createTempFile("ashura-dex-", ".dex", context.cacheDir)
                        try {
                            dexFile.writeBytes(dexBytes)
                            val smali = SmaliBridge.classSmali(dexFile, className)
                            JSONObject().put("source", sourceLabel).put("class", className)
                                .put("bytes", smali.length).put("smali", smali.take(120_000)).toString()
                        } finally { dexFile.delete() }
                    }
                    "smali_assemble" -> {
                        val folder = resolve(PathPolicy.segments(text(arguments, "folder")))
                        val output = text(arguments, "output")
                        require(output.endsWith(".dex")) { "output must end with .dex" }
                        val temp = java.io.File.createTempFile("ashura-smali-", "", context.cacheDir)
                        temp.delete()
                        temp.mkdirs()
                        val copied = copySmaliTree(folder, temp)
                        require(copied > 0) { "No .smali files in that folder" }
                        val dexOut = java.io.File.createTempFile("ashura-out-", ".dex", context.cacheDir)
                        try {
                            SmaliBridge.assemble(temp, dexOut)
                            val dexBytes = dexOut.readBytes()
                            val parentUri = ensureDirectoryUri(PathPolicy.segments(output).dropLast(1)) ?: error("Cannot create output folder")
                            val name = PathPolicy.segments(output).last()
                            findChildUri(parentUri, name)?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                            val uri = createChildUri(parentUri, "application/octet-stream", name) ?: error("Cannot create $output")
                            writeBytesAt(uri, dexBytes)
                            JSONObject().put("output", output).put("smaliFiles", copied).put("bytes", dexBytes.size).toString()
                        } finally {
                            temp.deleteRecursively()
                            dexOut.delete()
                        }
                    }
                    "apk_smali_replace" -> withApk(text(arguments, "path")) { apk, _ ->
                        val dexEntry = text(arguments, "dex")
                        val folder = resolve(PathPolicy.segments(text(arguments, "folder")))
                        val output = text(arguments, "output")
                        val temp = java.io.File.createTempFile("ashura-smali-", "", context.cacheDir)
                        temp.delete(); temp.mkdirs()
                        val dexOut = java.io.File.createTempFile("ashura-out-", ".dex", context.cacheDir)
                        try {
                            val copied = copySmaliTree(folder, temp)
                            require(copied > 0) { "No .smali files in that folder" }
                            SmaliBridge.assemble(temp, dexOut)
                            val dexBytes = dexOut.readBytes()
                            val touched = writeApkOutput(output) { target -> apk.rewrite(target, mapOf(dexEntry to dexBytes), emptySet()) }
                            JSONObject().put("output", output).put("dex", dexEntry)
                                .put("smaliFiles", copied).put("dexBytes", dexBytes.size)
                                .put("changes", JSONArray(touched))
                                .put("note", "Unsigned APK: re-sign before installing").toString()
                        } finally {
                            temp.deleteRecursively()
                            dexOut.delete()
                        }
                    }

                    "apk_dex_extract" -> withApk(text(arguments, "path")) { apk, _ ->
                        val dexEntry = if (arguments.has("dex")) text(arguments, "dex") else apk.dexFiles().firstOrNull()
                            ?: error("APK has no dex files")
                        val target = PathPolicy.segments(text(arguments, "folder"))
                        val dexBytes = apk.readEntry(dexEntry) ?: error("Missing $dexEntry")
                        val tempDex = java.io.File.createTempFile("ashura-dex-", ".dex", context.cacheDir)
                        val tempTree = java.io.File.createTempFile("ashura-smali-", "", context.cacheDir)
                        tempTree.delete(); tempTree.mkdirs()
                        try {
                            tempDex.writeBytes(dexBytes)
                            val result = SmaliBridge.disassemble(tempDex, tempTree)
                            val base = ensureDirectoryUri(target) ?: error("Cannot create output folder")
                            var written = 0
                            result.classFiles.forEach { relative ->
                                val file = java.io.File(tempTree, relative)
                                val bytes = file.readBytes()
                                val parts = relative.split('/')
                                var parentUri = base
                                parts.dropLast(1).forEach { segment ->
                                    parentUri = findChildUri(parentUri, segment)
                                        ?: createChildUri(parentUri, DocumentsContract.Document.MIME_TYPE_DIR, segment)
                                        ?: error("Cannot create $segment")
                                }
                                val name = parts.last()
                                findChildUri(parentUri, name)?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                                val uri = createChildUri(parentUri, "text/plain", name) ?: error("Cannot create $name")
                                writeBytesAt(uri, bytes)
                                written++
                            }
                            JSONObject().put("dex", dexEntry).put("folder", text(arguments, "folder"))
                                .put("classes", written).toString()
                        } finally {
                            tempDex.delete(); tempTree.deleteRecursively()
                        }
                    }
                    "dex_set_string" -> withApk(text(arguments, "path")) { apk, _ ->
                        val dexEntry = if (arguments.has("dex")) text(arguments, "dex") else apk.dexFiles().firstOrNull()
                            ?: error("APK has no dex files")
                        val find = text(arguments, "find")
                        val replace = text(arguments, "replace")
                        val output = text(arguments, "output")
                        val (dexBytes, patched) = apk.patchDexString(dexEntry, find, replace)
                        require(patched > 0) { "String not found in $dexEntry (or length mismatch)" }
                        val touched = writeApkOutput(output) { target ->
                            apk.rewrite(target, mapOf(dexEntry to dexBytes), emptySet())
                        }
                        JSONObject().put("output", output).put("dex", dexEntry)
                            .put("patched", patched).put("find", find).put("replace", replace)
                            .put("changes", JSONArray(touched))
                            .put("note", "Unsigned APK: sign before installing").toString()
                    }
                    "apk_extract_entry" -> withApk(text(arguments, "path")) { apk, _ ->
                        val entry = text(arguments, "entry")
                        val bytes = apk.readEntry(entry) ?: error("Entry not found: $entry")
                        val output = text(arguments, "output")
                        val parentUri = ensureDirectoryUri(PathPolicy.segments(output).dropLast(1)) ?: error("Cannot create output folder")
                        val name = PathPolicy.segments(output).last()
                        findChildUri(parentUri, name)?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                        val uri = createChildUri(parentUri, "application/octet-stream", name) ?: error("Cannot create $output")
                        writeBytesAt(uri, bytes)
                        JSONObject().put("entry", entry).put("output", output).put("bytes", bytes.size).toString()
                    }
                    "hex_view" -> {
                        val path = text(arguments, "path")
                        val offset = if (arguments.has("offset")) arguments.optInt("offset", 0).coerceAtLeast(0) else 0
                        val length = (if (arguments.has("length")) arguments.optInt("length", 512) else 512).coerceIn(1, 16384)
                        val bytes = readBytes(path)
                        require(offset <= bytes.size) { "Offset is past the end of the file" }
                        val end = minOf(bytes.size, offset + length)
                        val slice = bytes.copyOfRange(offset, end)
                        val hex = StringBuilder()
                        val ascii = StringBuilder()
                        slice.forEachIndexed { index, value ->
                            hex.append(String.format("%02X ", value.toInt() and 0xFF))
                            if (index % 16 == 15) hex.append('\n')
                            val code = value.toInt() and 0xFF
                            ascii.append(if (code in 0x20..0x7E) code.toChar() else '.')
                        }
                        JSONObject().put("path", path).put("offset", offset).put("length", slice.size)
                            .put("size", bytes.size).put("hex", hex.toString().trim())
                            .put("ascii", ascii.toString()).toString()
                    }
                    "read_block" -> {
                        val path = text(arguments, "path")
                        val offset = if (arguments.has("offset")) arguments.optInt("offset", 0).coerceAtLeast(0) else 0
                        val length = (if (arguments.has("length")) arguments.optInt("length", 4096) else 4096).coerceIn(1, 4 * 1024 * 1024)
                        val bytes = readBytes(path)
                        require(offset <= bytes.size) { "Offset is past the end of the file" }
                        val end = minOf(bytes.size, offset + length)
                        val slice = bytes.copyOfRange(offset, end)
                        JSONObject().put("path", path).put("offset", offset).put("length", slice.size)
                            .put("size", bytes.size)
                            .put("base64", android.util.Base64.encodeToString(slice, android.util.Base64.NO_WRAP)).toString()
                    }
                    "write_block", "replace_asset" -> {
                        val path = text(arguments, "path")
                        require(path.substringAfterLast('.', "").lowercase() in binaryAssetExtensions) { "Choose a media, font, or PDF asset" }
                        val original = readBytes(path)
                        val replacement = if (name == "replace_asset") {
                            readBytes(text(arguments, "source"))
                        } else {
                            val offset = arguments.getInt("offset")
                            val data = android.util.Base64.decode(text(arguments, "base64"), android.util.Base64.DEFAULT)
                            require(offset in 0..original.size && data.size <= original.size - offset) { "Range exceeds file size" }
                            original.copyOf().also { data.copyInto(it, offset) }
                        }
                        if (original.contentEquals(replacement)) return@withContext success(path, "unchanged")
                        if (!permitted("Update binary asset: $path")) return@withContext denied()
                        require(readBytes(path).contentEquals(original)) { "File changed before update" }
                        val parts = PathPolicy.segments(path)
                        val parent = workspaceReadDirectory(parts.dropLast(1))
                        val target = checkNotNull(child(parent, parts.last())) { "File is missing" }
                        writeBytes(parent, target, replacement)
                        check(readBytes(path).contentEquals(replacement)) { "Saved bytes could not be verified" }
                        success(path, "updated")
                    }
                    "fetch_url" -> {
                        val url = text(arguments, "url").trim()
                        require(url.startsWith("https://") || url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
                            "Only HTTPS URLs are allowed"
                        }
                        val limit = (if (arguments.has("limit")) arguments.optInt("limit", 120_000) else 120_000).coerceIn(1024, 2_000_000)
                        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 15_000
                            readTimeout = 20_000
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AshuraAI/1.0")
                            setRequestProperty("Accept", "text/html,application/json,text/plain,*/*")
                        }
                        try {
                            val status = connection.responseCode
                            val type = connection.contentType.orEmpty()
                            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                            val body = stream?.use { readPrefix(it, limit + 1) } ?: ByteArray(0)
                            val textLike = type.contains("json") || type.contains("text") || type.contains("html") || type.contains("xml") || type.contains("javascript")
                            JSONObject().put("url", url).put("status", status).put("contentType", type)
                                .put("bytesRead", body.size)
                                .put(if (textLike) "text" else "base64",
                                    if (textLike) String(body, 0, minOf(body.size, limit), Charsets.UTF_8)
                                    else android.util.Base64.encodeToString(body.copyOf(minOf(body.size, limit)), android.util.Base64.NO_WRAP))
                                .put("truncated", body.size > limit).toString()
                        } finally {
                            connection.disconnect()
                        }
                    }
                    "deploy_vercel" -> {
                        val folder = if (arguments.has("path")) text(arguments, "path") else ""
                        val token = text(arguments, "token").trim()
                        require(token.isNotEmpty()) { "Vercel token is required" }
                        val project = if (arguments.has("project")) text(arguments, "project").trim() else ""
                        if (!permitted("Upload workspace files to Vercel: ${folder.ifBlank { "." }}")) return@withContext denied()
                        val files = collectFiles(folder)
                        require(files.isNotEmpty()) { "No files to deploy in $folder" }
                        require(files.size <= 100) { "Vercel inline deploy accepts at most 100 files; found ${files.size}" }
                        val payload = JSONObject().put("name", project.ifBlank { "ashura-site" })
                            .put("projectSettings", JSONObject().put("framework", JSONObject.NULL).put("buildCommand", ""))
                            .put("files", JSONArray().apply {
                                files.forEach { (relative, bytes) ->
                                    put(JSONObject().put("file", relative)
                                        .put("data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                                        .put("encoding", "base64"))
                                }
                            })
                        val (status, body) = httpPost("https://api.vercel.com/v13/deployments", token,
                            payload.toString().toByteArray(Charsets.UTF_8), "application/json")
                        JSONObject().put("status", status).put("files", files.size)
                            .put("response", runCatching { JSONObject(body) }.getOrElse { JSONObject().put("raw", body.take(600)) }).toString()
                    }
                    "deploy_netlify" -> {
                        val folder = if (arguments.has("path")) text(arguments, "path") else ""
                        val token = text(arguments, "token").trim()
                        require(token.isNotEmpty()) { "Netlify token is required" }
                        var siteId = if (arguments.has("site")) text(arguments, "site").trim() else ""
                        if (!permitted("Upload workspace files to Netlify: ${folder.ifBlank { "." }}")) return@withContext denied()
                        val files = collectFiles(folder)
                        require(files.isNotEmpty()) { "No files to deploy in $folder" }
                        if (siteId.isBlank()) {
                            val (createStatus, createBody) = httpPost("https://api.netlify.com/api/v1/sites", token,
                                JSONObject().put("name", if (arguments.has("project")) text(arguments, "project") else "ashura-site")
                                    .toString().toByteArray(Charsets.UTF_8), "application/json")
                            require(createStatus in 200..299) { "Netlify site creation failed (HTTP $createStatus): ${createBody.take(300)}" }
                            siteId = JSONObject(createBody).optString("id")
                        }
                        val archive = java.io.ByteArrayOutputStream()
                        java.util.zip.ZipOutputStream(archive).use { zip ->
                            files.forEach { (relative, bytes) ->
                                zip.putNextEntry(java.util.zip.ZipEntry(relative))
                                zip.write(bytes)
                                zip.closeEntry()
                            }
                        }
                        val (status, body) = httpPost("https://api.netlify.com/api/v1/sites/$siteId/deploys", token,
                            archive.toByteArray(), "application/zip")
                        JSONObject().put("status", status).put("site", siteId).put("files", files.size)
                            .put("response", runCatching { JSONObject(body) }.getOrElse { JSONObject().put("raw", body.take(600)) }).toString()
                    }
                    "ssh_exec" -> {
                        val ssh = sshConfig ?: error("SSH is not configured. Settings → SSH (Termux).")
                        val command = text(arguments, "command")
                        require(command.isNotBlank()) { "command is required" }
                        if (!permitted("Run SSH command on ${ssh.host}: $command")) return@withContext denied()
                        val result = SshClient(ssh, context).exec(command, arguments.optInt("timeout_ms", 120_000))
                        JSONObject().put("exit", result.exit).put("ms", result.millis)
                            .put("stdout", result.stdout.takeLast(8000)).put("stderr", result.stderr.takeLast(4000)).toString()
                    }
                    "ssh_tools" -> {
                        val ssh = sshConfig ?: error("SSH is not configured. Settings → SSH (Termux).")
                        val client = SshClient(ssh, context)
                        val probe = client.exec("for t in java jadx apktool; do printf '%s=' \$t; command -v \$t >/dev/null 2>&1 && echo yes || echo no; done", 30_000)
                        JSONObject().put("exit", probe.exit).put("tools", probe.stdout.trim()).toString()
                    }
                    "ssh_install" -> {
                        val ssh = sshConfig ?: error("SSH is not configured. Settings → SSH (Termux).")
                        val packages = if (arguments.has("packages")) text(arguments, "packages") else "openjdk-17 apktool jadx"
                        if (!permitted("Install SSH packages on ${ssh.host}: $packages")) return@withContext denied()
                        val result = SshClient(ssh, context).exec("pkg install -y $packages", 600_000)
                        JSONObject().put("exit", result.exit).put("packages", packages)
                            .put("output", (result.stdout + "\n" + result.stderr).takeLast(6000)).toString()
                    }
                    "ssh_decompile" -> {
                        val ssh = sshConfig ?: error("SSH is not configured. Settings → SSH (Termux).")
                        val rootPath = rootFilesystemPath() ?: error("Workspace is not on shared storage; pick a folder under /sdcard")
                        val apk = text(arguments, "apk")
                        val inputParts = PathPolicy.segments(apk)
                        PathPolicy.requireReadable(apk)
                        require(apk.endsWith(".apk", ignoreCase = true) && resolve(inputParts).isFile) {
                            "Choose an existing APK inside the workspace"
                        }
                        val mode = if (arguments.has("mode")) text(arguments, "mode") else "jadx"
                        require(mode == "jadx" || mode == "apktool") { "mode must be jadx or apktool" }
                        var output = if (arguments.has("output")) text(arguments, "output")
                            else (inputParts.dropLast(1) + (inputParts.last().substringBeforeLast('.') + "_" + mode)).joinToString("/")
                        if (!arguments.has("output") && child(resolve(inputParts.dropLast(1)), output.substringAfterLast('/')) != null) {
                            output += "_" + java.util.UUID.randomUUID().toString().take(8)
                        }
                        val outputParts = PathPolicy.segments(output)
                        require(child(resolve(outputParts.dropLast(1)), outputParts.last()) == null) {
                            "Choose a new output folder"
                        }
                        fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
                        val apkPath = quote("$rootPath/$apk")
                        val outPath = quote("$rootPath/$output")
                        val decode = if (mode == "jadx") "jadx --no-res -d $outPath $apkPath"
                            else "apktool d --no-res --no-assets -o $outPath $apkPath"
                        val command = "test ! -e $outPath && $decode"
                        if (!permitted("Write decompiler output on ${ssh.host}: $output")) return@withContext denied()
                        val result = SshClient(ssh, context).exec(command, 900_000)
                        JSONObject().put("exit", result.exit).put("mode", mode).put("output", output)
                            .put("verification", inspectDecodeOutput(outputParts, mode, result.exit))
                            .put("log", (result.stdout + "\n" + result.stderr).takeLast(6000)).toString()
                    }
                    "shell_checksum" -> {
                        val path = text(arguments, "path")
                        val bytes = readBytes(path)
                        val hash = kotlinx.coroutines.runInterruptible {
                            val process = ProcessBuilder("/system/bin/sh", "-c",
                                "command -v sha256sum >/dev/null 2>&1 && exec sha256sum")
                                .redirectErrorStream(true).start()
                            try {
                                process.outputStream.use { it.write(bytes) }
                                val output = process.inputStream.bufferedReader().use { it.readText() }
                                check(process.waitFor() == 0) { "Android shell checksum failed" }
                                output.substringBefore(' ').trim().also {
                                    check(it.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid shell checksum" }
                                }
                            } finally {
                                process.destroyForcibly()
                            }
                        }
                        JSONObject().put("path", path).put("sha256", hash).put("bytes", bytes.size).toString()
                    }
                    "shell_exec" -> {
                        val cwd = checkNotNull(directWorkspaceRoot()) { "Direct storage permission is unavailable" }
                        val command = text(arguments, "command")
                        require(command.isNotBlank()) { "command is required" }
                        if (!permitted("Run local shell command: $command")) return@withContext denied()
                        val (exit, out) = kotlinx.coroutines.runInterruptible {
                            runLocalShell(command, arguments.optInt("timeout_ms", 60_000).coerceIn(1, 600_000), cwd)
                        }
                        JSONObject().put("exit", exit).put("cwd", cwd.absolutePath).put("output", out.takeLast(12000)).toString()
                    }
                    "grep" -> {
                        val query = text(arguments, "query")
                        require(query.isNotBlank() && query.length <= 200) { "query must be 1-200 characters" }
                        val target = if (arguments.has("path")) text(arguments, "path").trim('/') else ""
                        val include = if (arguments.has("glob")) text(arguments, "glob") else "*"
                        val quotedQuery = query.replace("\"", "\\\"")
                        val command = "grep -rn --binary-files=without-match -I --include='$include' \"$quotedQuery\" ${if (target.isBlank()) "." else "'" + target.replace("'", "'\\''") + "'"} 2>/dev/null | head -200"
                        val (_, out) = runLocalShell(command, 60_000)
                        JSONObject().put("query", query).put("matches", out.lines().filter { it.isNotBlank() }.size).put("output", out.take(12000)).toString()
                    }
                    "glob" -> {
                        val pattern = text(arguments, "pattern")
                        require(pattern.isNotBlank()) { "pattern is required" }
                        val target = if (arguments.has("path")) text(arguments, "path").trim('/') else "."
                        val command = "find ${if (target.isBlank()) "." else "'" + target.replace("'", "'\\''") + "'"} -name '" +
                            pattern.replace("'", "'\\''") + "' 2>/dev/null | head -500"
                        val (_, out) = runLocalShell(command, 60_000)
                        JSONObject().put("pattern", pattern).put("count", out.lines().count { it.isNotBlank() }).put("paths", out.take(12000)).toString()
                    }
                    "skill_list" -> skillListTool()
                    "skill_read" -> {
                        val name = text(arguments, "name")
                        require(name.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Invalid skill name" }
                        JSONObject().put("name", name).put("content", readText(".ashura/skills/$name/SKILL.md")).toString()
                    }
                    "skill_install" -> skillInstall(text(arguments, "url"), arguments.optString("name", ""))
                    "skill_remove" -> skillRemove(text(arguments, "name"))
                    else -> error("Unknown workspace tool")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = when {
                error is IllegalArgumentException -> error.message
                error is SecurityException -> "Permission denied by the storage provider; re-select the workspace folder"
                error is java.io.FileNotFoundException -> "File is no longer available: ${error.message?.take(120)}"
                error is OutOfMemoryError -> "Out of memory while processing this file"
                else -> "$name failed: ${error.message?.take(160) ?: error.javaClass.simpleName}"
            }
            android.util.Log.w("AshuraTool", "$name error: ${error.javaClass.name}: ${error.message}")
            JSONObject().put("error", message).toString()
        }
    }

    private fun text(arguments: JSONObject, key: String): String {
        require(arguments.has(key) && arguments.get(key) is String) { "$key must be a string" }
        return arguments.getString(key)
    }

    /** Models commonly ask for the workspace root as "", ".", "./" or "/". */
    private fun rootAlias(raw: String): String = when (raw.trim()) {
        "", ".", "./", "/", ".\\" -> ""
        else -> raw.trim()
    }

    private fun documentUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    private data class Child(val name: String, val id: String, val directory: Boolean, val size: Long)

    private fun children(directory: DocumentFile, max: Int, requireValidNames: Boolean = false, offset: Int = 0): Pair<List<Child>, Boolean> {
        require(directory.isDirectory) { "Path is not a directory" }
        if (directory.uri.scheme == "file") {
            val folder = java.io.File(checkNotNull(directory.uri.path))
            val entries = checkNotNull(folder.listFiles()) { "Directory unavailable" }.asSequence()
                .filter { file ->
                    val valid = runCatching { PathPolicy.segments(file.name).size == 1 &&
                        file.canonicalFile == file.absoluteFile }.getOrDefault(false)
                    require(valid || !requireValidNames) { "Folder contains an unsupported entry" }
                    valid
                }
                .drop(offset).take(max + 1)
                .map { Child(it.name, it.absolutePath, it.isDirectory, if (it.isFile) it.length() else 0L) }
                .toList()
            return entries.take(max) to (entries.size > max)
        }

        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(directory.uri))
        val result = ArrayList<Child>()
        var skipped = 0
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE), null, null, null).use { cursor ->
            checkNotNull(cursor) { "Directory unavailable" }
            while (cursor.moveToNext()) {
                val name = cursor.getString(1).orEmpty()
                val valid = runCatching { PathPolicy.segments(name).size == 1 }.getOrDefault(false)
                require(valid || !requireValidNames) { "Folder contains a name this operation cannot preserve" }
                if (valid) {
                    if (skipped < offset) { skipped++; continue }
                    if (result.size >= max) return result to true
                    result += Child(name, cursor.getString(0),
                        cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                        if (cursor.isNull(3)) 0L else cursor.getLong(3).coerceAtLeast(0))
                }
            }
        }
        return result to false
    }

    private fun child(parent: DocumentFile, name: String): DocumentFile? {
        if (parent.uri.scheme == "file") {
            val directory = java.io.File(checkNotNull(parent.uri.path))
            val file = java.io.File(directory, name)
            require(file.canonicalFile == file.absoluteFile) { "Linked paths are unavailable in direct storage" }
            return if (file.exists()) DocumentFile.fromFile(file) else null
        }
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        var id: String? = null
        checkNotNull(resolver.query(childrenUri(parent.uri), projection, null, null, null)).use { cursor ->
            while (cursor.moveToNext()) if (cursor.getString(1) == name) {
                require(id == null) { "Ambiguous duplicate document name" }
                id = cursor.getString(0)
            }
        }
        return id?.let { DocumentFile.fromTreeUri(rootContext, documentUri(it)) }
    }

    private val rootContext = context.applicationContext

    private fun resolve(parts: List<String>): DocumentFile {
        var current = root
        parts.forEachIndexed { index, segment ->
            require(current.isDirectory) { "Path component is not a directory: ${parts.take(index).joinToString("/")}" }
            current = requireNotNull(child(current, segment)) { "Missing component: ${segment.take(48)}. List the containing folder to check its name." }
        }
        return current
    }

    /** Resolves existing ancestors and creates missing folders only after the caller approves. */
    private fun workspaceDirectory(parts: List<String>, createMissing: Boolean, start: DocumentFile = root): DocumentFile? {
        var current = start
        parts.forEachIndexed { index, segment ->
            val path = parts.take(index + 1).joinToString("/")
            val existing = child(current, segment)
            val folder = existing ?: if (createMissing) {
                checkNotNull(current.createDirectory(segment)) { "Could not create directory: $path" }
            } else return null
            require(folder.isDirectory && folder.name == segment) { "Expected a directory at $path" }
            current = folder
        }
        return current
    }

    private fun readBytes(path: String): ByteArray {
        PathPolicy.requireReadable(path)
        val file = workspaceReadFile(path)
        require(file.isFile) { "Path is not a file" }
        require(file.length() <= MAX_APK_BYTES) { "File exceeds ${MAX_APK_BYTES / (1024 * 1024)} MB" }
        return resolver.openInputStream(file.uri).use { input ->
            checkNotNull(input) { "File unavailable" }
            readPrefix(input, MAX_APK_BYTES + 1).also {
                require(it.size <= MAX_APK_BYTES) { "File exceeds the binary read size limit" }
            }
        }
    }

    private fun <T> withApk(path: String, block: (ApkInspector, java.io.File) -> T): T {
        PathPolicy.requireReadable(path)
        val source = resolve(PathPolicy.segments(path))
        require(source.isFile) { "Path is not a file" }
        val length = source.length()
        require(length <= MAX_APK_BYTES) { "File exceeds ${MAX_APK_BYTES / (1024 * 1024)} MB" }
        val temp = java.io.File.createTempFile("apk-", ".apk", context.cacheDir)
        try {
            checkNotNull(resolver.openInputStream(source.uri)) { "File unavailable" }.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_APK_BYTES) { "APK exceeds the inspection size limit" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            return block(ApkInspector(temp), temp)
        } finally {
            temp.delete()
        }
    }

    // DocumentFile cannot create children on single-URI documents, so mutations go through
    // DocumentsContract with tree-based URIs.
    private fun childrenUri(parent: Uri): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(parent))

    private fun listChildUris(parent: Uri): List<Pair<String, Uri>> {
        val result = ArrayList<Pair<String, Uri>>()
        val cursor = runCatching {
            resolver.query(childrenUri(parent), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
        }.getOrNull() ?: return result
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue
                result += name to DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
            }
        }
        return result
    }

    private fun findChildUri(parent: Uri, name: String): Uri? =
        listChildUris(parent).firstOrNull { it.first == name }?.second

    private fun createChildUri(parent: Uri, mime: String, name: String): Uri? =
        runCatching { DocumentsContract.createDocument(resolver, parent, mime, name) }.getOrNull()

    /** Walks the tree and creates missing directories, returning the URI of the last segment. */
    private fun ensureDirectoryUri(parts: List<String>): Uri? {
        var current = root.uri
        parts.forEach { segment ->
            current = findChildUri(current, segment)
                ?: createChildUri(current, DocumentsContract.Document.MIME_TYPE_DIR, segment)
                ?: return null
        }
        return current
    }

    private fun writeBytesAt(uri: Uri, bytes: ByteArray) {
        if (uri.scheme == "file") {
            java.io.File(checkNotNull(uri.path)).writeBytes(bytes)
            return
        }
        val direct = runCatching { resolver.openOutputStream(uri, "rwt") }.getOrNull()
        if (direct != null) {
            direct.use { out -> out.write(bytes); out.flush() }
            if (DocumentFile.fromSingleUri(rootContext, uri)?.length() == bytes.size.toLong()) return
        }
        error("Provider stored a different size")
    }

    /** Copies a SAF smali tree into cache so baksmali/smali can read real paths. */
    private fun copySmaliTree(directory: DocumentFile, target: java.io.File): Int {
        var count = 0
        val (entries, truncated) = children(directory, MAX_SCAN * 4)
        require(!truncated) { "Folder has more than ${MAX_SCAN * 4} entries; split the smali tree" }
        entries.forEach { entry ->
            val uri = documentUri(entry.id)
            if (entry.directory) {
                val nested = DocumentFile.fromSingleUri(rootContext, uri) ?: return@forEach
                val childDir = java.io.File(target, entry.name)
                childDir.mkdirs()
                count += copySmaliTree(nested, childDir)
            } else if (entry.name.endsWith(".smali")) {
                val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@forEach
                java.io.File(target, entry.name).writeBytes(bytes)
                count++
            }
        }
        return count
    }

    /** Public entry points used by the Settings skills panel. */
    suspend fun installSkillFromUrl(url: String, name: String = ""): String = withContext(Dispatchers.IO) {
        skillInstall(url, name)
    }

    suspend fun removeSkill(name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { skillRemove(name); true }.getOrDefault(false)
    }

    /** Copies skills shipped inside the APK into the workspace when they are missing. */
    suspend fun ensureBundledSkills(): List<String> = withContext(Dispatchers.IO) {
        val installed = mutableListOf<String>()
        val names = runCatching { context.assets.list("skills")?.toList().orEmpty() }.getOrDefault(emptyList())
        names.forEach { skill ->
            val path = ".ashura/skills/$skill/SKILL.md"
            val exists = runCatching { child(resolve(PathPolicy.segments(".ashura/skills/$skill")), "SKILL.md") }.getOrNull() != null
            if (exists) return@forEach
            val body = runCatching {
                context.assets.open("skills/$skill/SKILL.md").bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@forEach
            val result = runCatching {
                writeSkillFile(skill, "SKILL.md", body)
                installed += skill
            }
            result.exceptionOrNull()?.let { error ->
                android.util.Log.w("AshuraSkills", "install $skill failed: ${error.javaClass.name}: ${error.message} @ ${error.stackTrace.firstOrNull()}")
            }
        }
        installed
    }

    /** Rebuilds an APK into a workspace path, creating the file if needed. */
    private fun writeApkOutput(path: String, build: (java.io.File) -> List<String>): List<String> {
        val parts = PathPolicy.segments(path)
        require(parts.isNotEmpty()) { "Output path required" }
        val temp = java.io.File.createTempFile("ashura-out-", ".apk", context.cacheDir)
        return try {
            val changes = build(temp)
            require(temp.length() <= MAX_APK_BYTES) { "APK output exceeds the size limit" }
            val parentUri = ensureDirectoryUri(parts.dropLast(1)) ?: error("Cannot create the output folder")
            val parent = checkNotNull(DocumentFile.fromTreeUri(rootContext, parentUri))
            saveChild(parent, parts.last(), "application/vnd.android.package-archive", temp.readBytes())
            changes
        } finally {
            temp.delete()
        }
    }

    private fun writeSkillFile(folder: String, name: String, body: String): String {
        val directory = ensureDirectoryUri(listOf(".ashura", "skills", folder)) ?: error("Cannot create skill folder")
        val parent = checkNotNull(DocumentFile.fromTreeUri(rootContext, directory))
        saveChild(parent, name, "application/octet-stream", body.toByteArray(Charsets.UTF_8))
        return ".ashura/skills/$folder/$name"
    }

    /** Working directory for local shell commands: the workspace on shared storage, else app files. */
    private fun localShellCwd(): String {
        val shared = rootFilesystemPath()?.let { java.io.File(it) }
        val dir = if (shared != null && shared.isDirectory) shared else context.filesDir
        return dir.absolutePath
    }

    /** Runs a command with /system/bin/sh inside the workspace, returning exit code and combined output. */
    private fun runLocalShell(command: String, timeoutMs: Int, cwd: java.io.File = java.io.File(localShellCwd())): Pair<Int, String> {
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(cwd)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().use { buffered ->
                    val buffer = CharArray(4096)
                    while (true) {
                        val read = buffered.read(buffer)
                        if (read <= 0) break
                        synchronized(output) {
                            output.append(buffer, 0, read)
                            if (output.length > 400_000) output.delete(0, output.length - 300_000)
                        }
                    }
                }
            }
        }
        reader.start()
        try {
            val finished = process.waitFor(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                return -1 to (synchronized(output) { output.toString() } + "\n[timeout after ${timeoutMs}ms]")
            }
            reader.join(1000)
            return process.exitValue() to synchronized(output) { output.toString() }
        } finally {
            process.destroyForcibly()
            runCatching { process.inputStream.close() }
        }
    }

    /** Filesystem path of the workspace when it lives on shared storage, so Termux can reach it. */
    private fun rootFilesystemPath(): String? {
        val id = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        return when {
            id.startsWith("primary:") -> "/storage/emulated/0/" + id.removePrefix("primary:").trim('/')
            id.startsWith("home:") -> "/storage/emulated/0/Documents/" + id.removePrefix("home:").trim('/')
            else -> null
        }?.trimEnd('/')
    }

    /** Reads every file under [folder] with paths relative to it, for deploy payloads. */
    private fun collectFiles(folder: String): List<Pair<String, ByteArray>> {
        val base = folder.trim('/')
        val parts = PathPolicy.segments(base, allowRoot = true)
        require(parts.none(PathPolicy::isSecretName)) { "Cannot deploy a credentials directory" }
        val directory = if (parts.isEmpty()) root else resolve(parts)
        require(directory.isDirectory) { "Not a directory: $base" }
        val out = mutableListOf<Pair<String, ByteArray>>()
        var total = 0
        fun walk(node: DocumentFile, relative: String) {
            val (entries, truncated) = children(node, MAX_SCAN, requireValidNames = true)
            require(!truncated) { "Directory too large to deploy: $relative" }
            entries.forEach { entry ->
                if (PathPolicy.isSecretName(entry.name) || entry.name == ".git" || entry.name == ".ashura") return@forEach
                val name = if (relative.isEmpty()) entry.name else "$relative/${entry.name}"
                if (entry.directory) {
                    val next = checkNotNull(child(node, entry.name)) { "Deployment directory disappeared: $name" }
                    walk(next, name)
                } else {
                    val fileName = entry.name.lowercase(java.util.Locale.ROOT)
                    require(fileName !in webBuildConfigFiles && fileName.substringAfterLast('.', "") in webAssetExtensions) {
                        "Deploy the prepared static website folder. Unsupported file: $name"
                    }
                    val budget = 16 * 1024 * 1024 - total
                    require(entry.size <= budget) { "Deployment exceeds 16 MiB at $name" }
                    val bytes = checkNotNull(resolver.openInputStream(documentUri(entry.id))) { "Cannot read deployment file: $name" }
                        .use { readPrefix(it, budget + 1) }
                    require(bytes.size <= budget) { "Deployment exceeds 16 MiB at $name" }
                    total += bytes.size
                    out += name to bytes
                }
            }
        }
        walk(directory, "")
        require(out.any { it.first == "index.html" }) { "Static deploy folder needs an index.html at its root" }
        return out
    }

    private fun readPrefix(input: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count <= 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    /** Posts a body to an HTTPS API and returns status plus response text. */
    private fun httpPost(url: String, token: String, body: ByteArray, contentType: String): Pair<Int, String> {
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 180_000
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AshuraAI/1.0")
        }
        return try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            status to (stream?.use { String(readPrefix(it, 512 * 1024), Charsets.UTF_8) } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    private fun skillListTool(): String {
        val skills = runCatching { listSkills() }.getOrDefault(emptyList())
        return JSONObject().put("skills", JSONArray().apply {
            skills.forEach { put(JSONObject().put("name", it.first).put("description", it.second).put("path", ".ashura/skills/${it.first}/SKILL.md")) }
        }).toString()
    }

    /** Reads installed skills as name to (description, body). */
    fun listSkills(): List<Pair<String, String>> {
        val directory = child(root, ".ashura")?.let { ashura -> child(ashura, "skills") } ?: return emptyList()
        val (entries, _) = children(directory, MAX_SCAN)
        return entries.filter { it.directory && it.name != "bypass" }.mapNotNull { entry ->
            val skillDir = DocumentFile.fromSingleUri(rootContext, documentUri(entry.id)) ?: return@mapNotNull null
            val file = child(skillDir, "SKILL.md") ?: return@mapNotNull null
            val text = runCatching { readText(file) }.getOrNull() ?: return@mapNotNull null
            val description = skillDescription(text)
            entry.name to description
        }.sortedBy { it.first }
    }

    private fun skillDescription(text: String): String {
        val frontMatter = text.substringAfter("---", "").substringBefore("\n---")
        val fromHeader = frontMatter.lineSequence().firstOrNull { it.trimStart().startsWith("description:") }
            ?.substringAfter("description:")?.trim()?.trim('"', '\'')
        if (!fromHeader.isNullOrBlank()) return fromHeader.take(140)
        val heading = text.lineSequence().firstOrNull { it.trimStart().startsWith("# ") }?.removePrefix("# ")?.trim()
        return heading?.take(140) ?: "Installed skill"
    }

    private fun skillInstall(url: String, requestedName: String): String {
        require(url.startsWith("https://")) { "Only HTTPS URLs are allowed" }
        val candidates = mutableListOf<String>()
        val raw = toRawUrl(url)
        candidates += raw
        if (raw == url) {
            candidates += "$url/SKILL.md"
            candidates += "$url/raw/main/SKILL.md"
            candidates += "$url/raw/master/SKILL.md"
            candidates += "$url/raw/main/README.md"
            candidates += "$url/raw/master/README.md"
        }
        var body: String? = null
        var used: String? = null
        for (candidate in candidates) {
            body = runCatching { fetchText(candidate) }.getOrNull()
            if (!body.isNullOrBlank()) { used = candidate; break }
        }
        require(body != null) { "Could not download a SKILL.md or README.md from that URL" }
        val name = (requestedName.ifBlank { body.trimStart().removePrefix("---").let { guessSkillName(it, url) } })
            .lowercase().replace(Regex("[^a-z0-9._-]"), "-").take(64).trim('-')
            .ifBlank { "skill-${System.currentTimeMillis() % 100000}" }
        val directoryPath = writeSkillFile(name, "SKILL.md", body)
        return JSONObject().put("name", name).put("path", directoryPath)
            .put("source", used ?: url).put("bytes", body.length).toString()
    }

    private fun skillRemove(name: String): String {
        require(name.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Invalid skill name" }
        val file = child(root, ".ashura")?.let { child(it, "skills") }?.let { child(it, name) }
            ?: error("Skill not found")
        require(file.delete()) { "Could not delete the skill folder" }
        return JSONObject().put("removed", name).toString()
    }

    private fun toRawUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        val match = Regex("https://github\\.com/([^/]+)/([^/]+)(?:/blob/([^/]+)/(.+))?").find(trimmed)
        if (match == null) return trimmed
        val (owner, repo, branch, path) = match.destructured
        return if (path.isNotBlank() && branch.isNotBlank()) {
            "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
        } else "https://raw.githubusercontent.com/$owner/$repo/HEAD/README.md"
    }

    private fun guessSkillName(body: String, url: String): String {
        Regex("(?m)^name:\\s*(.+)$").find(body)?.groupValues?.get(1)?.trim()?.let { if (it.isNotBlank()) return it }
        val line = body.lineSequence().firstOrNull { it.trimStart().startsWith("# ") }
        if (line != null) return line.removePrefix("# ").trim()
        return url.trimEnd('/').substringAfterLast('/')
    }

    private fun fetchText(url: String): String {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "text/plain, text/markdown, */*")
            connection.setRequestProperty("User-Agent", "AshuraAI/1.0")
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    require(output.size() <= MAX_SKILL_BYTES) { "Skill file exceeds ${MAX_SKILL_BYTES / 1024} KB" }
                }
                output.toString("UTF-8")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun listInternal(path: String, offset: Int = 0): Pair<List<WorkspaceEntry>, Boolean> {
        val parts = PathPolicy.segments(path, allowRoot = true)
        val (entries, truncated) = children(workspaceReadDirectory(parts), MAX_ENTRIES, offset = offset)
        return entries.map { WorkspaceEntry(it.name, (parts + it.name).joinToString("/"), it.directory, it.size) } to truncated
    }

    private fun workspaceFileRoot(): DocumentFile {
        if (!ai.ashura.app.BuildConfig.DEBUG) return root
        val directory = checkNotNull(directWorkspaceRoot()) {
            "Enable Direct storage access and select a writable folder on shared storage."
        }
        return DocumentFile.fromFile(directory)
    }

    private fun workspaceReadDirectory(parts: List<String>): DocumentFile =
        checkNotNull(workspaceDirectory(parts, createMissing = false, start = workspaceFileRoot())) {
            "Workspace directory not found"
        }

    private fun directWorkspaceRoot(): java.io.File? {
        if (!ai.ashura.app.BuildConfig.DEBUG || android.os.Build.VERSION.SDK_INT < 30 ||
            !android.os.Environment.isExternalStorageManager() ||
            treeUri.authority != "com.android.externalstorage.documents") return null
        val path = rootFilesystemPath() ?: return null
        return runCatching { java.io.File(path).canonicalFile.takeIf { it.isDirectory && it.canRead() && it.canWrite() } }.getOrNull()
    }

    private fun workspaceReadFile(path: String): DocumentFile {
        val parts = PathPolicy.segments(path)
        return checkNotNull(child(workspaceReadDirectory(parts.dropLast(1)), parts.last())) {
            "Missing component: ${parts.last()}"
        }
    }

    private fun readFileReport(path: String): JSONObject {
        PathPolicy.requireReadable(path)
        val file = workspaceReadFile(path)
        require(file.isFile) { "Choose a file" }
        val bytes = resolver.openInputStream(file.uri)?.use { readPrefix(it, MAX_FILE_BYTES + 1) }
            ?: error("File unavailable")
        val result = JSONObject().put("path", path)
        val prefix = bytes.copyOf(minOf(512, bytes.size))
        val size = prefix.size
        val binary = prefix.any { it.toInt() in 0..8 }
        if (!binary) {
            require(bytes.size <= MAX_FILE_BYTES) { "File exceeds $MAX_FILE_BYTES bytes. Use read_lines for a section." }
            if (bytes.none { it == 0.toByte() }) {
                try {
                    return result.put("content", Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString())
                } catch (_: java.nio.charset.CharacterCodingException) {
                    // Show a byte preview when the file is not UTF-8.
                }
            }
        }
        val fileSize = file.length()
        return result.put("encoding", "hex").put("size", fileSize)
            .put("previewBytes", size).put("truncated", fileSize > size)
            .put("preview", prefix.take(size).joinToString(" ") { "%02x".format(it.toInt() and 255) })
    }
    private fun readText(path: String): String {
        PathPolicy.requireReadable(path)
        return readText(workspaceReadFile(path))
    }

    private fun readText(file: DocumentFile): String {
        require(file.isFile) { "Path is not a file" }
        require(file.length() <= MAX_FILE_BYTES) { "File exceeds $MAX_FILE_BYTES bytes" }
        val bytes = resolver.openInputStream(file.uri).use { input ->
            checkNotNull(input) { "File unavailable" }
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_FILE_BYTES) { "File exceeds $MAX_FILE_BYTES bytes" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.none { it == 0.toByte() }) { "Binary files are not supported" }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }

    private suspend fun createDirectory(path: String): String {
        val parts = PathPolicy.segments(path)
        val start = workspaceFileRoot()
        if (workspaceDirectory(parts, createMissing = false, start = start) != null) return success(path, "already_exists")
        if (!permitted("Create directory and any missing parents: $path")) return denied()
        checkNotNull(workspaceDirectory(parts, createMissing = true, start = start))
        return success(path, "created")
    }

    private suspend fun writeFile(path: String, content: String, overwrite: Boolean): String {
        PathPolicy.requireReadable(path)
        val parts = PathPolicy.segments(path)
        val start = workspaceFileRoot()
        requireSourceDestination(parts.last())
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FILE_BYTES) { "Content exceeds $MAX_FILE_BYTES bytes" }
        val parentParts = parts.dropLast(1)
        val name = parts.last()
        val parentBeforeApproval = workspaceDirectory(parentParts, createMissing = false, start = start)
        val existing = parentBeforeApproval?.let { child(it, name) }
        require(existing == null || existing.isFile) { "Destination exists as a directory: $path" }
        require(existing == null || overwrite) { "File exists and overwrite is disabled: $path" }
        val previousSize = existing?.length()
        val previousModified = existing?.lastModified()
        val action = when {
            existing != null -> "Overwrite file"
            parentBeforeApproval == null -> "Create file and missing parent folders"
            else -> "Create file"
        }
        if (!permitted("$action: $path (${bytes.size} bytes)", textEdit = true)) return denied()
        val parent = checkNotNull(workspaceDirectory(parentParts, createMissing = true, start = start))
        require(parentBeforeApproval == null || parent.uri == parentBeforeApproval.uri) { "Parent changed while awaiting approval: $path" }
        val current = child(parent, name)
        require(current?.uri == existing?.uri) { "Path changed while awaiting approval: $path" }
        require(current?.length() == previousSize && current?.lastModified() == previousModified) {
            "File changed while awaiting approval: $path"
        }
        if (current != null && hasSourceContent(current, bytes)) return success(path, "unchanged")
        val previousText = if (current != null) runCatching { readText(current) }.getOrNull() else null
        saveChild(parent, name, "application/octet-stream", bytes, current)
        val saved = verifySourceWrite(parent, name, path, bytes)
        lastEdit = if (current == null || previousText != null) SavedEdit(path, saved.uri, previousText, bytes) else null
        return JSONObject(success(path, "written")).put("bytes", bytes.size).put("undoAvailable", lastEdit != null).toString()
    }

    private suspend fun editFile(path: String, old: String, replacement: String): String {
        PathPolicy.requireReadable(path)
        val parts = PathPolicy.segments(path)
        val start = workspaceFileRoot()
        requireSourceDestination(parts.last())
        val parent = workspaceDirectory(parts.dropLast(1), createMissing = false, start = start)
        val file = parent?.let { child(it, parts.last()) }
        if (parent == null || file == null) {
            return JSONObject().put("error", "Cannot edit a missing file: $path")
                .put("path", path).put("next", "Use write_file to create it, or list_files to verify the path").toString()
        }
        require(old.isNotEmpty()) { "old_text cannot be empty" }
        require(old.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES && replacement.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) { "Edit text exceeds file size limit" }
        val content = readText(file)
        val index = content.indexOf(old)
        require(index >= 0) { "old_text was not found" }
        require(content.indexOf(old, index + 1) < 0) { "old_text must match exactly once" }
        if (old == replacement) return success(path, "unchanged")
        val bytes = content.replaceRange(index, index + old.length, replacement).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FILE_BYTES) { "Edited content exceeds $MAX_FILE_BYTES bytes" }
        val line = content.substring(0, index).lineSequence().count()
        fun excerpt(value: String): String = when {
            value.isEmpty() -> "(empty)"
            value.length <= 1200 -> value
            else -> value.take(600) + "\n… ${value.length - 1200} characters omitted …\n" + value.takeLast(600)
        }
        if (!permitted("Edit $path at line $line\n\nBefore\n${excerpt(old)}\n\nAfter\n${excerpt(replacement)}", textEdit = true)) return denied()
        require(workspaceDirectory(parts.dropLast(1), createMissing = false, start = start)?.let { child(it, parts.last()) }?.uri == file.uri) { "Path changed while awaiting approval" }
        require(hasSourceContent(file, content.toByteArray(Charsets.UTF_8))) { "File changed while awaiting approval" }
        writeBytes(parent, file, bytes)
        val saved = verifySourceWrite(parent, parts.last(), path, bytes)
        lastEdit = SavedEdit(path, saved.uri, content, bytes)
        return success(path, "edited")
    }

    private fun hasSourceContent(file: DocumentFile, expected: ByteArray): Boolean =
        resolver.openInputStream(file.uri)?.use { input ->
            readPrefix(input, expected.size + 1).contentEquals(expected)
        } == true

    private fun verifySourceWrite(parent: DocumentFile, name: String, path: String, expected: ByteArray): DocumentFile {
        val saved = checkNotNull(child(parent, name)) { "Could not find saved file: $path" }
        check(hasSourceContent(saved, expected)) { "Could not verify saved content: $path" }
        return saved
    }

    private suspend fun deletePath(path: String): String {
        val parts = PathPolicy.segments(path)
        // Documents resolved by name have no parentFile, so the parent must come from the tree walk.
        val parent = workspaceReadDirectory(parts.dropLast(1))
        val name = parts.last()
        val target = requireNotNull(child(parent, name)) { "Path does not exist" }
        val isDirectory = target.isDirectory
        val direct = target.uri.scheme == "file"
        val count = if (isDirectory && !direct) countEntries(target, 500) else 1
        val label = if (isDirectory) "folder and its contents" else "file"
        if (!permitted("Delete $label: $path")) return denied()
        require(child(workspaceReadDirectory(parts.dropLast(1)), name)?.uri == target.uri) {
            "Path changed while awaiting approval"
        }
        val deleted = if (direct && isDirectory) {
            val directory = java.io.File(checkNotNull(target.uri.path)).toPath()
            java.nio.file.Files.walkFileTree(directory, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                    java.nio.file.Files.delete(file)
                    return java.nio.file.FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: java.nio.file.Path, exc: java.io.IOException?): java.nio.file.FileVisitResult {
                    if (exc != null) throw exc
                    java.nio.file.Files.delete(dir)
                    return java.nio.file.FileVisitResult.CONTINUE
                }
            })
            !java.nio.file.Files.exists(directory)
        } else target.delete()
        require(deleted) { "Provider refused to delete this path" }
        return success(path, if (isDirectory) "folder_deleted" else "file_deleted").let {
            JSONObject(it).apply { if (!direct || !isDirectory) put("items", count) }.toString()
        }
    }

    private suspend fun movePath(from: String, to: String): String {
        PathPolicy.requireReadable(from)
        PathPolicy.requireReadable(to)
        val fromParts = PathPolicy.segments(from)
        val toParts = PathPolicy.segments(to)
        requireSourceDestination(toParts.last())
        val oldParent = workspaceReadDirectory(fromParts.dropLast(1))
        val source = requireNotNull(child(oldParent, fromParts.last())) { "Path does not exist" }
        require(!source.isDirectory || toParts.size <= fromParts.size || toParts.take(fromParts.size) != fromParts) {
            "Cannot move a directory into itself"
        }
        val newParent = workspaceReadDirectory(toParts.dropLast(1))
        require(newParent.isDirectory) { "Destination parent is not a directory" }
        val newName = toParts.last()
        if (source.name == newName && oldParent.uri == newParent.uri) {
            return success(to, "unchanged")
        }
        require(child(newParent, newName) == null) { "Destination already exists" }
        if (!permitted("Move: $from → $to")) return denied()
        require(child(workspaceReadDirectory(fromParts.dropLast(1)), fromParts.last())?.uri == source.uri &&
            workspaceReadDirectory(toParts.dropLast(1)).uri == newParent.uri &&
            child(newParent, newName) == null) { "Path changed while awaiting approval" }
        if (source.uri.scheme == "file") {
            val sourcePath = java.io.File(checkNotNull(source.uri.path)).toPath()
            val destinationPath = java.io.File(checkNotNull(newParent.uri.path), newName).toPath()
            java.nio.file.Files.move(sourcePath, destinationPath)
            check(child(oldParent, fromParts.last()) == null && child(newParent, newName) != null) {
                "Could not verify move"
            }
            return success(to, "moved")
        }
        val moved = runCatching {
            when {
                oldParent.uri == newParent.uri -> source.renameTo(newName)
                source.name == newName -> DocumentsContract.moveDocument(resolver, source.uri, oldParent.uri, newParent.uri) != null
                else -> false
            }
        }.getOrDefault(false)
        if (moved) return success(to, "moved")
        val destination = if (source.isDirectory) newParent.createDirectory(newName)
            else newParent.createFile("application/octet-stream", newName)
        checkNotNull(destination) { "Provider refused to create the move destination" }
        try {
            require(destination.name == newName) { "Provider changed the destination name" }
            copyTree(source, destination)
        } catch (error: Exception) {
            runCatching { destination.delete() }
            throw error
        }
        require(source.delete()) { "Copy completed, but the provider refused to delete the source" }
        return success(to, "moved")
    }

    private fun copyTree(source: DocumentFile, destination: DocumentFile) {
        if (source.isDirectory) {
            val (entries, truncated) = children(source, MAX_SCAN, requireValidNames = true)
            require(!truncated) { "Directory exceeds the move limit ($MAX_SCAN entries)" }
            for (entry in entries) {
                val childDoc = checkNotNull(DocumentFile.fromSingleUri(rootContext, documentUri(entry.id)))
                val target = if (entry.directory) destination.createDirectory(entry.name)
                    else destination.createFile("application/octet-stream", entry.name)
                checkNotNull(target) { "Could not copy ${entry.name}" }
                require(target.name == entry.name) { "Provider changed the name of ${entry.name}" }
                copyTree(childDoc, target)
            }
        } else {
            val copied = checkNotNull(resolver.openInputStream(source.uri)) { "Source file unavailable" }.use { input ->
                checkNotNull(resolver.openOutputStream(destination.uri, "rwt")) { "Destination file unavailable" }
                    .use { output -> input.copyTo(output) }
            }
            require(destination.length() == copied) { "Provider did not store the complete copy" }
        }
    }

    private fun inspectDecodeOutput(parts: List<String>, mode: String, exit: Int): JSONObject {
        val extension = if (mode == "jadx") ".java" else ".smali"
        var count = 0
        var visited = 0
        var truncated = false
        var folderFound = false
        val scan = runCatching {
            val folder = child(resolve(parts.dropLast(1)), parts.last())
            if (folder == null || !folder.isDirectory) return@runCatching
            folderFound = true
            val queue = ArrayDeque<DocumentFile>()
            queue.add(folder)
            while (queue.isNotEmpty() && visited < 20000) {
                val (entries, more) = children(queue.removeFirst(), minOf(MAX_SCAN, 20000 - visited))
                truncated = truncated || more
                visited += entries.size
                for (entry in entries) {
                    if (entry.directory) queue.add(checkNotNull(DocumentFile.fromSingleUri(rootContext, documentUri(entry.id))))
                    else if (entry.name.endsWith(extension) && entry.size > 0) count++
                }
            }
            truncated = truncated || queue.isNotEmpty()
        }
        val status = SourceInspection.decodeStatus(exit, count, scan.isSuccess && !truncated)
        return JSONObject().put("status", status).put("sourceFiles", count).put("folderFound", folderFound)
            .put("scanComplete", scan.isSuccess && !truncated)
    }

    private fun countEntries(start: DocumentFile, limit: Int): Int {
        var total = 1
        val queue = ArrayDeque<DocumentFile>()
        queue.add(start)
        while (queue.isNotEmpty() && total < limit) {
            val directory = queue.removeFirst()
            if (!directory.isDirectory) continue
            val (entries, _) = children(directory, MAX_SCAN)
            total += entries.size
            entries.filter { it.directory }.forEach { entry ->
                runCatching { DocumentFile.fromSingleUri(rootContext, documentUri(entry.id)) }.getOrNull()?.let(queue::add)
            }
            if (total >= limit) break
        }
        return total.coerceAtMost(limit)
    }

    private suspend fun searchText(query: String, path: String): String {
        if (path.isNotEmpty()) PathPolicy.requireReadable(path)
        val rootDir = workspaceReadDirectory(PathPolicy.segments(path, allowRoot = true))
        val hits = JSONArray()
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        var scanned = 0
        var visited = 0
        var truncated = false
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        queue.add(rootDir to path)
        search@ while (queue.isNotEmpty()) {
            kotlin.coroutines.coroutineContext.ensureActive()
            if (visited >= MAX_SCAN || android.os.SystemClock.elapsedRealtime() >= deadline) { truncated = true; break }
            val (directory, relPath) = queue.removeFirst()
            val (entries, more) = children(directory, MAX_SCAN - visited)
            if (more) truncated = true
            for (entry in entries) {
                kotlin.coroutines.coroutineContext.ensureActive()
                if (android.os.SystemClock.elapsedRealtime() >= deadline) { truncated = true; break@search }
                visited++
                if (PathPolicy.isSecretName(entry.name)) continue
                val entryPath = if (relPath.isEmpty()) entry.name else "$relPath/${entry.name}"
                val directFile = if (directory.uri.scheme == "file") java.io.File(entry.id) else null
                val uri = directFile?.let(Uri::fromFile) ?: documentUri(entry.id)
                if (entry.directory) {
                    if (entryPath.count { it == '/' } < PathPolicy.MAX_DEPTH) {
                        val childDir = directFile?.let { DocumentFile.fromFile(it) } ?: DocumentFile.fromSingleUri(rootContext, uri)
                        childDir?.let { queue.add(it to entryPath) }
                    } else truncated = true
                    continue
                }
                if (entry.size > MAX_FILE_BYTES) continue
                val bytes = try {
                    resolver.openInputStream(uri)?.use { readPrefix(it, MAX_FILE_BYTES + 1) } ?: continue
                } catch (_: java.io.IOException) { continue }
                scanned++
                if (bytes.size > MAX_FILE_BYTES || bytes.any { it == 0.toByte() }) continue
                val content = try {
                    Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
                } catch (_: java.nio.charset.CharacterCodingException) { continue }
                for ((index, line) in content.lineSequence().withIndex()) {
                    if (line.contains(query, ignoreCase = true)) {
                        hits.put(JSONObject().put("path", entryPath).put("line", index + 1).put("text", line.trim().take(240)))
                        if (hits.length() >= MAX_HITS) { truncated = true; break@search }
                    }
                }
            }
        }
        return JSONObject().put("matches", hits).put("scanned", scanned).put("truncated", truncated)
            .apply { if (truncated) put("hint", "Partial search. Set path to the relevant project or source folder before retrying.") }.toString()
    }

    private fun writeBytes(parent: DocumentFile, file: DocumentFile, bytes: ByteArray) {
        val name = file.name ?: error("File name unavailable")
        val id = java.util.UUID.randomUUID()
        val staged = checkNotNull(parent.createFile("application/octet-stream", "ashura-$id.tmp")) {
            "Could not stage the file"
        }
        val backup = "ashura-$id.bak"
        var committed = false
        try {
            writeBytesAt(staged.uri, bytes)
            require(file.renameTo(backup)) { "Provider cannot safely replace this file" }
            try {
                require(staged.renameTo(name)) { "Could not commit the staged file" }
                committed = true
            } catch (error: Exception) {
                if (!runCatching { file.renameTo(name) }.getOrDefault(false)) {
                    throw IllegalStateException("Could not restore $name. Original retained as $backup", error)
                }
                throw error
            }
            runCatching { file.delete() }
        } finally {
            if (!committed) runCatching { staged.delete() }
        }
    }

    private fun saveChild(
        parent: DocumentFile, name: String, mimeType: String, bytes: ByteArray,
        existing: DocumentFile? = child(parent, name)
    ) {
        if (existing != null) {
            require(existing.isFile) { "Destination is not a file" }
            writeBytes(parent, existing, bytes)
            return
        }
        val created = checkNotNull(parent.createFile(mimeType, name)) { "Could not create $name" }
        try {
            if (created.name != name) {
                // Some SAF providers append an extension based on the MIME type.
                require(child(parent, name) == null) { "Destination appeared during file creation: $name" }
                require(created.renameTo(name)) { "Storage provider could not save the exact filename: $name" }
            }
            require(created.name == name && child(parent, name)?.uri == created.uri) {
                "Storage provider could not save the exact filename: $name"
            }
            writeBytesAt(created.uri, bytes)
        } catch (error: Exception) {
            runCatching { created.delete() }
            throw error
        }
    }

    private fun toolAvailable(name: String): Boolean =
        name in sourceTools || (name == "shell_exec" && directWorkspaceRoot() != null)

    private fun denied() = JSONObject().put("error", "User denied workspace modification").put("denied", true).toString()
    private fun success(path: String, status: String) = JSONObject().put("path", path).put("status", status).toString()

    override fun definitions(): JSONArray = JSONArray().apply {
        fun tool(name: String, description: String, fields: JSONObject, required: List<String>) {
            if (!toolAvailable(name)) return
            put(JSONObject().put("type", "function").put("function", JSONObject().put("name", name)
                .put("description", description).put("parameters", JSONObject().put("type", "object")
                    .put("properties", fields).put("required", JSONArray(required)).put("additionalProperties", false))))
        }
        fun string(description: String) = JSONObject().put("type", "string").put("description", description)
        fun path() = string("Relative slash-separated path inside the selected workspace")
        val checkFields = JSONObject().put("label", string("Short description of the requested change"))
            .put("path", path()).put("text", string("Exact source text to verify")).put("should_exist", JSONObject().put("type", "boolean"))
        tool("plan_source_checks", "Register acceptance checks for a multi-part source edit before writing. Each check is run against saved files at completion.",
            JSONObject().put("checks", JSONObject().put("type", "array").put("minItems", 1).put("maxItems", 12)
                .put("items", JSONObject().put("type", "object").put("properties", checkFields)
                    .put("required", JSONArray(listOf("label", "path", "text", "should_exist"))))), listOf("checks"))
        tool("apk_resources", "Inspect packaged UI resources. Omit options for an inventory, supply layout for XML elements, or string_name and optional locale for an Android-resolved string (locale fallback applies).",
            JSONObject().put("path", path()).put("layout", string("Exact packaged res XML path, or layout name such as @layout/activity_main"))
                .put("string_name", string("String resource entry name")).put("locale", string("BCP 47 language tag")), listOf("path"))
        tool("apk_compare", "Compare two APK manifests, permissions, components, SDKs, versions and size.", JSONObject().put("before", path()).put("after", path()), listOf("before", "after"))
        tool("list_files", "List up to $MAX_ENTRIES immediate children. Continue the same folder with next_offset. Empty path selects workspace root.",
            JSONObject().put("path", path()).put("offset", JSONObject().put("type", "integer").put("minimum", 0)), emptyList())
        tool("read_file", "Read a UTF-8 text file up to $MAX_FILE_BYTES bytes.", JSONObject().put("path", path()), listOf("path"))
        tool("read_lines", "Read 1-based source lines. For large files, reads up to 32 Mi characters per call, clips lines after 512 characters and returns has_more.",
            JSONObject().put("path", path()).put("start_line", JSONObject().put("type", "integer").put("minimum", 1))
                .put("line_count", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 200)),
            listOf("path", "start_line"))
        tool("check_source_text", "Check whether exact text is present or absent in the saved file. Returns passed and the first matching line.",
            JSONObject().put("path", path()).put("text", string("Exact, case-sensitive source text"))
                .put("should_exist", JSONObject().put("type", "boolean")), listOf("path", "text", "should_exist"))
        tool("create_directory", "Create a directory and any missing parents.", JSONObject().put("path", path()), listOf("path"))
        tool("write_file", "Write complete UTF-8 file content, at most $MAX_FILE_BYTES bytes. Creates missing parent folders and replaces an existing file by default.", JSONObject().put("path", path()).put("content", string("Complete file content"))
            .put("overwrite", JSONObject().put("type", "boolean").put("default", true).put("description", "Set false for create-only writes.")), listOf("path", "content"))
        tool("edit_file", "Replace exactly one literal occurrence in a UTF-8 file.", JSONObject().put("path", path()).put("old_text", string("Non-empty exact unique text to replace"))
            .put("new_text", string("Replacement text")), listOf("path", "old_text", "new_text"))
        tool("delete_path", "Delete a file or a folder with everything inside it. The workspace root cannot be deleted.",
            JSONObject().put("path", path()), listOf("path"))
        tool("move_path", "Move or rename a file or folder inside the workspace.",
            JSONObject().put("from", path()).put("to", path()), listOf("from", "to"))
        tool("search_text", "Search text across workspace files. Returns path, line number and the matching line.",
            JSONObject().put("query", string("Case-insensitive text to find")).put("path", path()), listOf("query"))
        tool("apk_overview", "Map an APK without decompiling: zip layout by group, dex files and entry sizes.",
            JSONObject().put("path", path()), listOf("path"))
        tool("apk_manifest", "Decode the binary AndroidManifest.xml: package, version, SDKs, permissions, components, label.",
            JSONObject().put("path", path()), listOf("path"))
        tool("apk_dex", "Inventory one dex: class/method/string counts, class descriptors and method-name search.",
            JSONObject().put("path", path()).put("dex", string("Dex entry name such as classes.dex"))
                .put("query", string("Optional method-name substring")), listOf("path"))
        tool("apk_strings", "Grep printable strings inside an APK (skips images and native libraries).",
            JSONObject().put("path", path()).put("query", string("Case-insensitive substring")), listOf("path", "query"))
        tool("apk_entry_write", "Replace or add one zip entry in an APK and write a new unsigned APK. Content comes from a workspace file.",
            JSONObject().put("path", path()).put("entry", string("Zip entry name to write")).put("source", path())
                .put("output", string("New APK path, e.g. out/patched.apk")), listOf("path", "entry", "source", "output"))
        tool("apk_entry_delete", "Remove one zip entry from an APK and write a new unsigned APK.",
            JSONObject().put("path", path()).put("entry", string("Zip entry name to remove"))
                .put("output", string("New APK path")), listOf("path", "entry", "output"))
        tool("apk_extract_entry", "Copy one zip entry (for example assets/program/basic40.html) out to a workspace file.",
            JSONObject().put("path", path()).put("entry", string("Zip entry name"))
                .put("output", path()), listOf("path", "entry", "output"))
        tool("dex_smali", "Disassemble one class of a dex or APK dex into smali text, without writing files.",
            JSONObject().put("path", path()).put("dex", string("Dex entry name when path is an APK"))
                .put("class", string("Class name or descriptor, e.g. com.example.Main")), listOf("path", "class"))
        tool("smali_assemble", "Assemble a workspace folder of .smali files into a .dex file.",
            JSONObject().put("folder", path()).put("output", path()), listOf("folder", "output"))
        tool("apk_smali_replace", "Assemble a smali folder and replace one dex inside an APK, writing a new unsigned APK.",
            JSONObject().put("path", path()).put("dex", string("Dex entry to replace, e.g. classes.dex"))
                .put("folder", path()).put("output", path()), listOf("path", "dex", "folder", "output"))
        tool("apk_dex_extract", "Disassemble a whole dex of an APK into a smali folder inside the workspace, ready for edits.",
            JSONObject().put("path", path()).put("dex", string("Dex entry, defaults to classes.dex"))
                .put("folder", path()), listOf("path", "folder"))
        tool("dex_set_string", "Replace one dex string in place (same length or shorter, space padded) and write a new unsigned APK.",
            JSONObject().put("path", path()).put("dex", string("Dex entry, defaults to classes.dex"))
                .put("find", string("Exact existing string"))
                .put("replace", string("New string, no longer than the original"))
                .put("output", path()), listOf("path", "find", "replace", "output"))

        tool("hex_view", "Hex and ASCII dump of any workspace file, with offset and length.",
            JSONObject().put("path", path()).put("offset", JSONObject().put("type", "integer").put("description", "Byte offset"))
                .put("length", JSONObject().put("type", "integer").put("description", "Bytes to read, default 512")), listOf("path"))
        tool("read_block", "Read raw bytes of any file as base64 (binary-safe).",
            JSONObject().put("path", path()).put("offset", JSONObject().put("type", "integer"))
                .put("length", JSONObject().put("type", "integer")), listOf("path"))
        tool("replace_asset", "Replace an existing media, font or PDF asset with bytes from a workspace file.", JSONObject().put("path", path()).put("source", path()), listOf("path", "source"))
        tool("write_block", "Overwrite a byte range of an existing file with base64 data.",
            JSONObject().put("path", path()).put("offset", JSONObject().put("type", "integer"))
                .put("base64", string("Base64 bytes to write at that offset")), listOf("path", "offset", "base64"))
        tool("fetch_url", "Fetch an HTTPS page or JSON API and return its text (base64 for binary).",
            JSONObject().put("url", string("HTTPS URL")).put("limit", JSONObject().put("type", "integer").put("description", "Max bytes of body, default 120000")),
            listOf("url"))
        tool("deploy_vercel", "Upload a prepared static website (index.html and assets) to Vercel. Uses the Vercel API token saved locally in Ashura.",
            JSONObject().put("path", path())
                .put("project", string("Project name, optional")).put("target", string("production or preview")), listOf("path"))
        tool("deploy_netlify", "Upload a prepared static website (index.html and assets) to Netlify. Requires a Netlify API token.",
            JSONObject().put("path", path()).put("token", string("Netlify personal access token"))
                .put("site", string("Existing site id, optional")).put("project", string("Site name when creating")), listOf("path", "token"))
        tool("ssh_exec", "Run a shell command on the SSH host (Termux) and return exit code, stdout and stderr.",
            JSONObject().put("command", string("Shell command")).put("timeout_ms", JSONObject().put("type", "integer")), listOf("command"))
        tool("ssh_tools", "Check whether Java, JADX and Apktool are available on the SSH host.", JSONObject(), emptyList())
        tool("ssh_install", "Install packages on the SSH host with pkg install (default: openjdk-17 apktool jadx).",
            JSONObject().put("packages", string("Space separated package list")), emptyList())
        tool("ssh_decompile", "Decode an existing workspace APK into a new Java or smali source folder over SSH.",
            JSONObject().put("apk", path()).put("mode", string("jadx or apktool")).put("output", path()), listOf("apk"))
        tool("shell_checksum", "Compute SHA-256 of a workspace file using Android shell.", JSONObject().put("path", path()), listOf("path"))
        tool("shell_exec", "Run a command with /system/bin/sh in the workspace folder (fast, no SSH). Returns exit code and output.",
            JSONObject().put("command", string("Shell command")).put("timeout_ms", JSONObject().put("type", "integer")), listOf("command"))
        tool("grep", "Recursive text search in the workspace using grep -rn; optional glob filter.",
            JSONObject().put("query", string("Literal text")).put("path", path()).put("glob", string("File pattern, e.g. *.kt")), listOf("query"))
        tool("glob", "Find files by name pattern under the workspace (find -name).",
            JSONObject().put("pattern", string("Name pattern, e.g. *.apk")).put("path", path()), listOf("pattern"))
        tool("skill_list", "List installed skills with their descriptions.", JSONObject(), emptyList())
        tool("skill_read", "Read one installed skill's SKILL.md.", JSONObject().put("name", string("Skill name")), listOf("name"))
        tool("skill_install", "Install a skill from GitHub or any HTTPS URL into .ashura/skills. Accepts a blob URL, a repo URL or a raw file URL.",
            JSONObject().put("url", string("HTTPS URL to a SKILL.md, README, or GitHub repo"))
                .put("name", string("Optional folder name")), listOf("url"))
        tool("skill_remove", "Delete an installed skill folder.", JSONObject().put("name", string("Skill name")), listOf("name"))
    }

    companion object {
        private val sourceTools = setOf(
            "list_files", "read_file", "read_lines", "check_source_text", "plan_source_checks", "create_directory", "write_file", "edit_file", "delete_path",
            "move_path", "search_text", "apk_overview", "apk_manifest", "apk_compare", "apk_resources", "apk_dex", "apk_strings",
            "dex_smali", "apk_dex_extract", "hex_view", "read_block", "write_block", "replace_asset", "shell_checksum", "ssh_tools", "ssh_decompile",
            "skill_list", "skill_read", "deploy_vercel", "deploy_netlify"
        )
        private val binaryAssetExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "ico", "woff", "woff2", "ttf", "otf", "mp3", "ogg", "mp4", "webm", "pdf")
        private val webAssetExtensions = setOf(
            "html", "htm", "css", "js", "mjs", "json", "map", "txt", "xml", "webmanifest",
            "svg", "png", "jpg", "jpeg", "gif", "webp", "avif", "ico", "woff", "woff2", "ttf", "otf",
            "mp3", "ogg", "mp4", "webm", "pdf"
        )
        private val webBuildConfigFiles = setOf("package.json", "deno.json", "vercel.json")
        private val buildArtifactExtensions = setOf("apk", "aab", "apks", "xapk", "apkm", "aar", "dex", "class", "jar", "ipa", "so", "exe", "dll")

        private fun requireSourceDestination(name: String) {
            require(name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) !in buildArtifactExtensions) {
                "Compiled artifact destinations are disabled in source-only mode"
            }
        }
        private val additionalWorkspaceMutations = setOf(
            "apk_entry_write", "apk_entry_delete", "smali_assemble", "apk_smali_replace",
            "apk_dex_extract", "dex_set_string", "apk_extract_entry", "skill_install", "skill_remove"
        )
        const val MAX_FILE_BYTES = 128 * 1024
        const val MAX_APK_BYTES = 96 * 1024 * 1024
        const val MAX_SKILL_BYTES = 256 * 1024
        private const val SIGN_PASSWORD_PLACEHOLDER = ""

        const val MAX_ENTRIES = 200
        const val MAX_HITS = 80
        private const val MAX_SCAN = 2048
    }
}

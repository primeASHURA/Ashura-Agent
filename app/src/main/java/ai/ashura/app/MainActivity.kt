package ai.ashura.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import ai.ashura.app.engine.AgentEngine
import ai.ashura.app.engine.ApkWorkflow
import ai.ashura.app.engine.McpServer
import ai.ashura.app.engine.McpTools
import ai.ashura.app.engine.isMcpEndpoint
import ai.ashura.app.engine.ChatMessage
import ai.ashura.app.engine.ProviderConfig
import ai.ashura.app.engine.ProviderPresets
import ai.ashura.app.engine.WorkspaceTools
import ai.ashura.app.storage.AppStore
import ai.ashura.app.storage.PathPolicy
import ai.ashura.app.storage.SecretVault
import ai.ashura.app.storage.SshClient
import ai.ashura.app.storage.SshConfig
import ai.ashura.app.storage.StoredChat
import ai.ashura.app.storage.StoredPreferences
import ai.ashura.app.storage.WorkspaceEntry
import ai.ashura.app.storage.WorkspaceStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Dark = Palette(
    bg = Color(0xFF151715), panel = Color(0xFF1E211E), raised = Color(0xFF292C28),
    ink = Color(0xFFF1F0E9), muted = Color(0xFF9A9B91), accent = Color(0xFFC5D99B),
    accentInk = Color(0xFF20251B), line = Color(0xFF333631), code = Color(0xFF191B19),
    codeKeyword = Color(0xFFC5D99B), codeString = Color(0xFFB7C9A5),
    codeNumber = Color(0xFFD9B98C), codeComment = Color(0xFFA0A598),
    error = Color(0xFFF2A69F)
)
private val Light = Palette(
    bg = Color(0xFFF3F1E9), panel = Color(0xFFFBFAF5), raised = Color(0xFFE9E7DE),
    ink = Color(0xFF282A25), muted = Color(0xFF5F6359), accent = Color(0xFF52683D),
    accentInk = Color.White, line = Color(0xFFDAD8CE), code = Color(0xFFF0EFE9),
    codeKeyword = Color(0xFF4F6B3C), codeString = Color(0xFF4C6B45),
    codeNumber = Color(0xFF8B6430), codeComment = Color(0xFF63685E),
    error = Color(0xFFA73636)
)
/** Picks skills that match the request so their full text is loaded before the run. */
private fun autoSkillMatches(request: String, skills: List<Pair<String, String>>): List<String> {
    val text = request.lowercase()
    if (Regex("""\b(?:large|big|entire|whole|full)\s+(?:project|repo|codebase)|\b(?:project|repo|codebase)\s+(?:review|analysis)|\b(?:pura|poora|bada)\s+project""")
        .containsMatchIn(text) && skills.any { it.first == "large-project-review" }) return listOf("large-project-review")
    val rules = mapOf(
        "change-review" to listOf("code review", "review code", "review the diff", "security audit", "regression"),
        "feature-transfer" to listOf("port feature", "port this", "migrate feature", "reuse component", "copy feature"),
        "instruction-authoring" to listOf("create skill", "write a skill", "add skill", "skill.md"),
        "feasibility-check" to listOf("feasibility", "prototype", "proof of concept", "compare approaches"),
        "root-cause-analysis" to listOf("debug", "crash", "root cause", "fix", "bug", "not working", "atak"),
        "follow-up-edit" to listOf("follow-up", "follow up", "previous change", "last change", "thoda change", "thora change", "pichla change"),
        "accessibility-review" to listOf("accessibility", "screen reader", "talkback", "keyboard navigation", "text scaling"),
        "apk-reverse" to listOf("apk", "dex", "smali", "manifest", "reverse", "decompile", "jadx", "apktool"),
        "rea-android" to listOf("reverse", "decompile", "jadx", "ghidra", "binary", "analysis"),
        "app-clone" to listOf("clone", "rebrand", "rename app", "package name", "duplicate app"),
        "web-reverse" to listOf("website", "web app", "api", "endpoint", "userscript", "burp", "network"),
        "web-dev" to listOf("html", "css", "javascript", "react", "vue", "frontend", "landing", "portfolio", "tailwind"),
        "app-dev" to listOf("android app", "kotlin", "compose", "flutter", "react native", "swiftui", "gradle"),
        "gsd" to listOf("plan", "multi-step", "phase", "milestone", "refactor", "migration", "feature", "roadmap"),
        "ax" to listOf("orchestrate", "sandbox", "cluster", "task manifest", "fleet")
    )
    return skills.mapNotNull { (name, _) ->
        val keywords = rules[name] ?: listOf(name.replace('-', ' '))
        val hit = keywords.any { text.contains(it) } || text.contains(name.replace('-', ' '))
        if (hit) name else null
    }.take(2)
}
private val Attention = Color(0xFFD8A26A)

/** Builds one MCP management tool definition for the agent surface. */
/** Copies a picked image into app storage, downscaled for chat use. */
private fun saveChatImage(context: android.content.Context, uri: android.net.Uri): String? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1280 && bounds.outHeight / (sample * 2) >= 1280) sample *= 2
    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, options)
    } ?: return null
    val longest = maxOf(decoded.width, decoded.height)
    val scaled = if (longest > 1280) {
        val ratio = 1280f / longest
        android.graphics.Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt(), (decoded.height * ratio).toInt(), true)
    } else decoded
    val directory = java.io.File(context.filesDir, "chat-images").apply { mkdirs() }
    val file = java.io.File(directory, UUID.randomUUID().toString() + ".jpg")
    file.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }
    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()
    return file.absolutePath
}

/** Placeholder tool surface for provider calls that never touch the workspace. */
private val NoWorkspace = object : WorkspaceTools {
    override suspend fun execute(name: String, arguments: JSONObject): String = "{}"
    override fun definitions() = org.json.JSONArray()
    override fun description() = ""
}

private enum class Screen { HOME, PROVIDER, PROJECTS, HISTORY, SETTINGS }
private const val MCP_TOKEN_COMMAND = "/mcp-token"

private fun McpServer.hasVercelDeployToken(): Boolean =
    headers.entries.any { (name, value) ->
        name.equals("Authorization", ignoreCase = true) &&
            value.trim().replaceFirst(Regex("^Bearer(?:\\s+|$)", RegexOption.IGNORE_CASE), "").isNotBlank()
    }

private fun isMcpTokenCommand(input: String): Boolean =
    input.trimStart().takeWhile { !it.isWhitespace() }.equals(MCP_TOKEN_COMMAND, ignoreCase = true)

private val mcpTokenMask = VisualTransformation { input ->
    val tokenStart = Regex("^\\s*\\S+\\s+\\S+\\s+").find(input.text)?.range?.last?.plus(1)
    if (tokenStart == null) TransformedText(input, OffsetMapping.Identity)
    else TransformedText(
        AnnotatedString(input.text.take(tokenStart) + "•".repeat(input.length - tokenStart)),
        OffsetMapping.Identity
    )
}

private enum class WorkMode { CHAT, AGENT }
private data class ApprovalRequest(val summary: String, val response: CompletableDeferred<Boolean>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent { AshuraApp() }
    }
}

@Composable
private fun AshuraApp() {
    val context = LocalContext.current
    val store = remember(context) { AppStore(context) }
    val legacy = remember(context) { context.getSharedPreferences("ashura-settings", 0) }
    var consentEndpoint by remember { mutableStateOf<String?>(null) }
    consentEndpoint?.let { endpoint ->
        AlertDialog(onDismissRequest = { consentEndpoint = null },
            title = { Text("Send data to this provider?") },
            text = { Text("Your messages, conversation history and attached images go to $endpoint. Agent mode may send file excerpts from the workspace. The provider processes this data under its own policies.") },
            confirmButton = { TextButton(onClick = {
                legacy.edit().putString("consented_endpoint", endpoint).apply()
                consentEndpoint = null
            }) { Text("Allow provider") } },
            dismissButton = { TextButton(onClick = { consentEndpoint = null }) { Text("Cancel") } })
    }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var dark by remember { mutableStateOf(true) }
    var ready by remember { mutableStateOf(false) }
    var workspace by remember { mutableStateOf<WorkspaceStore?>(null) }
    var workspaceUri by remember { mutableStateOf("") }
    var workspaceName by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var provider by remember { mutableStateOf(ProviderPresets.openAi) }
    var providers by remember { mutableStateOf(listOf(ProviderPresets.openAi)) }
    var draft by remember { mutableStateOf("") }
    var chats by remember { mutableStateOf(emptyList<StoredChat>()) }
    var activeChatId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf(emptyList<ChatMessage>()) }
    var projectEntries by remember { mutableStateOf(emptyList<WorkspaceEntry>()) }
    var projectPath by remember { mutableStateOf("") }
    var decodeFolder by remember(workspace) { mutableStateOf<String?>(null) }
    var undoPath by remember(workspace) { mutableStateOf<String?>(null) }
    var projectNameInput by remember { mutableStateOf("") }
    var projectBusy by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var agentStatus by remember { mutableStateOf("Ready") }
    var phase by remember { mutableStateOf("idle") }
    var workMode by remember { mutableStateOf(WorkMode.AGENT) }
    var renameTarget by remember { mutableStateOf<StoredChat?>(null) }
    var deleteTarget by remember { mutableStateOf<StoredChat?>(null) }
    var showIntro by rememberSaveable { mutableStateOf(true) }
    var autoApprove by remember { mutableStateOf(legacy.getBoolean("auto_approve_text_edits", false)) }
    var useAgentsMd by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var mcpServers by remember { mutableStateOf<List<McpServer>>(emptyList()) }
    val mcpSettingsLock = remember { Mutex() }
    var savingMcpToken by remember { mutableStateOf(false) }

    suspend fun persistMcpServers(change: (List<McpServer>) -> List<McpServer>) {
        mcpSettingsLock.withLock {
            val updated = change(mcpServers)
            withContext(Dispatchers.IO) { store.saveMcpServers(updated) }
            mcpServers = updated
        }
    }

    fun updateMcpSettings(change: (List<McpServer>) -> List<McpServer>) {
        scope.launch {
            try {
                persistMcpServers(change)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbar.showSnackbar("Could not save MCP settings.")
            }
        }
    }
    val scrollPositions = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    var sshConfig by remember { mutableStateOf(SshConfig()) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { saveChatImage(context, uri) }.getOrNull() }
            if (saved != null) pendingImage = saved else snackbar.showSnackbar("Could not read that image.")
        }
    }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var approval by remember { mutableStateOf<ApprovalRequest?>(null) }
    var apkChoice by remember { mutableStateOf<CompletableDeferred<ApkWorkflow?>?>(null) }
    val agentEvents = remember { mutableStateMapOf<String, List<String>>() }
    val reasoningByMessage = remember { mutableStateMapOf<String, String>() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val colors = if (dark) Dark else Light

    val askApproval: suspend (String) -> Boolean = { summary ->
        run {
            val request = ApprovalRequest(summary, CompletableDeferred())
            withContext(Dispatchers.Main.immediate) { approval = request }
            try {
                val granted = request.response.await()
                granted
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) {
                    if (approval === request) approval = null
                }
            }
        }
    }

    fun bindWorkspace(uri: android.net.Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        workspaceUri = uri.toString()
        workspace = WorkspaceStore(context, uri, { legacy.getBoolean("auto_approve_text_edits", false) }, askApproval)
        workspaceName = workspace?.label().orEmpty()
        scope.launch(Dispatchers.IO) { runCatching { workspace?.ensureBundledSkills() } }
        scope.launch(Dispatchers.IO) {
            store.savePreferences(StoredPreferences(if (dark) "dark" else "light", workspaceUri, provider.id))
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching { bindWorkspace(uri) }
            .onFailure { scope.launch { snackbar.showSnackbar("Could not open that folder. Select another workspace.") } }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            Triple(store.loadPreferences(), store.loadProviders(), store.loadChats())
        }
        val savedPrefs = loaded.first
        val loadedProviders = loaded.second
        chats = loaded.third.sortedByDescending { it.updatedAt }
        mcpServers = withContext(Dispatchers.IO) { runCatching { store.loadMcpServers() }.getOrDefault(McpServer.presets()) }
        sshConfig = withContext(Dispatchers.IO) { runCatching { store.loadSsh() }.getOrDefault(SshConfig()) }
        savedPrefs.workspaceUri.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching {
                val uri = android.net.Uri.parse(raw)
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                workspaceUri = raw
                workspace = WorkspaceStore(context, uri, { legacy.getBoolean("auto_approve_text_edits", false) }, askApproval)
                workspaceName = workspace?.label().orEmpty()
                runCatching { workspace?.ensureBundledSkills() }
            }.onFailure {
                workspace = null
                workspaceUri = ""
                scope.launch { snackbar.showSnackbar("The saved workspace is no longer available. Choose another folder.") }
            }
        }
        val oldId = legacy.getString("provider_id", "openai") ?: "openai"
        val oldProvider = ProviderConfig(
            oldId,
            legacy.getString("provider_name", "OpenAI") ?: "OpenAI",
            legacy.getString("base_url", ProviderPresets.openAi.baseUrl) ?: ProviderPresets.openAi.baseUrl,
            legacy.getString("api_key", null)?.let { runCatching { SecretVault(context).decrypt(it) }.getOrDefault("") } ?: "",
            legacy.getString("model", ProviderPresets.openAi.model) ?: ProviderPresets.openAi.model,
            legacy.getString("protocol", "openai") ?: "openai",
            legacy.getBoolean("reasoning", false)
        )
        providers = loadedProviders.ifEmpty { listOf(oldProvider) }
        provider = providers.firstOrNull { it.id == savedPrefs.selectedProviderId }
            ?: providers.firstOrNull { it.id == oldId } ?: providers.first()
        if (loadedProviders.isEmpty() && oldProvider.apiKey.isNotEmpty()) {
            withContext(Dispatchers.IO) { store.saveProviders(providers) }
        }
        dark = savedPrefs.theme != "light"
        workMode = if (legacy.getString("work_mode", "agent") == "chat") WorkMode.CHAT else WorkMode.AGENT
        useAgentsMd = legacy.getBoolean("use_agents_md", false)
        chats.firstOrNull()?.let {
            activeChatId = it.id
            messages = it.messages
        }
        ready = true
    }

    LaunchedEffect(dark, workspaceUri, provider.id) {
        if (ready) withContext(Dispatchers.IO) {
            store.savePreferences(StoredPreferences(if (dark) "dark" else "light", workspaceUri, provider.id))
        }
    }

    LaunchedEffect(workMode) {
        legacy.edit().putString("work_mode", if (workMode == WorkMode.CHAT) "chat" else "agent").apply()
    }


    LaunchedEffect(useAgentsMd) {
        legacy.edit().putBoolean("use_agents_md", useAgentsMd).apply()
    }



    LaunchedEffect(activeChatId, messages) {
        val id = activeChatId ?: return@LaunchedEffect
        if (messages.isEmpty()) return@LaunchedEffect
        delay(450)
        val title = chats.firstOrNull { it.id == id }?.title
            ?: messages.firstOrNull { it.role == "user" }?.content?.lineSequence()?.firstOrNull()?.take(64)?.takeIf { it.isNotBlank() }
            ?: "New conversation"
        val saved = StoredChat(id, title, messages, System.currentTimeMillis())
        val snapshot = (listOf(saved) + chats.filterNot { it.id == id }).take(80)
        chats = snapshot.sortedByDescending { it.updatedAt }
        try {
            withContext(Dispatchers.IO) { store.saveChats(snapshot) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            snackbar.showSnackbar(error.message ?: "Could not save conversations")
        }
    }

    LaunchedEffect(busy, startedAt) {
        if (busy) {
            while (true) {
                elapsedSeconds = ((android.os.SystemClock.elapsedRealtime() - startedAt) / 1000L).toInt().coerceAtLeast(0)
                delay(1000)
            }
        } else elapsedSeconds = 0
    }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
    SideEffect {
        val window = (context as? ComponentActivity)?.window ?: return@SideEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    val chooseChat: (StoredChat) -> Unit = { selected ->
        activeChatId = selected.id
        messages = selected.messages
        screen = Screen.HOME
        scope.launch { drawerState.close() }
    }
    val newChat: () -> Unit = {
        if (busy) scope.launch { snackbar.showSnackbar("Stop the current response before starting another chat.") }
        else {
            activeChatId = UUID.randomUUID().toString()
            messages = emptyList()
            draft = ""
            screen = Screen.HOME
            scope.launch { drawerState.close() }
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(
        background = colors.bg, surface = colors.panel, primary = colors.accent, onPrimary = colors.accentInk,
        onBackground = colors.ink, onSurface = colors.ink, secondary = colors.muted, outline = colors.line
    ) else lightColorScheme(
        background = colors.bg, surface = colors.panel, primary = colors.accent, onPrimary = colors.accentInk,
        onBackground = colors.ink, onSurface = colors.ink, secondary = colors.muted, outline = colors.line
    )) {
        Surface(Modifier.fillMaxSize(), color = colors.bg) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = workspace != null,
                drawerContent = {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(.90f).widthIn(max = 390.dp).background(colors.panel)
                    ) {
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.Bottom))) {
                            DrawerContent(
                                colors = colors, workspaceName = workspaceName, provider = provider, chats = chats,
                                activeChatId = activeChatId, busy = busy, workMode = workMode,
                                onNew = newChat,
                                onProvider = { screen = Screen.PROVIDER; scope.launch { drawerState.close() } },
                                onProjects = { screen = Screen.PROJECTS; projectPath = ""; scope.launch { drawerState.close() } },
                                onHistory = { screen = Screen.HISTORY; scope.launch { drawerState.close() } },
                                onSettings = { screen = Screen.SETTINGS; scope.launch { drawerState.close() } },
                                onRename = { renameTarget = it },
                                onDelete = { deleteTarget = it },
                                dark = dark, onTheme = { dark = !dark },
                                onChat = chooseChat
                            )
                        }
                    }
                }
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(
                            snackbar,
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                            )
                        )
                    },
                    topBar = {
                        AppTopBar(
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                            ),
                            colors = colors, screen = screen, workspaceName = workspaceName, provider = provider,
                            hasWorkspace = workspace != null, onBack = { screen = Screen.HOME },
                            onMenu = { scope.launch { drawerState.open() } },
                            onProjects = { screen = Screen.PROJECTS; projectPath = "" },
                            onProvider = { screen = Screen.PROVIDER }, onNew = newChat,
                            dark = dark, onTheme = { dark = !dark }
                        )
                    },
                    bottomBar = {
                        if (screen == Screen.HOME && workspace != null) {
                            Composer(
                                colors, draft, busy, workMode, pendingImage,
                                modifier = Modifier.windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                                ),
                                onChange = { draft = it },
                                onMode = { workMode = it },
                                onPickImage = { imagePicker.launch("image/*") },
                                onClearImage = { pendingImage = null },
                                onStop = { activeJob?.cancel(CancellationException("Stopped by user")) },
                                onSend = send@{
                                    val text = draft.trim()
                                    if (savingMcpToken || !ready) return@send
                                    if (isMcpTokenCommand(text)) {
                                        draft = ""
                                        val parts = text.split(Regex("\\s+"), limit = 3)
                                        val preset = McpServer.presets().firstOrNull {
                                            it.id.equals(parts.getOrNull(1), ignoreCase = true) &&
                                                McpTools.webProvider(it.url) != null
                                        }
                                        val raw = parts.getOrNull(2).orEmpty().trim()
                                        val token = if (raw.startsWith("Bearer ", ignoreCase = true)) raw.substring(7).trim() else raw
                                        if (preset == null || token.length !in 1..8192 ||
                                            token.any { it.code !in 33..126 } || raw.any { it == '\r' || it == '\n' }) {
                                            scope.launch { snackbar.showSnackbar("Use /mcp-token <vercel|netlify> <token>.") }
                                            return@send
                                        }
                                        savingMcpToken = true
                                        scope.launch {
                                            val notice = try {
                                                persistMcpServers { servers ->
                                                    val existing = servers.firstOrNull { it.id == preset.id && it.url == preset.url }
                                                    val headers = existing?.headers.orEmpty().filterKeys {
                                                        !it.equals("Authorization", ignoreCase = true)
                                                    } + ("Authorization" to "Bearer $token")
                                                    servers.filterNot { it.id == preset.id } + preset.copy(headers = headers, enabled = true)
                                                }
                                                "${preset.name} token saved for all chats and workspaces."
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                "Could not save the MCP token. Try again or use Settings."
                                            } finally {
                                                savingMcpToken = false
                                            }
                                            snackbar.showSnackbar(notice)
                                        }
                                        return@send
                                    }
                                    val selectedWorkspace = workspace
                                    val attached = pendingImage
                                    if ((text.isNotEmpty() || attached != null) && !busy && selectedWorkspace != null) {
                                        if (legacy.getString("consented_endpoint", null) != provider.baseUrl) {
                                            consentEndpoint = provider.baseUrl
                                            return@send
                                        }
                                        val mode = workMode
                                        draft = ""
                                        pendingImage = null
                                        val userMessage = ChatMessage("user", text, imagePath = attached)
                                        val assistant = ChatMessage("assistant", "")
                                        val history = messages + userMessage
                                        val chatId = activeChatId ?: UUID.randomUUID().toString().also { activeChatId = it }
                                        messages = history + assistant
                                        agentEvents[assistant.id] = emptyList()
                                        busy = true
                                        phase = "thinking"
                                        startedAt = android.os.SystemClock.elapsedRealtime()
                                        activeJob = scope.launch {
                                            val taskService = android.content.Intent(context, ActiveTaskService::class.java)
                                            var streaming = true
                                            val streamHandler = android.os.Handler(android.os.Looper.getMainLooper())
                                            val pendingText = StringBuilder()
                                            val flushText = Runnable {
                                                val batch = synchronized(pendingText) {
                                                    pendingText.toString().also { pendingText.setLength(0) }
                                                }
                                                if (streaming && activeChatId == chatId && batch.isNotEmpty()) {
                                                    messages = messages.map { if (it.id == assistant.id) it.copy(content = it.content + batch) else it }
                                                }
                                            }
                                            try {
                                                // Foreground work starts only after approval prompts and setup complete.
                                                val method = if (mode == WorkMode.AGENT && ApkWorkflow.needsChoice(text)) {
                                                    val choice = CompletableDeferred<ApkWorkflow?>()
                                                    apkChoice = choice
                                                    val selected = try { choice.await() } finally { apkChoice = null }
                                                    if (selected == null) {
                                                        if (activeChatId == chatId) messages = messages.map {
                                                            if (it.id == assistant.id) it.copy(content = "APK task cancelled before inspection.") else it
                                                        }
                                                        return@launch
                                                    }
                                                    agentEvents[assistant.id] = listOf(selected.title)
                                                    selected
                                                } else if (mode == WorkMode.AGENT) {
                                                    ApkWorkflow.continuation(text, history.dropLast(1).lastOrNull { it.role == "user" }?.apkMethod)
                                                } else null
                                                if (method != null && activeChatId == chatId) {
                                                    messages = messages.map { if (it.id == userMessage.id) it.copy(apkMethod = method.name) else it }
                                                }
                                                val projectInstructions = if (mode == WorkMode.AGENT) withContext(Dispatchers.IO) {
                                                    buildString {
                                                        if (method != null) {
                                                            appendLine("User-selected APK method: ${method.title}")
                                                            appendLine(context.assets.open("skills/apk-project-review/SKILL.md").bufferedReader().use { it.readText() })
                                                        }
                                                        if (useAgentsMd) {
                                                            val agents = selectedWorkspace.readTextOrNull("AGENTS.md")
                                                            if (agents == null) {
                                                                snackbar.showSnackbar("AGENTS.md not found in this folder; using default instructions.")
                                                            } else {
                                                                appendLine(agents)
                                                            }
                                                        }
                                                        val skills = if (method == null) runCatching { selectedWorkspace.listSkills() }.getOrDefault(emptyList()) else emptyList()
                                                        if (skills.isNotEmpty()) {
                                                            appendLine()
                                                            appendLine("Installed skills in .ashura/skills. Call skill_read to load one before using it:")
                                                            skills.forEach { (skillName, description) -> appendLine("- $skillName: $description") }
                                                            val matched = autoSkillMatches(text, skills.filterNot { it.first == "unslop" })
                                                            if (matched.isNotEmpty()) {
                                                                appendLine()
                                                                appendLine("=== SKILLS LOADED FOR THIS REQUEST ===")
                                                                matched.forEach { name ->
                                                                    val body = selectedWorkspace.readTextOrNull(".ashura/skills/$name/SKILL.md")
                                                                    if (body != null) {
                                                                        appendLine("--- $name ---")
                                                                        appendLine(body.take(6000))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }.takeIf { it.isNotBlank() }
                                                } else null
                                                selectedWorkspace.sshConfig = sshConfig
                                                val webMcp = McpTools(mcpServers, webDeployOnly = true)
                                                val tools = object : WorkspaceTools {
                                                    override fun definitions() = selectedWorkspace.definitions().apply {
                                                        if (method != null) {
                                                            for (index in length() - 1 downTo 0) {
                                                                if (!method.allows(getJSONObject(index).getJSONObject("function").getString("name"))) remove(index)
                                                            }
                                                        } else {
                                                            put(JSONObject().put("type", "function").put("function", JSONObject()
                                                                .put("name", "mcp_connection_status")
                                                                .put("description", "Check the discovery and authentication results for configured MCP servers. Use this for connection questions.")
                                                                .put("parameters", JSONObject().put("type", "object").put("properties", JSONObject()))))
                                                            val remote = webMcp.jsonDefinitions()
                                                            for (index in 0 until remote.length()) put(remote.getJSONObject(index))
                                                        }
                                                    }
                                                    override fun description() = selectedWorkspace.description()
                                                    override suspend fun execute(name: String, arguments: JSONObject): String {
                                                        if (method != null && !method.allows(name)) return JSONObject().put("error", "Unavailable for the selected APK method").toString()
                                                        withContext(Dispatchers.Main.immediate) {
                                                            val call = "$name(${argSummary(name, arguments)})"
                                                            agentEvents[assistant.id] = (agentEvents[assistant.id].orEmpty() + call).takeLast(30)
                                                        }
                                                        val result = if (name == "mcp_connection_status") {
                                                            withContext(Dispatchers.IO) {
                                                                JSONObject().put("connectionStatus", webMcp.connectionStatus()).toString()
                                                            }
                                                        } else if (name.startsWith("mcp__")) {
                                                            if (askApproval("Run web deployment MCP tool: $name")) {
                                                                webMcp.execute(name, arguments)
                                                                    ?: JSONObject().put("error", "Web deployment tool unavailable").toString()
                                                            } else JSONObject().put("error", "User denied web deployment action").toString()
                                                        } else if (name == "deploy_vercel") {
                                                            val token = mcpServers.firstOrNull { McpTools.webProvider(it.url) == "vercel" }
                                                                ?.headers?.entries?.firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
                                                                ?.value.orEmpty().trim().replaceFirst(Regex("^Bearer(?:\\s+|$)", RegexOption.IGNORE_CASE), "")
                                                            if (token.isBlank()) JSONObject().put("error", "Save a Vercel API token using /mcp-token vercel <token> first.").toString()
                                                            else selectedWorkspace.execute(name, JSONObject(arguments.toString()).put("token", token))
                                                        } else selectedWorkspace.execute(name, arguments)
                                                        withContext(Dispatchers.Main.immediate) {
                                                            val json = runCatching { JSONObject(result) }.getOrNull()
                                                            val verification = json?.optJSONObject("verification")
                                                            if (workspace === selectedWorkspace && verification?.optBoolean("folderFound") == true)
                                                                decodeFolder = json.optString("output")
                                                            if (workspace === selectedWorkspace && name == "edit_file" && json?.optString("status") == "edited")
                                                                undoPath = json.optString("path")
                                                            if (workspace === selectedWorkspace && name == "write_file" && json?.optString("status") == "written")
                                                                undoPath = json.optString("path").takeIf { json.optBoolean("undoAvailable") }
                                                            val decodeSummary = verification?.let { "${it.optString("status")} · ${it.optInt("sourceFiles")} source files" }
                                                            val detail = when {
                                                                json == null -> ""
                                                                json.has("error") -> json.optString("error").ifBlank { "Tool failed" }
                                                                verification != null && verification.optString("status") != "complete" -> decodeSummary.orEmpty()
                                                                name == "check_source_text" && !json.optBoolean("passed") -> "Source check did not match"
                                                                json.has("exit") && json.optInt("exit") != 0 -> "Exit code ${json.optInt("exit")}"
                                                                json.optInt("status") >= 400 -> "HTTP ${json.optInt("status")}"
                                                                else -> ""
                                                            }.take(140)
                                                            val routineMiss = detail.startsWith("Missing component:", ignoreCase = true) ||
                                                                detail.startsWith("Path does not exist", ignoreCase = true) ||
                                                                detail.startsWith("old_text was not found", ignoreCase = true) ||
                                                                detail == "Source check did not match"
                                                            val failed = detail.isNotEmpty() && !routineMiss && detail != "null"
                                                            val summary = runCatching {
                                                                val json = JSONObject(result)
                                                                when {
                                                                    json.has("error") -> json.optString("error")
                                                                    decodeSummary != null -> decodeSummary
                                                                    json.has("output") -> json.optString("output").lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                                                                    json.has("matches") -> {
                                                                        val count = json.optJSONArray("matches")?.length() ?: json.optInt("matches")
                                                                        if (count > 0) "$count matches" else ""
                                                                    }
                                                                    json.has("count") -> "${json.optInt("count")} files"
                                                                    json.has("bytes") -> "${json.optInt("bytes")} bytes"
                                                                    json.has("status") -> json.optString("status")
                                                                    json.has("exit") -> "exit ${json.optInt("exit")}"
                                                                    else -> ""
                                                                }.lineSequence().firstOrNull().orEmpty().trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty().take(90)
                                                            }.getOrDefault("")
                                                            val toolLine = if (failed) "! ${toolLabel(name)} · ${detail.ifBlank { "failed" }}"
                                                                else "✓ ${toolLabel(name)} · ${(if (routineMiss || detail == "null") "" else summary).ifBlank { "done" }}"
                                                            agentEvents[assistant.id] = (agentEvents[assistant.id].orEmpty() + toolLine).takeLast(30)
                                                        }
                                                        return result
                                                    }
                                                }
                                                if (BuildConfig.DEBUG) context.startForegroundService(taskService)
                                                val answer = AgentEngine(tools, { event ->
                                                    if (event == "draft:reset") synchronized(pendingText) {
                                                        pendingText.setLength(0)
                                                        streamHandler.removeCallbacks(flushText)
                                                    }
                                                    streamHandler.post {
                                                        if (streaming && activeChatId == chatId) {
                                                            when {
                                                                event == "draft:reset" -> messages = messages.map {
                                                                    if (it.id == assistant.id && it.content.isNotEmpty()) it.copy(content = "") else it
                                                                }
                                                                event.startsWith("reason:") -> {
                                                                    val text = event.removePrefix("reason:")
                                                                    reasoningByMessage[assistant.id] =
                                                                        (reasoningByMessage[assistant.id].orEmpty() + text).takeLast(6000)
                                                                }
                                                                event.startsWith("phase:tool:") -> phase = "tool"
                                                                event == "phase:thinking" -> phase = "thinking"
                                                                event == "phase:working" -> phase = "working"
                                                                event == "phase:done" -> phase = "done"
                                                            }
                                                        }
                                                    }
                                                }, agentic = mode == WorkMode.AGENT, instructions = projectInstructions,
                                                    designGuidance = if (mode == WorkMode.AGENT) withContext(Dispatchers.IO) {
                                                        context.assets.open("skills/unslop/SKILL.md").bufferedReader().use { it.readText() }
                                                    } else "").run(provider, history) { delta ->
                                                    synchronized(pendingText) {
                                                        if (pendingText.isEmpty()) streamHandler.postDelayed(flushText, 40)
                                                        pendingText.append(delta)
                                                    }
                                                }
                                                streaming = false
                                                if (activeChatId == chatId) {
                                                    messages = messages.map { if (it.id == assistant.id) it.copy(content = answer) else it }
                                                }
                                            } catch (cancelled: CancellationException) {
                                                streaming = false
                                                if (activeChatId == chatId) {
                                                    phase = "stopped"
                                                    agentEvents[assistant.id] = (agentEvents[assistant.id].orEmpty() + "Run stopped by you").takeLast(30)
                                                }
                                                throw cancelled
                                            } catch (error: Exception) {
                                                streaming = false
                                                if (activeChatId == chatId) {
                                                    val safeMessage = error.message?.take(300) ?: "Check provider settings and connection."
                                                    messages = messages.map {
                                                        if (it.id == assistant.id) it.copy(content = "I couldn't complete that request. $safeMessage") else it
                                                    }
                                                }
                                            } finally {
                                                streamHandler.removeCallbacks(flushText)
                                                if (BuildConfig.DEBUG) context.stopService(taskService)
                                                busy = false
                                                activeJob = null
                                                if (phase != "stopped") phase = "idle"
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) { padding ->
                    when (screen) {
                        Screen.HOME -> when {
                            workspace == null -> OnboardingScreen(
                                colors, dark, provider.name,
                                onChooseFolder = { folderPicker.launch(null) },
                                onProvider = { screen = Screen.PROVIDER },
                                modifier = Modifier.padding(padding)
                            )
                            messages.isEmpty() -> EmptyChat(colors, workspaceName, provider.model,
                                onAsk = { draft = it }, onProjects = { screen = Screen.PROJECTS; projectPath = "" },
                                modifier = Modifier.padding(padding))
                            else -> Conversation(
                                messages, colors, busy, phase, elapsedSeconds, agentEvents, reasoningByMessage,
                                scrollPositions, activeChatId ?: "new",
                                onSuggestion = { draft = it },
                                modifier = Modifier.padding(padding)
                            )
                        }
                        Screen.PROVIDER -> ProviderScreen(
                            colors, provider, providers,
                            onSelect = { selected -> provider = selected },
                            onSave = { config ->
                                provider = config
                                providers = (listOf(config) + providers.filterNot { it.id == config.id }).take(20)
                                scope.launch(Dispatchers.IO) { store.saveProviders(providers) }
                                screen = Screen.HOME
                                scope.launch { snackbar.showSnackbar("${config.name} is ready") }
                            },
                            onFetchModels = { config -> AgentEngine(NoWorkspace, {}).fetchModels(config) },
                            Modifier.padding(padding).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        )
                        Screen.PROJECTS -> ProjectsScreen(
                            colors, workspaceName, projectPath, projectEntries, projectNameInput, projectBusy,
                            onPathChange = { projectPath = it }, onNameChange = { projectNameInput = it },
                            onOpen = { path ->
                                projectPath = path
                                scope.launch {
                                    val result = runCatching { workspace?.list(path).orEmpty() }
                                    result.onSuccess { projectEntries = it }
                                    result.exceptionOrNull()?.let { snackbar.showSnackbar("Could not list this folder.") }
                                }
                            },
                            onLoad = {
                                scope.launch {
                                    val result = runCatching { workspace?.list(projectPath).orEmpty() }
                                    result.onSuccess { projectEntries = it }
                                    result.exceptionOrNull()?.let { snackbar.showSnackbar("Could not read this folder. Check workspace permissions.") }
                                }
                            },                            onCreate = {
                                val name = projectNameInput.trim()
                                if (!projectBusy && name.isNotBlank()) scope.launch {
                                    projectBusy = true
                                    val result = runCatching {
                                        PathPolicy.segments(name).also { require(it.size == 1) { "Use a single folder name." } }
                                        workspace?.execute("create_directory", JSONObject().put("path", listOf(projectPath, name).filter(String::isNotBlank).joinToString("/")))
                                    }
                                    val error = result.exceptionOrNull()?.message
                                        ?: result.getOrNull()?.let { runCatching { JSONObject(it).optString("error").takeIf(String::isNotBlank) }.getOrNull() }
                                    if (error == null) {
                                        projectNameInput = ""
                                        runCatching { workspace?.list(projectPath).orEmpty() }.onSuccess { projectEntries = it }
                                        snackbar.showSnackbar("Project folder created")
                                    } else snackbar.showSnackbar(error)
                                    projectBusy = false
                                }
                            },
                            onChat = { draft = "Explore the project in $it and explain its structure."; screen = Screen.HOME },
                            modifier = Modifier.padding(padding).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                            decodeFolder = decodeFolder, undoPath = undoPath,
                            onUndo = {
                                val target = workspace
                                if (target != null && !busy && !projectBusy) scope.launch {
                                    projectBusy = true
                                    try {
                                        val restored = target.undoEdit()
                                        if (workspace === target) undoPath = null
                                        snackbar.showSnackbar("Undid edit to $restored")
                                    } catch (error: Exception) {
                                        if (error is CancellationException) throw error
                                        snackbar.showSnackbar(error.message ?: "Undo failed")
                                    } finally { projectBusy = false }
                                }
                            }, undoEnabled = !busy && !projectBusy
                        )
                        Screen.HISTORY -> HistoryScreen(colors, chats, activeChatId, onOpen = chooseChat, Modifier.padding(padding))
                        Screen.SETTINGS -> SettingsScreen(
                            colors = colors,
                            dark = dark,
                            provider = provider,
                            workspaceName = workspaceName,
                            hasWorkspace = workspace != null,
                            workMode = workMode,
                            autoApprove = autoApprove,
                            onAutoApprove = { enabled ->
                                autoApprove = enabled
                                legacy.edit().putBoolean("auto_approve_text_edits", enabled).apply()
                            },
                            useAgentsMd = useAgentsMd,
                            chatCount = chats.size,
                            onTheme = { dark = !dark },
                            onProvider = { screen = Screen.PROVIDER },
                            onWorkspace = { folderPicker.launch(null) },
                            onDefaultMode = { workMode = it },
                            onUseAgentsMd = { useAgentsMd = it },
                            workspace = workspace,
                            mcpServers = mcpServers,
                            onMcpToggle = { server, enabled ->
                                updateMcpSettings { servers -> servers.map { if (it.id == server.id) it.copy(enabled = enabled) else it } }
                            },
                            onMcpAdd = { id, name, url, headers ->
                                val serverId = id ?: name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                                    .ifBlank { "mcp-${System.currentTimeMillis() % 100000}" }
                                updateMcpSettings { servers ->
                                    val existing = servers.firstOrNull { it.id == serverId }
                                    val mergedHeaders = if (headers.isEmpty() && existing != null && existing.url == url) existing.headers else headers
                                    val server = McpServer(serverId, name, url, mergedHeaders,
                                        enabled = existing?.enabled ?: true, preset = existing?.preset ?: false)
                                    servers.filterNot { it.id == serverId } + server
                                }
                            },
                            onMcpRemove = { id ->
                                updateMcpSettings { servers -> servers.filterNot { it.id == id } }
                            },
                            sshConfig = sshConfig,
                            onSshSave = { cfg -> sshConfig = cfg; scope.launch(Dispatchers.IO) { store.saveSsh(cfg) } },
                            onMcpRefresh = {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        val status = McpTools(mcpServers, webDeployOnly = true).connectionStatus()
                                        if (mcpServers.any { McpTools.webProvider(it.url) == "vercel" && it.hasVercelDeployToken() }) {
                                            "Vercel direct deploy: token saved. Deploy from Agent mode.\n\nMCP discovery\n$status"
                                        } else status
                                    }
                                        .getOrElse { "refresh failed: ${it.message?.take(120)}" }
                                }
                            },
                            onInstallAgentsMd = {
                                val target = workspace
                                if (target == null) {
                                    scope.launch { snackbar.showSnackbar("Choose a workspace folder first.") }
                                } else scope.launch {
                                    val template = withContext(Dispatchers.IO) {
                                        runCatching {
                                            context.assets.open("AGENTS.md").bufferedReader().use { it.readText() }
                                        }.getOrNull()
                                    }
                                    if (template == null) {
                                        snackbar.showSnackbar("Template missing from this build.")
                                    } else {
                                        val ok = target.writeRootText("AGENTS.md", template)
                                        if (ok) useAgentsMd = true
                                        snackbar.showSnackbar(if (ok) "AGENTS.md written to the workspace" else "Could not create AGENTS.md. Check whether the file already exists.")
                                    }
                                }
                            },
                            onClearChats = {
                                val removedImages = (chats.flatMap { it.messages } + messages).mapNotNull(ChatMessage::imagePath)
                                activeJob?.cancel()
                                chats = emptyList()
                                if (activeChatId != null) { activeChatId = null; messages = emptyList() }
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { store.saveChats(emptyList(), removedImages) }
                                        snackbar.showSnackbar("All conversations removed")
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        snackbar.showSnackbar(error.message ?: "Could not clear conversations")
                                    }
                                }
                            },
                            modifier = Modifier.padding(padding).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        )
                    }
                }
            }
            apkChoice?.let { choice ->
                AlertDialog(onDismissRequest = { choice.complete(null) }, containerColor = colors.panel,
                    title = { Text("How should Ashura inspect this app?") },
                    text = {
                        Column {
                            TextButton(onClick = { choice.complete(ApkWorkflow.INSPECT) }) { Text(ApkWorkflow.INSPECT.title) }
                            Text("Read package details, permissions and UI resources.", color = colors.muted)
                            TextButton(onClick = { choice.complete(ApkWorkflow.DECOMPILE) }) { Text(ApkWorkflow.DECOMPILE.title) }
                            Text("Use the configured SSH host to generate Java or smali files.", color = colors.muted)
                        }
                    }, confirmButton = { TextButton(onClick = { choice.complete(null) }) { Text("Cancel") } })
            }
            approval?.let { request ->
                AlertDialog(
                    onDismissRequest = { android.util.Log.i("AshuraApproval", "dismissed"); request.response.complete(false); approval = null },
                    containerColor = colors.panel,
                    title = { Text("Approve agent action", color = colors.ink) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(request.summary, color = colors.muted, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp))
                            Spacer(Modifier.height(10.dp))
                            Text("Hold the approve control to confirm. A single touch is ignored.", color = colors.muted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    },
                    confirmButton = {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.raised)
                                .pointerInput(request) {
                                    detectTapGestures(onLongPress = {
                                        android.util.Log.i("AshuraApproval", "approve-hold")
                                        request.response.complete(true)
                                        approval = null
                                    })
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text("Hold to approve", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { android.util.Log.i("AshuraApproval", "deny-click"); request.response.complete(false); approval = null }) {
                            Text("Deny", color = colors.muted)
                        }
                    }
                )
            }
            renameTarget?.let { chat ->
                var title by remember(chat.id) { mutableStateOf(chat.title) }
                AlertDialog(
                    onDismissRequest = { renameTarget = null },
                    containerColor = colors.panel,
                    title = { Text("Rename conversation", color = colors.ink) },
                    text = {
                        OutlinedTextField(
                            value = title, onValueChange = { title = it.take(80) }, singleLine = true,
                            label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(colors)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val updated = title.trim().ifBlank { "New conversation" }
                            chats = chats.map { if (it.id == chat.id) it.copy(title = updated, updatedAt = System.currentTimeMillis()) else it }
                            scope.launch(Dispatchers.IO) { store.saveChats(chats) }
                            renameTarget = null
                        }) { Text("Rename", color = colors.accent) }
                    },
                    dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = colors.muted) } }
                )
            }
            deleteTarget?.let { chat ->
                AlertDialog(
                    onDismissRequest = { deleteTarget = null },
                    containerColor = colors.panel,
                    title = { Text("Delete conversation", color = colors.ink) },
                    text = { Text("“${chat.title.ifBlank { "New conversation" }}” will be removed from this device.", color = colors.muted, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp)) },
                    confirmButton = {
                        TextButton(onClick = {
                            val current = if (activeChatId == chat.id) messages else emptyList()
                            val removedImages = (chat.messages + current).mapNotNull(ChatMessage::imagePath)
                            val retained = chats.filterNot { it.id == chat.id }
                            chats = retained
                            if (activeChatId == chat.id) {
                                activeJob?.cancel()
                                activeChatId = null
                                messages = emptyList()
                            }
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { store.saveChats(retained, removedImages) }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    snackbar.showSnackbar(error.message ?: "Could not delete conversation")
                                }
                            }
                            deleteTarget = null
                        }) { Text("Delete", color = Attention) }
                    },
                    dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = colors.muted) } }
                )
            }
            if (showIntro) {
                SplashOverlay(onSettings = { showIntro = false; screen = Screen.SETTINGS }) { showIntro = false }
            }
        }
    }
}

@Composable
private fun AppTopBar(
    modifier: Modifier = Modifier,
    colors: Palette, screen: Screen, workspaceName: String, provider: ProviderConfig, hasWorkspace: Boolean,
    onBack: () -> Unit, onMenu: () -> Unit, onProjects: () -> Unit, onProvider: () -> Unit,
    onNew: () -> Unit, dark: Boolean, onTheme: () -> Unit
) {
    Column(modifier.fillMaxWidth().background(colors.bg)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (screen == Screen.HOME) Modifier.background(Brush.horizontalGradient(listOf(colors.bg, colors.bg, colors.accent.copy(alpha = .13f)))) else Modifier)
            .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (screen == Screen.HOME) {
                IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Open navigation", tint = colors.ink) }
                BrandMark(colors)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ashura AI", color = colors.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(provider.model, color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (hasWorkspace) IconButton(onClick = onNew) { Icon(Icons.Default.Add, "New chat", tint = colors.muted) }
                IconButton(onClick = onProvider) { Icon(Icons.Default.Tune, "Model settings", tint = colors.muted) }
            } else {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.ink) }
                Text(screenTitle(screen), color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (screen == Screen.PROVIDER) Icon(Icons.Default.Tune, null, tint = colors.accent, modifier = Modifier.padding(end = 14.dp).size(18.dp))
                if (screen == Screen.PROJECTS) Icon(Icons.Default.FolderOpen, null, tint = colors.accent, modifier = Modifier.padding(end = 14.dp).size(18.dp))
                if (screen == Screen.SETTINGS) Icon(Icons.Default.Settings, null, tint = colors.accent, modifier = Modifier.padding(end = 14.dp).size(18.dp))
            }
        }
        if (hasWorkspace && screen == Screen.HOME) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).workspaceSurface(colors, 24.dp).heightIn(min = 44.dp).clickable(onClick = onProjects).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(workspaceName, color = colors.ink, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ChevronRight, null, tint = colors.muted, modifier = Modifier.size(15.dp))
            }
        }
        HorizontalDivider(color = colors.line, thickness = .5.dp)
    }
}

@Composable
private fun DrawerContent(
    colors: Palette, workspaceName: String, provider: ProviderConfig, chats: List<StoredChat>, activeChatId: String?,
    busy: Boolean, workMode: WorkMode, onNew: () -> Unit, onProvider: () -> Unit,
    onProjects: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit,
    onRename: (StoredChat) -> Unit, onDelete: (StoredChat) -> Unit, dark: Boolean, onTheme: () -> Unit, onChat: (StoredChat) -> Unit
) {
    val titleOf = { chat: StoredChat -> chat.title.ifBlank { "New conversation" } }
    var menuFor by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ashura_mark), null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(60.dp).clip(CircleShape).border(2.dp, colors.accent.copy(alpha = .45f), CircleShape))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Ashura AI", color = colors.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Coding workspace", color = colors.muted, fontSize = 12.sp)
            }
            Box(Modifier.clip(RoundedCornerShape(18.dp)).background(colors.accent.copy(alpha = .12f)).padding(horizontal = 8.dp, vertical = 5.dp)) {
                Text(if (workMode == WorkMode.AGENT) "Agent" else "Chat", color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.accent.copy(alpha = .09f))
                .clickable(onClick = onProjects).padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = colors.accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(workspaceName.ifBlank { "Choose workspace" }, color = colors.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(provider.name, color = colors.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = colors.muted, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onNew, modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.accentInk)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp))
            Text("New conversation", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(14.dp))
        DrawerAction(Icons.Default.Settings, "Settings & tools", colors, onSettings)
        DrawerAction(Icons.Default.Tune, "Model & provider", colors, onProvider)
        DrawerAction(Icons.Default.History, "All chats", colors, onHistory)
        Spacer(Modifier.height(12.dp)); HorizontalDivider(color = colors.line.copy(alpha = .7f)); Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, tint = colors.muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Recent", color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f)); Text("${chats.size}", color = colors.muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        if (chats.isEmpty()) {
            Text("No conversations yet.", color = colors.muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp))
        } else {
            chats.take(5).forEach { chat ->
                val active = chat.id == activeChatId
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (active) colors.accent.copy(alpha = .14f) else colors.raised.copy(alpha = .6f))
                        .clickable(enabled = !busy, onClick = { onChat(chat) })
                        .padding(start = 10.dp, end = 2.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, null, tint = colors.muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(titleOf(chat), color = colors.ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text("${chat.messages.size} messages", color = colors.muted, fontSize = 9.sp)
                    }
                    Box {
                        IconButton(onClick = { menuFor = if (menuFor == chat.id) null else chat.id }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreHoriz, "Conversation actions", tint = colors.muted, modifier = Modifier.size(17.dp))
                        }
                        DropdownMenu(
                            expanded = menuFor == chat.id,
                            onDismissRequest = { menuFor = null },
                            containerColor = colors.panel,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename", color = colors.ink, fontSize = 12.sp) },
                                onClick = { menuFor = null; onRename(chat) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Attention, fontSize = 12.sp) },
                                onClick = { menuFor = null; onDelete(chat) }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp))
                .background(colors.raised).clickable(onClick = onTheme).heightIn(min = 48.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (dark) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = colors.accent)
            Spacer(Modifier.width(10.dp))
            Text("Appearance", Modifier.weight(1f), color = colors.ink, fontSize = 14.sp)
            Text(if (dark) "Dark" else "Light", color = colors.muted, fontSize = 13.sp)
            Icon(Icons.Default.ExpandMore, null, tint = colors.muted, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun DrawerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, colors: Palette, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(7.dp)).clickable(onClick = action).padding(horizontal = 8.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.muted, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(11.dp))
        Text(title, Modifier.weight(1f), color = colors.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.ChevronRight, null, tint = colors.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun OnboardingScreen(
    colors: Palette, dark: Boolean, providerName: String, onChooseFolder: () -> Unit, onProvider: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandMark(colors)
        Spacer(Modifier.height(16.dp))
        Text("Choose a workspace", color = colors.ink, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Open a project folder to review code, make edits, and see each action in chat.", color = colors.muted, fontSize = 15.sp, lineHeight = 23.sp)
        Spacer(Modifier.height(24.dp))
        InfoRow(colors, Icons.Default.FolderOpen, "Project files", "Ashura uses the folder you select.")
        Spacer(Modifier.height(9.dp)); InfoRow(colors, Icons.Default.Tune, "Connect your model", "$providerName · OpenAI-compatible providers · custom endpoints")
        Spacer(Modifier.height(23.dp))
        Button(onClick = onChooseFolder, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(9.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.accentInk)) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text("Choose workspace folder", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(9.dp)); OutlinedButton(onClick = onProvider, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(9.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink)) { Text("Configure provider", fontSize = 13.sp) }
    }
}

@Composable
private fun InfoRow(colors: Palette, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().workspaceSurface(colors).padding(16.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.padding(top = 1.dp).size(19.dp)); Spacer(Modifier.width(12.dp))
        Column { Text(title, color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.height(4.dp)); Text(subtitle, color = colors.muted, fontSize = 13.sp, lineHeight = 20.sp) }
    }
}

@Composable
private fun EmptyChat(
    colors: Palette, workspaceName: String, model: String, onAsk: (String) -> Unit, onProjects: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Work on this project", color = colors.ink, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Use Agent mode to review or edit workspace files. Use Chat for questions.", color = colors.muted, fontSize = 15.sp, lineHeight = 23.sp)
        Spacer(Modifier.height(24.dp))
        listOf(
            "Explore this project and explain how it works" to Icons.Default.Code,
            "Plan a feature and implement it in this workspace" to Icons.Default.Widgets,
            "Review the project for a bug" to Icons.Default.Tune
        ).forEach { (label, icon) ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).workspaceSurface(colors)
                .clickable { onAsk(label) }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(12.dp)); Text(label, color = colors.ink, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f)); Icon(Icons.Default.ArrowUpward, null, tint = colors.muted, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp)); Row(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onProjects).padding(vertical = 7.dp, horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FolderOpen, null, tint = colors.muted, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(7.dp)); Text(workspaceName, color = colors.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.width(6.dp)); Text("· $model", color = colors.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Composer(
    colors: Palette, value: String, busy: Boolean, workMode: WorkMode, imagePath: String?, modifier: Modifier = Modifier,
    onChange: (String) -> Unit, onMode: (WorkMode) -> Unit, onPickImage: () -> Unit, onClearImage: () -> Unit,
    onStop: () -> Unit, onSend: () -> Unit
) {
    Column(modifier.fillMaxWidth().background(colors.bg).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeChip("Chat", workMode == WorkMode.CHAT, colors, enabled = !busy) { onMode(WorkMode.CHAT) }
            Spacer(Modifier.width(6.dp))
            ModeChip("Agent", workMode == WorkMode.AGENT, colors, enabled = !busy) { onMode(WorkMode.AGENT) }
            Spacer(Modifier.weight(1f))
            Text(
                if (workMode == WorkMode.AGENT) "Workspace tools on" else "Answers only",
                color = colors.muted, fontSize = 10.sp
            )
        }
        if (imagePath != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.panel)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bitmap = remember(imagePath) {
                    runCatching { android.graphics.BitmapFactory.decodeFile(imagePath) }.getOrNull()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(), contentDescription = "Selected image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("Image attached · ask anything about it", color = colors.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onClearImage, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, "Remove image", tint = colors.muted, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().workspaceSurface(colors, 16.dp)
                .padding(start = 9.dp, top = 14.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                Modifier.padding(bottom = 4.dp).size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (imagePath != null) colors.accent else colors.raised)
                    .clickable(enabled = !busy, onClick = onPickImage),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add, "Attach image",
                    tint = when {
                        busy -> colors.muted.copy(alpha = .5f)
                        imagePath != null -> colors.accentInk
                        else -> colors.accent
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value, onValueChange = onChange,
                modifier = Modifier.weight(1f).heightIn(min = 46.dp, max = 168.dp).padding(bottom = 9.dp, top = 2.dp),
                textStyle = TextStyle(color = colors.ink, fontSize = 15.5.sp, lineHeight = 23.sp), cursorBrush = SolidColor(colors.accent),
                visualTransformation = if (isMcpTokenCommand(value)) mcpTokenMask else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send,
                    keyboardType = if (isMcpTokenCommand(value)) KeyboardType.Password else KeyboardType.Text),
                keyboardActions = KeyboardActions(onSend = { if (!busy) onSend() }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) Text(
                            if (busy) "Ashura is working…"
                            else if (workMode == WorkMode.AGENT) "Describe the task"
                            else "Ask anything or attach an image",
                            color = colors.muted, fontSize = 14.sp
                        )
                        inner()
                    }
                },
                enabled = !busy
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.padding(bottom = 4.dp).size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (busy) colors.raised else if (value.isBlank() && imagePath == null) colors.raised else colors.accent)
                    .clickable(onClick = if (busy) onStop else onSend), contentAlignment = Alignment.Center
            ) {
                Icon(if (busy) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                    if (busy) "Stop response" else "Send message",
                    tint = if (busy || (value.isBlank() && imagePath == null)) colors.muted else colors.accentInk,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, colors: Palette, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(7.dp))
            .background(if (selected) colors.raised else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(colors.accent))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (selected) colors.ink else colors.muted, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun Conversation(
    messages: List<ChatMessage>, colors: Palette, busy: Boolean, phase: String, elapsed: Int,
    events: SnapshotStateMap<String, List<String>>, reasoning: Map<String, String>,
    scrollMemory: MutableMap<String, Pair<Int, Int>>, chatKey: String,
    onSuggestion: (String) -> Unit, modifier: Modifier = Modifier
) {
    val state = key(chatKey) {
        rememberLazyListState(
            initialFirstVisibleItemIndex = (scrollMemory[chatKey]?.first ?: 0).coerceAtMost(messages.lastIndex.coerceAtLeast(0)),
            initialFirstVisibleItemScrollOffset = scrollMemory[chatKey]?.second ?: 0
        )
    }
    DisposableEffect(state, chatKey) {
        onDispose { scrollMemory[chatKey] = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
    }
    val lastId = messages.lastOrNull()?.id
    val lastContent = messages.lastOrNull()?.content
    val latestEvents = messages.lastOrNull()?.let { events[it.id] }.orEmpty()
    LaunchedEffect(lastId, lastContent, latestEvents, busy) {
        val layout = state.layoutInfo
        val visibleTail = layout.visibleItemsInfo.lastOrNull()
        val nearBottom = visibleTail == null || (visibleTail.index >= layout.totalItemsCount - 2 &&
            visibleTail.offset + visibleTail.size <= layout.viewportEndOffset + 120)
        if (nearBottom && !state.isScrollInProgress && messages.isNotEmpty()) {
            val tailOffset = if (visibleTail != null && visibleTail.index == messages.lastIndex) {
                (visibleTail.size - (layout.viewportEndOffset - layout.viewportStartOffset) + layout.afterContentPadding)
                    .coerceAtLeast(0)
            } else 0
            state.scrollToItem(messages.lastIndex, tailOffset)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(), state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            when (message.role) {
                "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Column(
                        Modifier.widthIn(max = 360.dp).workspaceSurface(colors, 16.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        message.imagePath?.let { path ->
                            val bitmap = remember(path) {
                                runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull()
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Attached image",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(10.dp))
                                )
                                if (message.content.isNotBlank()) Spacer(Modifier.height(9.dp))
                            }
                        }
                        if (message.content.isNotBlank()) SelectableText(message.content, colors.ink, 15, 23)
                    }
                }
                "assistant" -> Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(colors, true)
                        Spacer(Modifier.width(8.dp))
                        Text("Ashura", color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    val messageEvents = events[message.id].orEmpty()
                    val active = busy && message.id == lastId
                    val messageReasoning = reasoning[message.id].orEmpty()
                    if (messageEvents.isNotEmpty() || messageReasoning.isNotBlank() || active) {
                        Spacer(Modifier.height(12.dp))
                        AgentActivity(messageEvents, active, phase, elapsed, messageReasoning,
                            draft = if (active) message.content else "", colors = colors)
                    }
                    if (!active && message.content.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Column(Modifier.fillMaxWidth().workspaceSurface(colors, 18.dp).padding(14.dp)) {
                            MarkdownContent(message.content, colors)
                            if (!busy) {
                                val clipboard = LocalClipboardManager.current
                                val shareContext = LocalContext.current
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(modifier = Modifier.weight(1f),
                                        onClick = { clipboard.setText(AnnotatedString(message.content)) }) { Text("Copy") }
                                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                            .putExtra(Intent.EXTRA_TEXT, message.content)
                                        shareContext.startActivity(Intent.createChooser(send, null))
                                    }) { Text("Share") }
                                }
                                ReportResponse(message.content)
                            }
                        }
                    } else if (!busy && message.id == lastId && messageEvents.none { it == "Run stopped by you" }) {
                        Spacer(Modifier.height(12.dp))
                        Text("The provider returned no response text.", color = colors.muted, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                    if (message.id == lastId && !busy && message.content.isNotBlank()) {
                        val prompt = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
                        val chips = remember(prompt, message.content) { suggestionsFor(prompt, messageEvents) }
                        if (chips.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            SuggestionRow(chips, colors, onSuggestion)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentActivity(events: List<String>, active: Boolean, phase: String, elapsed: Int, reasoning: String, draft: String, colors: Palette) {
    val steps = remember(events) { pairActivity(events) }
    val failures = steps.count { it.failed }
    val stopped = events.any { it == "Run stopped by you" }
    var expandedOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val expanded = expandedOverride ?: active
    val label = when {
        active && phase == "tool" -> steps.lastOrNull()?.title ?: "Running a workspace action"
        active && phase == "working" -> "Working"
        active -> "Preparing response"
        stopped -> "Run stopped"
        failures > 0 -> "Activity · $failures failed"
        steps.size == 1 -> "Activity · 1 step"
        steps.isNotEmpty() -> "Activity · ${steps.size} steps"
        else -> "Thinking"
    }
    val indicator = if (failures > 0 || stopped) colors.error else colors.accent
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(indicator.copy(alpha = .11f)).heightIn(min = 44.dp)
                .clickable(onClickLabel = if (expanded) "Hide activity" else "Show activity") { expandedOverride = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(indicator))
            Text(label, Modifier.weight(1f), color = colors.ink, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (active) Text(formatElapsed(elapsed), color = colors.muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                tint = colors.muted, modifier = Modifier.size(20.dp))
        }
        if (expanded && (steps.isNotEmpty() || reasoning.isNotBlank() || draft.isNotBlank())) {
            Column(Modifier.fillMaxWidth().workspaceSurface(colors, 18.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (steps.isNotEmpty()) ActivitySection("Steps", colors) {
                    steps.forEach { step -> ActivityStepRow(step, active, colors) }
                }
                if (reasoning.isNotBlank()) {
                    SelectableText(reasoning.takeLast(2400), colors.muted, 13, 20)
                }
                if (draft.isNotBlank()) Text(draft.takeLast(3000), color = colors.ink, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

private data class ActivityStep(val title: String, val command: String, val result: String = "", val failed: Boolean = false)

/** Pairs a command line with the result line that follows it. */
private fun pairActivity(events: List<String>): List<ActivityStep> {
    val steps = ArrayList<ActivityStep>()
    events.forEach { raw ->
        when {
            raw.startsWith("✓ ") || raw.startsWith("! ") -> {
                val failed = raw.startsWith("!")
                val text = raw.drop(2)
                val last = steps.lastOrNull()
                if (last != null && last.result.isEmpty()) steps[steps.lastIndex] = last.copy(result = text, failed = failed)
                else steps += ActivityStep(text, "", text, failed)
            }
            raw != "Run stopped by you" -> {
                val command = raw.removePrefix("› ")
                steps += ActivityStep(toolTitle(command), command)
            }
        }
    }
    return steps
}

private fun toolTitle(command: String): String {
    val name = command.substringBefore('(').substringAfterLast('/').lowercase()
    return when (name) {
        "write_file" -> "Write file"
        "read_file" -> "Read file"
        "read_lines" -> "Read source lines"
        "check_source_text" -> "Check saved source"
        "edit_file" -> "Edit file"
        "delete_path" -> "Delete path"
        "move_path" -> "Move path"
        "create_directory" -> "Create folder"
        "list_files" -> "List files"
        "search_text" -> "Search text"
        else -> command.substringBefore('(').ifBlank { "Action" }
    }
}

@Composable
private fun ActivitySection(title: String, colors: Palette, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = colors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ActivityStepRow(step: ActivityStep, active: Boolean, colors: Palette) {
    val running = active && step.result.isEmpty()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 2.dp).size(17.dp).clip(RoundedCornerShape(5.dp))
                .background(
                    when {
                        step.failed -> colors.error.copy(alpha = .15f)
                        step.result.isNotEmpty() -> colors.accent.copy(alpha = .16f)
                        else -> colors.raised
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                step.failed -> Text("!", color = colors.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                step.result.isNotEmpty() -> Icon(Icons.Default.Check, null, tint = colors.accent, modifier = Modifier.size(11.dp))
                running -> CircularProgressIndicator(Modifier.size(9.dp), strokeWidth = 1.5.dp, color = colors.accent)
                else -> Icon(Icons.Default.MoreHoriz, null, tint = colors.muted, modifier = Modifier.size(11.dp))
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(step.title, color = if (step.failed) colors.error else colors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (step.command.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    step.command,
                    color = colors.muted,
                    fontSize = 12.sp, lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(colors.panel.copy(alpha = .6f))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            if (step.result.isNotBlank() && !step.result.equals(step.title, ignoreCase = true)) {
                Spacer(Modifier.height(3.dp))
                Text(step.result, color = if (step.failed) colors.error else colors.muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun MarkdownContent(text: String, colors: Palette) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        blocks.forEach { block ->
            if (block.code) CodePanel(block.language, block.text, colors)
            else TextBlock(block.text, colors)
        }
    }
}

/** Paragraphs, lists, headings and pipe tables inside one non-code block. */
@Composable
private fun TextBlock(text: String, colors: Palette) {
    // Some models emit a heading glued to the previous sentence; split it before layout.
    val lines = remember(text) {
        text.lines().flatMap { raw -> raw.split(Regex("(?<=[^\\s])(?=#{2,3} )")) }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.trimStart().startsWith("|") && index + 1 < lines.size &&
                lines[index + 1].trimStart().startsWith("|")
            ) {
                val rows = ArrayList<String>()
                while (index < lines.size && lines[index].trimStart().startsWith("|")) {
                    rows += lines[index]; index++
                }
                MarkdownTable(rows, colors)
                Spacer(Modifier.height(4.dp))
                continue
            }
            when {
                line.isBlank() -> if (index > 0 && lines[index - 1].isNotBlank()) Spacer(Modifier.height(8.dp))
                line.startsWith("> ") -> {
                    val quote = line.removePrefix("> ")
                    Row(Modifier.fillMaxWidth().workspaceSurface(colors, 8.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        SelectableText(quote, colors.muted, 15, 24, annotated = inlineMarkdown(quote, colors))
                    }
                }
                line.startsWith("### ") -> SelectableText(line.removePrefix("### "), colors.ink, 16, 23, style = SpanStyle(fontWeight = FontWeight.SemiBold), annotated = inlineMarkdown(line.removePrefix("### "), colors), modifier = Modifier.padding(top = 8.dp))
                line.startsWith("## ") -> SelectableText(line.removePrefix("## "), colors.ink, 18, 26, style = SpanStyle(fontWeight = FontWeight.SemiBold), annotated = inlineMarkdown(line.removePrefix("## "), colors), modifier = Modifier.padding(top = 10.dp))
                line.startsWith("# ") -> SelectableText(line.removePrefix("# "), colors.ink, 20, 28, style = SpanStyle(fontWeight = FontWeight.SemiBold), annotated = inlineMarkdown(line.removePrefix("# "), colors), modifier = Modifier.padding(top = 10.dp))
                line.startsWith("- ") || line.startsWith("* ") -> Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.Top) {
                    Text("•", color = colors.accent, fontSize = 14.sp); Spacer(Modifier.width(9.dp))
                    SelectableText(line.drop(2), colors.ink, 15, 24, Modifier.weight(1f), annotated = inlineMarkdown(line.drop(2), colors))
                }
                line.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val split = line.indexOf('.')
                    val body = line.substring(split + 1).trim()
                    Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.Top) {
                        Text(line.substring(0, split + 1), color = colors.accent, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        SelectableText(body, colors.ink, 15, 24, Modifier.weight(1f), annotated = inlineMarkdown(body, colors))
                    }
                }
                else -> SelectableText(line, colors.ink, 15, 24, annotated = inlineMarkdown(line, colors))
            }
            index++
        }
    }
}

@Composable
private fun MarkdownTable(rows: List<String>, colors: Palette) {
    val clipboard = LocalClipboardManager.current
    val linkPattern = remember { Regex("""https?://[^\s<>`\])]+""") }
    val cells = rows.map { row -> row.trim().trim('|').split('|').map(String::trim) }
    val body = cells.filterNot { row -> row.all { it.isEmpty() || it.all { ch -> ch == '-' || ch == ':' } } }
    if (body.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().workspaceSurface(colors).padding(12.dp)
    ) {
        body.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                row.forEachIndexed { cellIndex, cell ->
                    val link = linkPattern.find(cell)?.value
                    Row(Modifier.weight(if (cellIndex == 0) 1.1f else 1f).padding(end = 4.dp),
                        verticalAlignment = Alignment.Top) {
                    Text(
                        inlineMarkdown(cell, colors),
                        color = colors.ink, fontSize = 13.sp, lineHeight = 20.sp,
                        fontWeight = if (rowIndex == 0) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (link != null) {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(link)) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.ContentCopy, "Copy link", tint = colors.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                    }
                }
            }
            if (rowIndex < body.lastIndex) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = colors.line.copy(alpha = .55f), thickness = .5.dp)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CodePanel(language: String, source: String, colors: Palette) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(source) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().workspaceSurface(colors)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Code, null, tint = colors.accent, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(7.dp))
            Text(language.ifBlank { "Code" }, color = colors.muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(AnnotatedString(source)); copied = true }) {
                Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null, tint = colors.muted, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)); Text(if (copied) "Copied" else "Copy", color = colors.muted, fontSize = 10.sp)
            }
        }
        HorizontalDivider(color = colors.line.copy(alpha = .7f), thickness = .5.dp)
        Row(Modifier.fillMaxWidth().background(colors.code).horizontalScroll(rememberScrollState()).padding(12.dp)) {
            SelectionContainer {
                Text(
                    remember(source, language, colors) { highlightCode(source, language, colors) },
                    color = colors.ink, fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp, lineHeight = 21.sp, softWrap = false
                )
            }
        }
    }
}

private val CODE_KEYWORDS = setOf(
    "fun", "val", "var", "if", "else", "when", "for", "while", "return", "class", "object",
    "interface", "data", "import", "package", "private", "public", "internal", "override",
    "suspend", "try", "catch", "finally", "throw", "in", "is", "as", "null", "true", "false",
    "let", "const", "async", "await", "function", "new", "this", "super", "def", "elif",
    "except", "with", "lambda", "from", "raise", "pass", "yield", "None", "True", "False",
    "struct", "enum", "impl", "fn", "mut", "pub", "use", "match", "loop", "trait", "self",
    "Self", "int", "float", "double", "char", "void", "bool", "string", "static", "final",
    "extends", "implements", "throws", "switch", "case", "break", "continue", "default",
    "do", "typeof", "instanceof", "type", "namespace", "export", "select", "go", "defer",
    "chan", "map", "nil", "echo", "print", "println", "and", "or", "not", "end"
)

/** Lightweight token coloring so code replies stay readable without a parser dependency. */
private fun highlightCode(source: String, language: String, colors: Palette): AnnotatedString {
    val builder = AnnotatedString.Builder(source)
    if (language.equals("diff", ignoreCase = true) || language.equals("patch", ignoreCase = true)) {
        var offset = 0
        source.split('\n').forEach { line ->
            val tint = when {
                line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") -> colors.codeNumber
                line.startsWith("+") -> colors.accent
                line.startsWith("-") -> colors.error
                else -> null
            }
            if (tint != null) builder.addStyle(SpanStyle(color = tint, background = tint.copy(alpha = .10f)), offset, offset + line.length)
            offset += line.length + 1
        }
        return builder.toAnnotatedString()
    }
    val keyword = SpanStyle(color = colors.codeKeyword, fontWeight = FontWeight.Medium)
    val stringStyle = SpanStyle(color = colors.codeString)
    val numberStyle = SpanStyle(color = colors.codeNumber)
    val commentStyle = SpanStyle(color = colors.codeComment)
    val hashComments = language.lowercase() !in setOf("css", "html", "xml", "yaml", "yml", "scss")
    val length = source.length
    var index = 0
    while (index < length) {
        val char = source[index]
        val next = if (index + 1 < length) source[index + 1] else '\u0000'
        if ((char == '/' && next == '/') || (char == '#' && hashComments)) {
            val lineEnd = source.indexOf('\n', index).let { if (it < 0) length else it }
            builder.addStyle(commentStyle, index, lineEnd)
            index = lineEnd
            continue
        }
        if (char == '"' || char == '\'' || char == '`') {
            var cursor = index + 1
            while (cursor < length) {
                if (source[cursor] == '\\') cursor += 2
                else if (source[cursor] == char) { cursor++; break }
                else cursor++
            }
            val end = cursor.coerceAtMost(length)
            builder.addStyle(stringStyle, index, end)
            index = end
            continue
        }
        if (char.isDigit()) {
            var cursor = index
            while (cursor < length && (source[cursor].isLetterOrDigit() || source[cursor] == '.' || source[cursor] == '_')) cursor++
            builder.addStyle(numberStyle, index, cursor)
            index = cursor
            continue
        }
        if (char.isLetter() || char == '_') {
            var cursor = index
            while (cursor < length && (source[cursor].isLetterOrDigit() || source[cursor] == '_')) cursor++
            if (source.substring(index, cursor) in CODE_KEYWORDS) builder.addStyle(keyword, index, cursor)
            index = cursor
            continue
        }
        index++
    }
    return builder.toAnnotatedString()
}

@Composable
private fun SelectableText(
    text: String, color: Color, size: Int, lineHeight: Int, modifier: Modifier = Modifier,
    style: SpanStyle = SpanStyle(), annotated: AnnotatedString? = null
) {
    val base = SpanStyle(color = color, fontSize = size.sp)
    val content = annotated ?: AnnotatedString(text, base)
    val styled = if (style == SpanStyle()) content else buildAnnotatedString {
        append(content)
        addStyle(style, 0, this.length)
    }
    SelectionContainer(modifier) {
        Text(styled, color = color, fontSize = size.sp, lineHeight = lineHeight.sp)
    }
}

private data class MarkdownBlock(val code: Boolean, val language: String, val text: String)

private fun parseMarkdownBlocks(value: String): List<MarkdownBlock> {
    val result = ArrayList<MarkdownBlock>()
    val parts = value.split("```")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 0) {
            if (part.isNotEmpty()) result += MarkdownBlock(false, "", part.trim('\n'))
        } else {
            val newline = part.indexOf('\n')
            val firstLine = if (newline >= 0) part.substring(0, newline).trim() else ""
            val body = if (newline >= 0) part.substring(newline + 1) else part
            result += MarkdownBlock(true, firstLine.take(24), body.trimEnd())
        }
    }
    return result
}

private fun inlineMarkdown(line: String, colors: Palette): AnnotatedString {
    val out = AnnotatedString.Builder()
    var cursor = 0
    val pattern = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[(.+?)]\\((.+?)\\)")
    pattern.findAll(line).forEach { match ->
        out.append(line.substring(cursor, match.range.first))
        val strong = match.groups[1]?.value
        val code = match.groups[2]?.value
        val linkText = match.groups[3]?.value
        if (strong != null) {
            out.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.ink))
            out.append(strong.replace("`", ""))
            out.pop()
        } else if (code != null) {
            out.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = colors.accent, background = colors.raised))
            out.append(code)
            out.pop()
        } else if (linkText != null) {
            out.pushStyle(SpanStyle(color = colors.accent))
            out.append(linkText)
            out.pop()
        }
        cursor = match.range.last + 1
    }
    out.append(line.substring(cursor))
    return out.toAnnotatedString()
}

@Composable
private fun ProviderScreen(
    colors: Palette, current: ProviderConfig, saved: List<ProviderConfig>, onSelect: (ProviderConfig) -> Unit,
    onSave: (ProviderConfig) -> Unit, onFetchModels: suspend (ProviderConfig) -> List<String>,
    modifier: Modifier = Modifier
) {
    var selectedId by remember(current.id) { mutableStateOf(current.id) }
    var name by remember(current.id) { mutableStateOf(current.name) }
    var endpoint by remember(current.id) { mutableStateOf(current.baseUrl) }
    var model by remember(current.id) { mutableStateOf(current.model) }
    var key by remember(current.id) { mutableStateOf(current.apiKey) }
    var protocol by remember(current.id) { mutableStateOf(current.protocol) }
    var reasoning by remember(current.id) { mutableStateOf(current.reasoning) }
    var showKey by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModels by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf("") }
    val fetchScope = rememberCoroutineScope()
    fun draft() = ProviderConfig(selectedId.ifBlank { "custom" }, name.trim(), endpoint.trim(), key, model.trim(), protocol, reasoning)
    val presets = listOf(ProviderPresets.openAi, ProviderPresets.anthropic, ProviderPresets.gemini, ProviderPresets.deepSeek, ProviderPresets.groq, ProviderPresets.openRouter, ProviderPresets.mistral, ProviderPresets.xAi)
    val options = (presets.map { preset -> saved.firstOrNull { it.id == preset.id } ?: preset } + saved.filter { stored -> presets.none { it.id == stored.id } }).distinctBy { it.id }
    fun load(config: ProviderConfig) {
        selectedId = config.id; name = config.name; endpoint = config.baseUrl; model = config.model; key = config.apiKey; protocol = config.protocol; reasoning = config.reasoning; onSelect(config)
    }
    Column(modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(colors.accent.copy(alpha = .10f)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, null, tint = colors.accent)
                    Spacer(Modifier.width(12.dp))
                    Text("Choose the provider and model used in chat.", color = colors.ink, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            item {
                SectionLabel("Provider", colors)
                Spacer(Modifier.height(8.dp))
                options.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            ProviderOption(option, option.id == selectedId, colors, Modifier.weight(1f)) { load(option) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            item { SettingsField("Provider name", name, colors, onValue = { name = it }) }
            item { SettingsField("API endpoint", endpoint, colors, onValue = { endpoint = it }, hint = "https://api.example.com/v1") }
            item { SettingsField("Model ID", model, colors, onValue = { model = it }, hint = "Model identifier from the provider") }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            fetchError = ""
                            fetching = true
                            fetchScope.launch {
                                try {
                                    val list = onFetchModels(draft())
                                    models = list
                                    if (list.isEmpty()) fetchError = "This provider returned no models." else showModels = true
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    fetchError = e.message?.take(180) ?: "Could not load models."
                                } finally {
                                    fetching = false
                                }
                            }
                        },
                        enabled = !fetching && endpoint.isNotBlank() && key.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink)
                    ) {
                        if (fetching) CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp, color = colors.accent)
                        else Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if (fetching) "Fetching…" else "Fetch models", fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    if (fetchError.isNotBlank()) Text(fetchError, color = Attention, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f))
                }
            }
            item {
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key") }, placeholder = { Text("Stored encrypted on this device") },
                    singleLine = true, visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show", color = colors.muted, fontSize = 10.sp) } },
                    colors = fieldColors(colors)
                )
            }
            item {
                SectionLabel("API protocol", colors); Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProtocolOption("OpenAI-compatible", protocol == "openai", colors, Modifier.weight(1f)) { protocol = "openai" }
                    ProtocolOption("Anthropic Messages", protocol == "anthropic", colors, Modifier.weight(1f)) { protocol = "anthropic" }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.panel).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Reasoning effort", color = colors.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium); Text("Only sent when this model supports it; private reasoning stays hidden.", color = colors.muted, fontSize = 10.sp, lineHeight = 15.sp) }
                    Switch(reasoning, { reasoning = it }, colors = SwitchDefaults.colors(checkedThumbColor = colors.accentInk, checkedTrackColor = colors.accent))
                }
            }
            item {
                Text("Gemini uses Google's OpenAI-compatible API. Custom endpoints must use HTTPS, except local loopback.", color = colors.muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
        HorizontalDivider(color = colors.line, thickness = .5.dp)
        Button(
            onClick = { onSave(ProviderConfig(selectedId.ifBlank { "custom" }, name.trim(), endpoint.trim(), key, model.trim(), protocol, reasoning)) },
            enabled = name.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).height(48.dp),
            shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.accentInk)
        ) { Text("Save provider", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
    }
    if (showModels) {
        AlertDialog(
            onDismissRequest = { showModels = false },
            containerColor = colors.panel,
            title = { Text("Available models", color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(models) { id ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp))
                                .clickable { model = id; showModels = false }
                                .padding(horizontal = 8.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(id, color = if (id == model) colors.accent else colors.ink, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            if (id == model) Icon(Icons.Default.Check, null, tint = colors.accent, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModels = false }) { Text("Close", color = colors.accent) } }
        )
    }
}

@Composable
private fun ProviderOption(config: ProviderConfig, selected: Boolean, colors: Palette, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.heightIn(min = 64.dp).clip(shape).background(if (selected) colors.accent.copy(alpha = .10f) else colors.panel)
        .border(1.dp, if (selected) colors.accent else colors.line, shape)
        .clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val logo = when (android.net.Uri.parse(config.baseUrl).host?.lowercase()) {
            "api.openai.com" -> R.drawable.provider_openai
            "api.anthropic.com" -> R.drawable.provider_anthropic
            "generativelanguage.googleapis.com" -> R.drawable.provider_gemini
            "api.deepseek.com" -> R.drawable.provider_deepseek
            "api.groq.com" -> R.drawable.provider_groq
            "openrouter.ai" -> R.drawable.provider_openrouter
            "api.mistral.ai" -> R.drawable.provider_mistral
            "api.x.ai" -> R.drawable.provider_xai
            else -> null
        }
        Box(Modifier.size(32.dp).background(if (logo == null) colors.raised else Color.White, CircleShape),
            contentAlignment = Alignment.Center) {
            if (logo != null) Image(painterResource(logo), null, modifier = Modifier.size(23.dp))
            else Icon(Icons.Default.Widgets, null, tint = colors.accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(config.name, color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(config.model, color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.padding(start = 4.dp).size(14.dp)
            .border(1.dp, if (selected) colors.accent else colors.line, CircleShape),
            contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Default.Check, "Selected", tint = colors.accent, modifier = Modifier.size(11.dp))
        }
    }
}

@Composable
private fun ProtocolOption(label: String, selected: Boolean, colors: Palette, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 48.dp).workspaceSurface(colors)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (selected) colors.ink else colors.muted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f)); if (selected) Icon(Icons.Default.Check, "Selected", tint = colors.accent, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsField(label: String, value: String, colors: Palette, onValue: (String) -> Unit, hint: String = "") {
    OutlinedTextField(value, onValue, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, placeholder = { if (hint.isNotBlank()) Text(hint) }, singleLine = true, colors = fieldColors(colors))
}

@Composable
private fun ProjectsScreen(
    colors: Palette, workspaceName: String, path: String, entries: List<WorkspaceEntry>, name: String, busy: Boolean,
    onPathChange: (String) -> Unit, onNameChange: (String) -> Unit, onOpen: (String) -> Unit,
    onLoad: () -> Unit, onCreate: () -> Unit, onChat: (String) -> Unit, modifier: Modifier = Modifier,
    decodeFolder: String?, undoPath: String?, onUndo: () -> Unit, undoEnabled: Boolean
) {
    LaunchedEffect(path) { onLoad() }
    Column(modifier.fillMaxSize()) {
        if (decodeFolder != null && decodeFolder != path) TextButton(onClick = { onOpen(decodeFolder) }) { Text("Open decompile output") }
        if (undoPath != null) TextButton(onClick = onUndo, enabled = undoEnabled) {
            Text("Undo edit · $undoPath", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).workspaceSurface(colors).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(workspaceName, color = colors.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(path.ifBlank { "Workspace root" }, color = colors.muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (path.isNotBlank()) TextButton(onClick = { onOpen(path.substringBeforeLast('/', "")) }) { Text("Up", color = colors.accent) }
            IconButton(onClick = onLoad) { Icon(Icons.Default.History, "Refresh", tint = colors.muted) }
        }
        HorizontalDivider(color = colors.line, thickness = .5.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, onNameChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text("New project folder") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), colors = fieldColors(colors))
            Spacer(Modifier.width(8.dp)); Button(onClick = onCreate, enabled = !busy && name.isNotBlank(), modifier = Modifier.height(51.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.accentInk)) { if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = colors.accentInk) else Icon(Icons.Default.Add, "Create folder", modifier = Modifier.size(18.dp)) }
        }
        if (entries.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.FolderOpen, null, tint = colors.muted, modifier = Modifier.size(26.dp)); Spacer(Modifier.height(10.dp)); Text("No items in this folder", color = colors.ink, fontSize = 13.sp); Spacer(Modifier.height(4.dp)); Text("Create a project folder or ask Ashura to explore it.", color = colors.muted, fontSize = 11.sp) }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(entries, key = { it.path }) { entry ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).workspaceSurface(colors, 8.dp).clickable { if (entry.isDirectory) onOpen(entry.path) else onChat(entry.path) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (entry.isDirectory) Icons.Default.FolderOpen else Icons.Default.Code, null, tint = colors.accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(entry.name, color = colors.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); if (!entry.isDirectory) Text(android.text.format.Formatter.formatShortFileSize(LocalContext.current, entry.size), color = colors.muted, fontSize = 12.sp) }
                    Icon(Icons.Default.ChevronRight, null, tint = colors.muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(colors: Palette, chats: List<StoredChat>, activeChatId: String?, onOpen: (StoredChat) -> Unit, modifier: Modifier = Modifier) {
    if (chats.isEmpty()) Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No conversations yet", color = colors.muted, fontSize = 15.sp) }
    else LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chats, key = { it.id }) { chat ->
            Column(Modifier.fillMaxWidth().workspaceSurface(colors).clickable { onOpen(chat) }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(chat.title.ifBlank { "New conversation" }, color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); if (chat.id == activeChatId) Text("Current", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(4.dp)); Text("${chat.messages.size} messages · ${android.text.format.DateFormat.format("MMM d, HH:mm", chat.updatedAt)}", color = colors.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, colors: Palette) {
    Text(text, color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun BrandMark(colors: Palette, small: Boolean = false) {
    val size = if (small) 30.dp else 36.dp
    Box(
        Modifier.size(size).clip(CircleShape).background(colors.raised)
            .border(1.dp, colors.line.copy(alpha = .55f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ashura_mark),
            contentDescription = "Ashura AI",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape)
        )
    }
}

@Composable
private fun SplashOverlay(onSettings: () -> Unit, onStart: () -> Unit) {
    val green = Color(0xFF216437)
    val ink = Color(0xFF111618)
    val muted = Color(0xFF51575B)
    val center = androidx.compose.ui.text.style.TextAlign.Center
    Box(Modifier.fillMaxSize().background(Color(0xFFFAFCFA))) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            fun ribbon(y: Float, bend: Float, color: Color, thickness: Float) {
                val path = Path().apply {
                    moveTo(-w * .05f, h * y)
                    cubicTo(w * .33f, h * (y - bend), w * .49f, h * (y + bend), w * 1.05f, h * (y - bend * .8f))
                }
                drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(thickness.dp.toPx()))
            }
            ribbon(.31f, .14f, green.copy(alpha = .09f), 58f)
            ribbon(.34f, .14f, green.copy(alpha = .07f), 22f)
            ribbon(.91f, .12f, green.copy(alpha = .18f), 52f)
            ribbon(.94f, .11f, green.copy(alpha = .65f), 22f)
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painterResource(R.drawable.ashura_mark), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Ashura AI", Modifier.weight(1f), color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Surface(shape = CircleShape, color = Color.White, shadowElevation = 3.dp) {
                        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = ink) }
                    }
                }
                Spacer(Modifier.height(34.dp))
                Box(
                    Modifier.size(150.dp).background(Brush.radialGradient(listOf(Color(0xFFBCEBCC), Color.Transparent)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painterResource(R.drawable.ashura_mark), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(124.dp).clip(CircleShape).border(2.dp, Color(0xFF9CDBAE), CircleShape)
                    )
                }
                Spacer(Modifier.height(13.dp))
                Text(buildAnnotatedString {
                    append("Ashura ")
                    pushStyle(SpanStyle(color = green))
                    append("AI")
                    pop()
                }, color = ink, fontSize = 43.sp, lineHeight = 49.sp, fontWeight = FontWeight.Bold, textAlign = center)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(60.dp).height(3.dp).background(green, CircleShape))
                Spacer(Modifier.height(12.dp))
                Text("Open a project folder to start coding with Ashura.", color = muted, fontSize = 16.sp, lineHeight = 22.sp, textAlign = center)
                Spacer(Modifier.height(25.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(Icons.Default.FolderOpen, "Open Folder", "Start your project"),
                        Triple(Icons.Default.Code, "Code Faster", "With Ashura"),
                        Triple(Icons.Default.Bolt, "Boost Productivity", "Like never before")
                    ).forEach { (icon, title, subtitle) ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 2.dp) {
                            Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, null, modifier = Modifier.size(23.dp), tint = green)
                                Spacer(Modifier.height(5.dp))
                                Text(title, color = ink, fontSize = 11.sp, lineHeight = 14.sp, textAlign = center)
                                Text(subtitle, color = muted, fontSize = 10.sp, lineHeight = 13.sp, textAlign = center)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(25.dp))
                val buttonShape = RoundedCornerShape(40.dp)
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).heightIn(min = 56.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFF368548), Color(0xFF19502D))), buttonShape),
                    shape = buttonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)
                ) {
                    val bounce = rememberInfiniteTransition(label = "Welcome rocket")
                    val lift by bounce.animateFloat(
                        initialValue = 0f, targetValue = -4f,
                        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                        label = "Rocket lift"
                    )
                    Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.offset(y = lift.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Get started", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(9.dp))
                    Icon(Icons.Default.ChevronRight, null)
                }
                Spacer(Modifier.height(27.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(Modifier.weight(1f), color = green.copy(alpha = .2f))
                    Text("Build  ?  Code  ?  Innovate", color = muted, fontSize = 12.sp)
                    HorizontalDivider(Modifier.weight(1f), color = green.copy(alpha = .2f))
                }
                Spacer(Modifier.height(45.dp))
            }
        }
    }
}

@Composable
private fun fieldColors(colors: Palette) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = colors.ink, unfocusedTextColor = colors.ink, focusedBorderColor = colors.accent,
    unfocusedBorderColor = colors.line, focusedLabelColor = colors.accent, unfocusedLabelColor = colors.muted,
    cursorColor = colors.accent, focusedPlaceholderColor = colors.muted, unfocusedPlaceholderColor = colors.muted
)

private fun screenTitle(screen: Screen) = when (screen) {
    Screen.HOME -> "Ashura AI"
    Screen.PROVIDER -> "Model & provider"
    Screen.PROJECTS -> "Projects"
    Screen.HISTORY -> "Chat history"
    Screen.SETTINGS -> "Settings"
}

private fun formatElapsed(seconds: Int): String = if (seconds < 60) "${seconds}s" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** Next-step chips derived from the request and what the run actually did. */
private fun suggestionsFor(prompt: String, events: List<String>): List<String> {
    val text = prompt.lowercase()
    val wrote = events.any { it.contains("Wrote file") || it.contains("Edited file") || it.contains("Created folder") }
    val deleted = events.any { it.contains("Deleted path") }
    val out = when {
        deleted -> listOf("Show what is left in the folder", "Explain what changed")
        wrote -> listOf("Show the files you changed", "Run a quick check", "Explain the change")
        "explain" in text || "how it work" in text || "what is" in text ->
            listOf("Summarize in three lines", "List the weak points", "Explain the main file")
        "fix" in text || "bug" in text || "error" in text ->
            listOf("Explain the fix", "Check for related issues", "Add a test for it")
        "create" in text || "build" in text || "add" in text || "make" in text ->
            listOf("Show the new files", "Improve it a little", "Explain the change")
        else -> listOf("Explain what you changed", "What is the next step?", "Keep it shorter next time")
    }
    return out.distinct().take(3)
}

@Composable
private fun SuggestionRow(items: List<String>, colors: Palette, onPick: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 12.dp)
    ) {
        items(items) { label ->
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(colors.panel)
                    .clickable { onPick(label) }
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = colors.ink, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

private fun argSummary(name: String, args: JSONObject): String = runCatching {
    when (name) {
        "write_file" -> "\"${args.optString("path")}\", ${args.optString("content").length} chars"
        "move_path" -> "\"${args.optString("from")}\" → \"${args.optString("to")}\""
        "search_text" -> "\"${args.optString("query")}\""
        else -> "\"${args.optString("path")}\""
    }
}.getOrDefault("").take(120)
private fun toolLabel(name: String): String = when (name) {
    "list_files" -> "Listed files"
    "read_file" -> "Read file"
    "read_lines" -> "Read source lines"
    "check_source_text" -> "Checked saved source"
    "create_directory" -> "Created folder"
    "write_file" -> "Wrote file"
    "edit_file" -> "Edited file"
    "delete_path" -> "Deleted path"
    "move_path" -> "Moved path"
    "search_text" -> "Searched text"
    else -> "Workspace tool"
}

@Composable
private fun SettingsScreen(
    colors: Palette, dark: Boolean, provider: ProviderConfig, workspaceName: String, hasWorkspace: Boolean,
    workMode: WorkMode, autoApprove: Boolean, onAutoApprove: (Boolean) -> Unit, useAgentsMd: Boolean,
    chatCount: Int, onTheme: () -> Unit, onProvider: () -> Unit, onWorkspace: () -> Unit,
    onDefaultMode: (WorkMode) -> Unit, onUseAgentsMd: (Boolean) -> Unit,
    onInstallAgentsMd: () -> Unit,
    workspace: WorkspaceStore?, mcpServers: List<McpServer>, onMcpToggle: (McpServer, Boolean) -> Unit,
    onMcpAdd: (String?, String, String, Map<String, String>) -> Unit, onMcpRemove: (String) -> Unit,
    onMcpRefresh: suspend () -> String, sshConfig: SshConfig, onSshSave: (SshConfig) -> Unit,
    onClearChats: () -> Unit, modifier: Modifier = Modifier
) {
    var confirmClear by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SettingsSection("MCP servers", colors) {
                Text("Manage server connections and test available Agent tools.",
                    color = colors.muted, fontSize = 11.sp, lineHeight = 16.sp)
                var mcpName by remember { mutableStateOf("") }
                var mcpUrl by remember { mutableStateOf("") }
                var mcpToken by remember { mutableStateOf("") }
                var mcpStatus by remember { mutableStateOf("") }
                var mcpBusy by remember { mutableStateOf(false) }
                var editingId by remember { mutableStateOf<String?>(null) }
                val mcpScope = rememberCoroutineScope()
                Spacer(Modifier.height(10.dp))
                mcpServers.forEach { server ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (editingId == server.id) colors.raised else Color.Transparent)
                            .clickable {
                                editingId = server.id
                                mcpName = server.name
                                mcpUrl = server.url
                                mcpToken = ""
                                mcpStatus = "Editing ${server.name}: leave the token empty to keep the saved one."
                            }
                            .padding(vertical = 5.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val logo = when (android.net.Uri.parse(server.url).host?.lowercase()) {
                            "mcp.vercel.com" -> R.drawable.mcp_vercel
                            "mcp.netlify.com", "netlify-mcp.netlify.app", "mcp.netlify.app" -> R.drawable.mcp_netlify
                            "firebase.googleapis.com" -> R.drawable.mcp_firebase
                            "api.githubcopilot.com" -> R.drawable.mcp_github
                            "mcp.supabase.com" -> R.drawable.mcp_supabase
                            else -> null
                        }
                        Box(Modifier.size(28.dp).background(if (logo == null) colors.raised else Color.White, CircleShape),
                            contentAlignment = Alignment.Center) {
                            if (logo != null) Image(painterResource(logo), null, modifier = Modifier.size(18.dp))
                            else Icon(Icons.Default.Widgets, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(server.name, color = colors.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                if (server.preset) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("preset", color = colors.muted, fontSize = 9.sp)
                                }
                            }
                            Text(server.url, color = colors.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (McpTools.webProvider(server.url) == "vercel" && server.hasVercelDeployToken()) {
                                Text("Direct deploy: token saved", color = colors.accent, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = server.enabled,
                            onCheckedChange = { onMcpToggle(server, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.accentInk, checkedTrackColor = colors.accent),
                            modifier = Modifier.height(28.dp)
                        )
                        IconButton(onClick = { onMcpRemove(server.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Delete, "Remove server", tint = colors.muted, modifier = Modifier.size(15.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = mcpName, onValueChange = { mcpName = it.take(40) },
                    label = { Text("Server name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(colors)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = mcpUrl, onValueChange = { mcpUrl = it.take(300) },
                    label = { Text("MCP endpoint URL") }, placeholder = { Text("https://mcp.example.com/mcp") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(colors)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = mcpToken, onValueChange = { mcpToken = it.take(8192) },
                    label = { Text("Authorization (optional)") }, placeholder = { Text("Bearer <token>") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(colors)
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            if (mcpName.isBlank() || !isMcpEndpoint(mcpUrl.trim())) { mcpStatus = "Name and HTTPS URL required"; return@OutlinedButton }
                            val headers = if (mcpToken.isBlank()) emptyMap() else mapOf("Authorization" to mcpToken.trim())
                            onMcpAdd(editingId, mcpName.trim(), mcpUrl.trim(), headers)
                            mcpStatus = if (editingId == null) "Added ${mcpName.trim()}" else "Saved ${mcpName.trim()}"
                            editingId = null
                            mcpName = ""; mcpUrl = ""; mcpToken = ""
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink)
                    ) { Text(if (editingId == null) "Add server" else "Save changes", fontSize = 11.sp) }
                    if (editingId != null) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { editingId = null; mcpName = ""; mcpUrl = ""; mcpToken = ""; mcpStatus = "" },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.muted)
                        ) { Text("Cancel", fontSize = 11.sp) }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            mcpBusy = true; mcpStatus = ""
                            mcpScope.launch {
                                mcpStatus = onMcpRefresh()
                                mcpBusy = false
                            }
                        },
                        enabled = !mcpBusy,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink)
                    ) {
                        if (mcpBusy) CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp, color = colors.accent)
                        else Text("Test Agent tools", fontSize = 11.sp)
                    }
                }
                if (mcpStatus.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(mcpStatus, color = colors.muted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
        item {
            SettingsSection("Workspace", colors) {
                SettingsRow("Folder", workspaceName.ifBlank { "Not selected" }, colors, action = onWorkspace, actionLabel = if (hasWorkspace) "Change" else "Choose")
            }
        }
        item {
            SettingsSection("Tools & capabilities", colors) {
                listOf(
                    Triple(Icons.Default.FolderOpen, "Read workspace", "List folders, read text files, search across the project"),
                    Triple(Icons.Default.Code, "Write code", "Create files, write content, apply exact single-match edits"),
                    Triple(Icons.Default.Add, "Create folders", "Scaffold new project directories"),
                    Triple(Icons.Default.History, "Move & rename", "Move or rename files and folders inside the workspace"),
                    Triple(Icons.Default.Settings, "Delete", "Remove a file, or a folder with everything inside it"),
                    Triple(Icons.Default.Tune, "Search text", "Find a string and return path, line number and matching line")
                ).forEach { (icon, title, detail) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                        Icon(icon, null, tint = colors.accent, modifier = Modifier.padding(top = 1.dp).size(16.dp))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            Text(detail, color = colors.muted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(if (autoApprove) "Text writes and edits apply automatically. Moves and deletes require approval." else "Writes, moves and deletes always ask for a held approval. The workspace root cannot be deleted.", color = colors.muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
        item {
            SettingsSection("Appearance", colors) {
                SettingsRow("Theme", if (dark) "Dark" else "Light", colors, action = onTheme, actionLabel = "Switch")
            }
        }
        item {
            SettingsSection("Default mode", colors) {
                Text("Chat answers without touching files. Agent can read, write, move, search and delete inside your workspace.", color = colors.muted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("Chat", workMode == WorkMode.CHAT, colors, enabled = true) { onDefaultMode(WorkMode.CHAT) }
                    ModeChip("Agent", workMode == WorkMode.AGENT, colors, enabled = true) { onDefaultMode(WorkMode.AGENT) }
                }
            }
        }
        item {
            SettingsSection("Privacy and approvals", colors) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-approve text edits", Modifier.weight(1f), color = colors.ink)
                    Switch(checked = autoApprove, onCheckedChange = onAutoApprove)
                }
                if (BuildConfig.DEBUG && android.os.Build.VERSION.SDK_INT >= 30) {
                    val storageContext = LocalContext.current
                    var storageGranted by remember { mutableStateOf(android.os.Environment.isExternalStorageManager()) }
                    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        storageGranted = android.os.Environment.isExternalStorageManager()
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Direct storage access", Modifier.weight(1f), color = colors.ink)
                        Switch(
                            checked = storageGranted,
                            onCheckedChange = {
                                val settings = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    android.net.Uri.fromParts("package", storageContext.packageName, null)
                                )
                                if (settings.resolveActivity(storageContext.packageManager) != null) {
                                    storagePermission.launch(settings)
                                } else {
                                    storagePermission.launch(android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                    ))
                                }
                            }
                        )
                    }
                }
                val links = androidx.compose.ui.platform.LocalUriHandler.current
                if (BuildConfig.PRIVACY_POLICY_URL.isNotEmpty()) TextButton(onClick = { links.openUri(BuildConfig.PRIVACY_POLICY_URL) }) { Text("Privacy policy") }
                Text("Chats and attached images are sent to your selected model provider. Agent tasks can also send workspace excerpts. Chats are saved on this device. Auto-approval covers text writes and edits. Shell commands still ask for approval.", color = colors.ink)
            }
        }
        item {
            SettingsSection("SSH · Termux", colors) {
                var sshHost by remember { mutableStateOf(sshConfig.host) }
                var sshPort by remember { mutableStateOf(sshConfig.port.toString()) }
                var sshUser by remember { mutableStateOf(sshConfig.user) }
                var sshPass by remember { mutableStateOf("") }
                val sshContext = LocalContext.current
                var sshStatus by remember { mutableStateOf("") }
                var sshBusy by remember { mutableStateOf(false) }
                val sshScope = rememberCoroutineScope()
                fun sshDraft(): SshConfig {
                    val host = sshHost.trim()
                    val port = sshPort.toIntOrNull() ?: 8022
                    val sameHost = host == sshConfig.host && port == sshConfig.port
                    return SshConfig(host, port, sshUser.trim(),
                        sshPass.ifEmpty { if (sameHost) sshConfig.password else "" },
                        privateKey = if (sameHost) sshConfig.privateKey else "",
                        knownHost = if (sameHost) sshConfig.knownHost else "")
                }
                Text(
                    "SSH runs JADX or Apktool decoding into the selected workspace. App creation and edits stop at source files.",
                    color = colors.muted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(value = sshHost, onValueChange = { sshHost = it.take(80) }, label = { Text("Host") },
                        singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors(colors))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = sshPort, onValueChange = { sshPort = it.filter(Char::isDigit).take(5) }, label = { Text("Port") },
                        singleLine = true, modifier = Modifier.width(92.dp), colors = fieldColors(colors))
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = sshUser, onValueChange = { sshUser = it.take(40) }, label = { Text("User") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(colors))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = sshPass, onValueChange = { sshPass = it.take(200) }, label = { Text("Password (empty keeps saved)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(colors))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            val config = sshDraft()
                            onSshSave(config)
                            sshPass = ""
                            sshStatus = "Saved"
                        },
                        shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink)
                    ) { Text("Save", fontSize = 11.sp) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            sshBusy = true
                            sshScope.launch {
                                val config = sshDraft()
                                sshStatus = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val probe = SshClient(config, sshContext).exec("echo ashura-ssh-ok; uname -a", 20_000)
                                        if (probe.stdout.contains("ashura-ssh-ok")) "Connected: ${probe.stdout.lines().getOrNull(1).orEmpty()}"
                                        else "Failed: ${probe.stderr.take(200)}"
                                    }.getOrElse { "Failed: ${it.message?.take(160)}" }
                                }
                                sshBusy = false
                            }
                        },
                        enabled = !sshBusy, shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink)
                    ) {
                        if (sshBusy) CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp, color = colors.accent)
                        else Text("Test connection", fontSize = 11.sp)
                    }
                }
                if (sshStatus.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(sshStatus, color = colors.muted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
        item {
            SettingsSection("Skills", colors) {
                var skills by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
                var skillUrl by remember { mutableStateOf("") }
                var skillBusy by remember { mutableStateOf(false) }
                var skillMessage by remember { mutableStateOf("") }
                val skillScope = rememberCoroutineScope()
                LaunchedEffect(workspace) {
                    skills = withContext(Dispatchers.IO) { runCatching { workspace?.listSkills() }.getOrNull().orEmpty() }
                }
                Text(
                    "Installed in .ashura/skills. The agent sees this list in every run and loads a skill with skill_read.",
                    color = colors.muted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                if (skills.isEmpty()) {
                    Text("No skills installed yet.", color = colors.muted, fontSize = 11.sp)
                } else skills.forEach { (skillName, description) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(skillName, color = colors.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(description, color = colors.muted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {
                            skillScope.launch {
                                val removed = withContext(Dispatchers.IO) { workspace?.removeSkill(skillName) ?: false }
                                skills = withContext(Dispatchers.IO) { runCatching { workspace?.listSkills() }.getOrNull().orEmpty() }
                                skillMessage = if (removed) "Removed $skillName" else "Could not remove $skillName"
                            }
                        }) { Icon(Icons.Default.Delete, "Remove skill", tint = colors.muted, modifier = Modifier.size(16.dp)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = skillUrl,
                    onValueChange = { skillUrl = it.take(300) },
                    label = { Text("Install from GitHub or HTTPS URL") },
                    placeholder = { Text("https://github.com/user/repo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(colors)
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            val target = workspace
                            if (target == null) { skillMessage = "Choose a workspace folder first."; return@OutlinedButton }
                            skillBusy = true; skillMessage = ""
                            skillScope.launch {
                                skillMessage = withContext(Dispatchers.IO) {
                                    runCatching { target.installSkillFromUrl(skillUrl.trim()) }
                                        .map { "Installed ${JSONObject(it).optString("name")}" }
                                        .getOrElse { "Install failed: ${it.message?.take(120) ?: "unknown error"}" }
                                }
                                skills = withContext(Dispatchers.IO) { runCatching { target.listSkills() }.getOrNull().orEmpty() }
                                skillBusy = false
                            }
                        },
                        enabled = !skillBusy && skillUrl.startsWith("https://"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink)
                    ) {
                        if (skillBusy) CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp, color = colors.accent)
                        else Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (skillBusy) "Installing…" else "Install skill", fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    if (skillMessage.isNotBlank()) Text(skillMessage, color = colors.muted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            SettingsSection("Agent instructions", colors) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use AGENTS.md from this folder", color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (useAgentsMd) "Read before every request and followed as system instructions"
                            else "Default behaviour; workspace AGENTS.md is ignored",
                            color = colors.muted, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = useAgentsMd,
                        onCheckedChange = onUseAgentsMd,
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.accentInk, checkedTrackColor = colors.accent)
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onInstallAgentsMd,
                    enabled = hasWorkspace,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink)
                ) { Text("Write AGENTS.md template into the folder", fontSize = 11.sp) }
                Spacer(Modifier.height(6.dp))
                Text("The template is the Ashura AGENTS.md shipped with this app. Edit it in the workspace to change the agent's rules.", color = colors.muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
        item {
            SettingsSection("Model", colors) {
                SettingsRow("Provider", "${provider.name} · ${provider.model}", colors, action = onProvider, actionLabel = "Change")
            }
        }
        item {
            SettingsSection("Privacy", colors) {
                Text("API keys are encrypted with Android Keystore. Conversations and settings stay on this device.", color = colors.muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        item {
            SettingsSection("Data", colors) {
                SettingsRow("Conversations", "$chatCount saved", colors, action = { confirmClear = true }, actionLabel = "Clear")
            }
        }
        item {
            SettingsSection("About", colors) {
                SettingsRow("Ashura AI", "1.0.0 · agent workspace", colors, action = null, actionLabel = "")
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = colors.panel,
            title = { Text("Clear all conversations", color = colors.ink) },
            text = { Text("Every saved chat on this device will be removed. Workspace files are not touched.", color = colors.muted, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp)) },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClearChats() }) { Text("Clear", color = Attention) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = colors.muted) } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, colors: Palette, content: @Composable ColumnScope.() -> Unit) {
    val icon = when (title) {
        "MCP servers", "Skills" -> Icons.Default.Widgets
        "Workspace" -> Icons.Default.FolderOpen
        "Tools & capabilities", "Agent instructions" -> Icons.Default.Code
        "Appearance" -> Icons.Default.LightMode
        "Data" -> Icons.Default.History
        "Model", "Default mode" -> Icons.Default.Tune
        else -> Icons.Default.Settings
    }
    Column(Modifier.fillMaxWidth().workspaceSurface(colors, radius = 16.dp).padding(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            SectionLabel(title, colors)
        }
        Spacer(Modifier.height(7.dp))
        content()
    }
}

@Composable
private fun SettingsRow(title: String, value: String, colors: Palette, action: (() -> Unit)?, actionLabel: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(value, color = colors.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (action != null) TextButton(onClick = action) { Text(actionLabel, color = colors.accent, fontSize = 12.sp) }
    }
}

private data class Palette(
    val bg: Color, val panel: Color, val raised: Color, val ink: Color, val muted: Color,
    val accent: Color, val accentInk: Color, val line: Color, val code: Color,
    val codeKeyword: Color, val codeString: Color, val codeNumber: Color, val codeComment: Color,
    val error: Color
)

private fun Modifier.workspaceSurface(colors: Palette, radius: Dp = 12.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(colors.panel)
    .border(1.dp, colors.line, RoundedCornerShape(radius))

package ai.ashura.app.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

internal fun isMcpEndpoint(value: String): Boolean {
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) && uri.host != null &&
        uri.userInfo == null && uri.fragment == null && uri.port in -1..65535
}

/**
 * Minimal Model Context Protocol client over Streamable HTTP.
 * Implements initialize, tools/list and tools/call with JSON-RPC 2.0, accepting both JSON and SSE
 * responses, and keeping the Mcp-Session-Id handed out by the server.
 */
class McpClient(private val url: String, private val headers: Map<String, String>) {

    data class ToolInfo(val name: String, val description: String, val schema: JSONObject)

    private val ids = AtomicInteger(1)
    private var sessionId: String? = null
    private var protocolVersion: String? = null
    private fun nextId(): Int = ids.getAndIncrement()

    private fun open(): java.net.HttpURLConnection {
        require(isMcpEndpoint(url)) { "MCP requires an HTTPS endpoint URL" }
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        // Deploy tools (Vercel/Netlify) legitimately run for minutes; a short read timeout returns
        // a half-finished operation as if the server failed.
        connection.readTimeout = 180_000
        connection.doOutput = true
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json, text/event-stream")
        connection.setRequestProperty("User-Agent", "AshuraAI/1.0")
        headers.forEach { (key, value) -> if (value.isNotBlank()) connection.setRequestProperty(key, value) }
        sessionId?.let { connection.setRequestProperty("Mcp-Session-Id", it) }
        protocolVersion?.let { connection.setRequestProperty("MCP-Protocol-Version", it) }
        return connection
    }

    private fun rpc(method: String, params: JSONObject?, notify: Boolean = false): JSONObject? {
        val payload = JSONObject().put("jsonrpc", "2.0").put("method", method)
        val id = if (notify) null else nextId()
        if (id != null) payload.put("id", id)
        if (params != null) payload.put("params", params)
        val connection = open()
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val type = connection.contentType.orEmpty().lowercase()
            if (status !in 200..299) {
                val text = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use {
                    val chars = CharArray(300)
                    val count = it.read(chars)
                    if (count > 0) String(chars, 0, count) else ""
                }.orEmpty()
                if (status == 401) {
                    val hint = if (McpTools.webProvider(url) == "vercel")
                        "Vercel MCP requires OAuth authorization through a supported client. A deployment API token is separate."
                    else "MCP authorization missing, expired or rejected. Check the saved authorization."
                    throw McpException(hint)
                }
                throw McpException("HTTP $status from MCP server: ${text.ifBlank { connection.responseMessage.orEmpty() }}")
            }
            if (method == "initialize") sessionId = connection.getHeaderField("Mcp-Session-Id")
            if (id == null) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                if (type.contains("text/event-stream")) parseSse(it, id)
                else JSONObject(it.readBounded()).also { response ->
                    if (!matches(response, id)) throw McpException("Unexpected JSON-RPC response")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun matches(response: JSONObject, id: Int): Boolean =
        response.opt("id") == id && (response.has("result") || response.has("error"))

    private fun BufferedReader.readBounded(): String {
        val output = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toString()
            if (output.length + count > MAX_RESPONSE_CHARS) throw McpException("MCP response too large")
            output.append(buffer, 0, count)
        }
    }

    private fun parseSse(reader: BufferedReader, id: Int): JSONObject {
        val event = StringBuilder()
        val line = StringBuilder()
        var received = 0
        while (true) {
            val char = reader.read()
            if (char == -1) break
            if (++received > MAX_RESPONSE_CHARS) throw McpException("MCP response too large")
            if (char != '\n'.code) {
                line.append(char.toChar())
                continue
            }
            val value = line.toString().trimEnd('\r')
            line.setLength(0)
            if (value.isEmpty()) {
                if (event.isEmpty()) continue
                val response = runCatching { JSONObject(event.toString()) }.getOrNull()
                if (response != null && matches(response, id)) return response
                event.setLength(0)
            } else if (value.startsWith("data:")) {
                if (event.isNotEmpty()) event.append('\n')
                event.append(value.substring(5).removePrefix(" "))
            }
        }
        throw McpException("MCP server sent no matching JSON-RPC response")
    }

    private fun ensureInitialized() {
        if (initialized) return
        val response = rpc("initialize", JSONObject()
            .put("protocolVersion", "2025-06-18")
            .put("capabilities", JSONObject())
            .put("clientInfo", JSONObject().put("name", "Ashura AI").put("version", "1.0")))
        val result = checkedResult(response, "initialize")
        val negotiated = result.optString("protocolVersion")
        if (negotiated !in setOf("2025-06-18", "2025-03-26")) {
            throw McpException("Unsupported MCP protocol version: $negotiated")
        }
        protocolVersion = negotiated
        rpc("notifications/initialized", JSONObject(), notify = true)
        initialized = true
    }

    @Volatile private var initialized = false

    fun listTools(): List<ToolInfo> {
        ensureInitialized()
        val available = mutableListOf<ToolInfo>()
        val seen = mutableSetOf<String>()
        var cursor = ""
        do {
            if (!seen.add(cursor) || seen.size > 100) throw McpException("Invalid MCP tool pagination")
            val params = JSONObject()
            if (cursor.isNotEmpty()) params.put("cursor", cursor)
            val result = checkedResult(rpc("tools/list", params), "tools/list")
            val tools = result.optJSONArray("tools") ?: throw McpException("Missing MCP tools list")
            for (index in 0 until tools.length()) {
                val tool = tools.optJSONObject(index) ?: continue
                val name = tool.optString("name").ifBlank { continue }
                available.add(ToolInfo(name, tool.optString("description"), tool.optJSONObject("inputSchema") ?: JSONObject()))
            }
            cursor = result.optString("nextCursor", "")
        } while (cursor.isNotBlank())
        return available
    }

    private fun checkedResult(response: JSONObject?, method: String): JSONObject {
        val error = response?.optJSONObject("error")
        if (error != null) throw McpException(error.optString("message", "MCP error"))
        return response?.optJSONObject("result") ?: throw McpException("Missing $method result")
    }

    fun callTool(name: String, arguments: JSONObject): String {
        ensureInitialized()
        val result = checkedResult(
            rpc("tools/call", JSONObject().put("name", name).put("arguments", arguments)), "tools/call"
        )
        val content = result.optJSONArray("content")
        val text = StringBuilder()
        for (index in 0 until (content?.length() ?: 0)) {
            val block = content?.optJSONObject(index) ?: continue
            when (block.optString("type")) {
                "text" -> text.append(block.optString("text"))
                else -> text.append(block.toString())
            }
        }
        if (result.optBoolean("isError")) {
            return JSONObject().put("error", text.toString().ifBlank { "MCP tool failed" }.take(120_000)).toString()
        }
        return JSONObject().put("mcpCallSucceeded", true)
            .put("content", text.toString().take(120_000))
            .apply { result.optJSONObject("structuredContent")?.let { put("structuredContent", it.toString().take(120_000)) } }
            .toString()
    }

    class McpException(message: String) : Exception(message)

    private companion object {
        const val MAX_RESPONSE_CHARS = 8 * 1024 * 1024
    }
}

/** One configured remote MCP server. Headers carry auth and are stored encrypted by the app store. */
data class McpServer(
    val id: String,
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val preset: Boolean = false
) {
    companion object {
        fun presets(): List<McpServer> = listOf(
            McpServer("supabase", "Supabase", "https://mcp.supabase.com/mcp",
                mapOf("Authorization" to ""), enabled = false, preset = true),
            McpServer("vercel", "Vercel", "https://mcp.vercel.com",
                mapOf("Authorization" to ""), enabled = false, preset = true),
            McpServer("netlify", "Netlify", "https://mcp.netlify.com/mcp",
                mapOf("Authorization" to ""), enabled = false, preset = true),
            McpServer("firebase", "Firebase", "https://firebase.googleapis.com/mcp",
                mapOf("Authorization" to ""), enabled = false, preset = true),
            McpServer("github", "GitHub", "https://api.githubcopilot.com/mcp/",
                mapOf("Authorization" to "Bearer "), enabled = false, preset = true)
        )
    }
}

/** Merges workspace tools with every enabled MCP server into one tool surface for the engine. */
class McpTools(private val servers: List<McpServer>, private val webDeployOnly: Boolean = false) {

    class Remote(val server: McpServer, val tool: McpClient.ToolInfo, val client: McpClient)

    private var cached: List<Remote>? = null
    private var lastError: String? = null

    fun refresh(): String {
        val remotes = mutableListOf<Remote>()
        val errors = mutableListOf<String>()
        servers.filter { it.enabled && (!webDeployOnly || webProvider(it.url) != null) }.forEach { server ->
            runCatching {
                val client = McpClient(server.url, server.headers)
                client.listTools().map { Remote(server, it, client) }
            }
                .onSuccess { tools ->
                    remotes += tools.filter { !webDeployOnly || webToolAllowed(server.url, it.tool.name) }
                }
                .onFailure { errors += "${server.name}: ${it.message}" }
        }
        cached = remotes
        lastError = errors.joinToString("; ").ifBlank { null }
        return "Eligible servers=${servers.count { it.enabled && (!webDeployOnly || webProvider(it.url) != null) }}, tools=${remotes.size}" +
            (lastError?.let { "; errors=$it" } ?: "")
    }

    fun connectionStatus(): String {
        val discovered = tools()
        val lines = servers.map { server ->
            val count = discovered.count { it.server.id == server.id }
            server.name + ": " + when {
                !server.enabled -> "Disabled"
                webDeployOnly && webProvider(server.url) == null -> "Outside Agent tool scope"
                count > 0 -> "$count Agent tools discovered"
                else -> "No Agent tools discovered"
            }
        }
        return (lines + listOfNotNull(lastError)).joinToString("\n").ifBlank { "No MCP servers configured." }
    }

    fun tools(): List<Remote> = cached ?: run { refresh(); cached.orEmpty() }

    /** Reuses discovery results within the current run. */
    @Synchronized
    fun ensureLoaded(): String = if (cached != null) "cached" else refresh()

    fun toolNames(): List<String> = tools().map { toolName(it.server.id, it.tool.name) }

    fun jsonDefinitions(): JSONArray = JSONArray().apply {
        tools().forEach { remote ->
            val name = toolName(remote.server.id, remote.tool.name)
            put(JSONObject().put("type", "function").put("function", JSONObject()
                .put("name", name)
                .put("description", "[${remote.server.name}] " + remote.tool.description.take(400))
                .put("parameters", remote.tool.schema)))
        }
    }

    fun execute(name: String, arguments: JSONObject): String? {
        val remote = tools().firstOrNull { toolName(it.server.id, it.tool.name) == name } ?: return null
        if (webDeployOnly && remote.tool.name == "netlify-deploy-services-updater" &&
            arguments.optJSONObject("selectSchema")?.optString("operation") != "deploy-site") {
            return JSONObject().put("error", "Only the Netlify deploy-site operation is available").toString()
        }
        return runCatching { remote.client.callTool(remote.tool.name, arguments) }
            .getOrElse { JSONObject().put("error", "MCP ${remote.server.name}: ${it.message?.take(200)}").toString() }
    }

    fun describe(): String {
        val active = tools().groupBy { it.server.name }
        if (active.isEmpty()) return ""
        return active.entries.joinToString("\n") { (server, tools) ->
            "MCP server $server: " + tools.take(12).joinToString(", ") { it.tool.name } +
                if (tools.size > 12) " (+${tools.size - 12} more)" else ""
        }
    }

    companion object {
        internal fun webProvider(url: String): String? {
            if (!isMcpEndpoint(url)) return null
            val uri = URI(url)
            if (uri.port !in setOf(-1, 443) || uri.rawQuery != null) return null
            val path = uri.rawPath.trimEnd('/')
            return when (uri.host.lowercase(java.util.Locale.ROOT)) {
                "mcp.vercel.com" -> if (path.isEmpty()) "vercel" else null
                "mcp.netlify.com", "netlify-mcp.netlify.app" -> if (path == "/mcp") "netlify" else null
                else -> null
            }
        }

        internal fun webToolAllowed(url: String, tool: String): Boolean = when (webProvider(url)) {
            "vercel" -> tool in setOf("deploy_to_vercel", "list_teams", "list_projects", "list_deployments", "get_deployment")
            "netlify" -> tool in setOf("netlify-deploy-site", "netlify-deploy-services-updater",
                "netlify-deploy-services-reader", "netlify-open-deployments")
            else -> false
        }
        private const val MAX_NAME = 60
        fun toolName(serverId: String, tool: String): String {
            val raw = "mcp__${serverId}__$tool".replace(Regex("[^A-Za-z0-9_]"), "_")
            if (raw.length <= MAX_NAME) return raw
            // Keep the tail unique so long tool names never collide after truncation.
            val digest = (raw.hashCode().toUInt().toString(16)).takeLast(6)
            return raw.take(MAX_NAME - 7) + "_" + digest
        }
    }
}

package ai.ashura.app.storage

import android.content.Context
import android.util.AtomicFile
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ai.ashura.app.engine.ChatMessage
import ai.ashura.app.engine.McpServer
import ai.ashura.app.engine.ProviderConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

 data class StoredChat(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val updatedAt: Long
)

data class StoredPreferences(
    val theme: String = "system",
    val workspaceUri: String = "",
    val selectedProviderId: String = ""
)

/** App-private JSON persistence. Provider API keys are individually AES-GCM encrypted. */
class AppStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "ashura-store").apply { mkdirs() }
    private val imageDirectory = File(context.applicationContext.filesDir, "chat-images")
    private val lock = Any()

    fun saveProviders(providers: List<ProviderConfig>) = synchronized(lock) {        val array = JSONArray()
        providers.forEach { provider ->
            array.put(JSONObject().put("id", provider.id).put("name", provider.name)
                .put("baseUrl", provider.baseUrl).put("apiKey", encrypt(provider.apiKey))
                .put("model", provider.model).put("protocol", provider.protocol).put("reasoning", provider.reasoning))
        }
        write("providers.json", array.toString())
    }

    fun loadProviders(): List<ProviderConfig> = synchronized(lock) {
        val array = read("providers.json")?.let(::JSONArray) ?: return@synchronized emptyList()
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                try {
                    add(ProviderConfig(item.getString("id"), item.getString("name"), item.getString("baseUrl"),
                        decrypt(item.getJSONObject("apiKey")), item.getString("model"),
                        item.optString("protocol", "openai"), item.optBoolean("reasoning", false)))
                } catch (_: Exception) { /* Ignore one unreadable entry without exposing its encrypted data. */ }
            }
        }
    }

    fun saveChats(chats: List<StoredChat>, removedImages: List<String> = emptyList()) = synchronized(lock) {
        val array = JSONArray()
        chats.forEach { chat ->
            val messages = JSONArray()
            chat.messages.forEach { message ->
                messages.put(JSONObject().put("role", message.role).put("content", message.content)
                    .put("id", message.id).put("toolCalls", message.toolCalls).put("imagePath", message.imagePath)
                    .put("apkMethod", message.apkMethod))
            }
            array.put(JSONObject().put("id", chat.id).put("title", chat.title)
                .put("updatedAt", chat.updatedAt).put("messages", messages))
        }
        val encoded = array.toString()
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "Chat data exceeds storage limit" }
        write("chats.json", encoded)
        if (removedImages.isNotEmpty()) {
            val retained = chats.flatMap { chat -> chat.messages.mapNotNull(ChatMessage::imagePath) }.toSet()
            val imageRoot = imageDirectory.canonicalFile
            for (path in removedImages.distinct()) {
                if (path in retained) continue
                runCatching {
                    val file = File(path).canonicalFile
                    if (file.parentFile == imageRoot && file.extension == "jpg") file.delete()
                }
            }
        }
    }

    fun loadChats(): List<StoredChat> = synchronized(lock) {
        val raw = read("chats.json") ?: return@synchronized emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                try {
                    val messages = item.getJSONArray("messages")
                    require(messages.length() <= MAX_MESSAGES_PER_CHAT)
                    val parsed = buildList {
                        for (messageIndex in 0 until messages.length()) {
                            val message = messages.getJSONObject(messageIndex)
                            add(ChatMessage(message.getString("role"), message.getString("content"),
                                message.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                                message.optString("toolCalls").takeIf { !message.isNull("toolCalls") && it.isNotEmpty() },
                                message.optString("imagePath").takeIf { !message.isNull("imagePath") && it.isNotEmpty() },
                                message.optString("apkMethod").takeIf { !message.isNull("apkMethod") }))
                        }
                    }
                    add(StoredChat(item.getString("id"), item.optString("title"), parsed, item.optLong("updatedAt")))
                } catch (_: Exception) { /* Skip malformed sessions. */ }
            }
        }
    }

    fun savePreferences(preferences: StoredPreferences) = synchronized(lock) {
        require(preferences.theme in setOf("system", "light", "dark")) { "Unsupported theme" }
        write("preferences.json", JSONObject().put("theme", preferences.theme)
            .put("workspaceUri", preferences.workspaceUri).put("selectedProviderId", preferences.selectedProviderId).toString())
    }

    fun loadPreferences(): StoredPreferences = synchronized(lock) {
        val item = read("preferences.json")?.let(::JSONObject) ?: return@synchronized StoredPreferences()
        StoredPreferences(item.optString("theme", "system").takeIf { it in setOf("system", "light", "dark") } ?: "system",
            item.optString("workspaceUri"), item.optString("selectedProviderId"))
    }

    fun saveMcpServers(servers: List<McpServer>) = synchronized(lock) {
        val array = JSONArray()
        servers.forEach { server ->
            val headers = JSONObject()
            server.headers.forEach { (key, value) -> headers.put(key, encrypt(value)) }
            array.put(JSONObject().put("id", server.id).put("name", server.name).put("url", server.url)
                .put("enabled", server.enabled).put("preset", server.preset).put("headers", headers))
        }
        write("mcp.json", array.toString())
    }

    fun loadMcpServers(): List<McpServer> = synchronized(lock) {
        val raw = read("mcp.json") ?: return@synchronized McpServer.presets()
        runCatching { JSONArray(raw) }.getOrNull()?.let { array ->
            val saved = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val headers = mutableMapOf<String, String>()
                    item.optJSONObject("headers")?.let { headersObject ->
                        headersObject.keys().forEach { key ->
                            headersObject.optJSONObject(key)?.let { headers[key] = decrypt(it) }
                        }
                    }
                    add(McpServer(
                        id = item.optString("id").ifBlank { "server-$index" },
                        name = item.optString("name").ifBlank { "MCP server" },
                        url = item.optString("url"),
                        headers = headers,
                        enabled = item.optBoolean("enabled", true),
                        preset = item.optBoolean("preset", false)
                    ))
                }
            }
            if (saved.isNotEmpty()) return@synchronized saved
        }
        McpServer.presets()
    }

    fun saveSsh(config: SshConfig) = synchronized(lock) {
        write("ssh.json", JSONObject().put("host", config.host).put("port", config.port)
            .put("user", config.user).put("password", encrypt(config.password))
            .put("key", encrypt(config.privateKey)).put("knownHost", config.knownHost).toString())
    }

    fun loadSsh(): SshConfig = synchronized(lock) {
        val item = read("ssh.json")?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return@synchronized SshConfig()
        SshConfig(
            host = item.optString("host", "127.0.0.1"),
            port = item.optInt("port", 8022),
            user = item.optString("user", "u0_a"),
            password = item.optJSONObject("password")?.let(::decrypt).orEmpty(),
            privateKey = item.optJSONObject("key")?.let(::decrypt).orEmpty(),
            knownHost = item.optString("knownHost")
        )
    }

    private fun write(name: String, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_JSON_BYTES) { "Stored data exceeds limit" }
        val target = AtomicFile(File(directory, name))
        val output = target.startWrite()
        try {
            output.write(bytes)
            target.finishWrite(output)
        } catch (error: Throwable) {
            target.failWrite(output)
            throw error
        }
    }

    private fun read(name: String): String? {
        val target = AtomicFile(File(directory, name))
        return try {
            target.openRead().use {
                require(it.channel.size() <= MAX_JSON_BYTES) { "Stored data exceeds limit" }
                it.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (error: java.io.FileNotFoundException) {
            if (target.baseFile.exists()) throw error
            null
        }
    }

    private fun encrypt(value: String): JSONObject {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return JSONObject().put("v", 1).put("iv", android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP))
            .put("data", android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP))
    }

    private fun decrypt(value: JSONObject): String {
        require(value.getInt("v") == 1) { "Unsupported encrypted key version" }
        val iv = android.util.Base64.decode(value.getString("iv"), android.util.Base64.NO_WRAP)
        val ciphertext = android.util.Base64.decode(value.getString("data"), android.util.Base64.NO_WRAP)
        require(iv.size == GCM_IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "ashura.provider.api_keys.v1"
        private const val MAX_JSON_BYTES = 8 * 1024 * 1024
        private const val MAX_MESSAGES_PER_CHAT = 10000
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}

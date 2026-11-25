package ai.ashura.app.engine

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** baseUrl is an API root, for example https://api.openai.com/v1. */
data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val protocol: String = "openai",
    val reasoning: Boolean = false
) {
    override fun toString(): String = "ProviderConfig(id=$id, name=$name, protocol=$protocol, apiKey=[redacted])"
}

/** For a tool result, use role=tool and id=the provider tool-call ID. */
data class ChatMessage(
    val role: String,
    val content: String,
    val id: String = UUID.randomUUID().toString(),
    val toolCalls: String? = null,
    val imagePath: String? = null,
    val apkMethod: String? = null
)

interface WorkspaceTools {
    suspend fun execute(name: String, arguments: JSONObject): String
    /** OpenAI function tools: [{"type":"function","function":{"name":...,"parameters":...}}]. */
    fun definitions(): JSONArray
    fun description(): String
}

class ProviderException(message: String) : Exception(message)

/** Presets supply API roots; configure a model and API key before running. */
object ProviderPresets {
    val openAi = ProviderConfig("openai", "OpenAI", "https://api.openai.com/v1", "", "gpt-4o-mini")
    val anthropic = ProviderConfig("anthropic", "Anthropic", "https://api.anthropic.com/v1", "", "claude-sonnet-4-5", "anthropic")
    val gemini = ProviderConfig("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "", "gemini-2.5-flash")
    val deepSeek = ProviderConfig("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "", "deepseek-chat")
    val groq = ProviderConfig("groq", "Groq", "https://api.groq.com/openai/v1", "", "llama-3.3-70b-versatile")
    val openRouter = ProviderConfig("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "", "openai/gpt-4o-mini")
    val mistral = ProviderConfig("mistral", "Mistral", "https://api.mistral.ai/v1", "", "mistral-small-latest")
    val xAi = ProviderConfig("xai", "xAI", "https://api.x.ai/v1", "", "grok-3-mini")
}

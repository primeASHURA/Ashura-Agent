package ai.ashura.app.storage

import java.util.Locale

/** Platform-independent policy. Paths are canonical, relative SAF names, never URIs. */
object PathPolicy {
    const val MAX_PATH_LENGTH = 1024
    const val MAX_DEPTH = 32

    fun segments(path: String, allowRoot: Boolean = false): List<String> {
        require(path.length <= MAX_PATH_LENGTH) { "Path is too long" }
        if (path.isEmpty()) {
            require(allowRoot) { "A non-empty path is required" }
            return emptyList()
        }
        require(!path.startsWith('/') && !path.endsWith('/')) { "Use a relative path without trailing slash" }
        require(path.none { it == '\\' || it == ':' || it == '%' || it == '?' || it == '#' || it.code < 32 || it.code == 127 }) {
            "Path contains a forbidden character"
        }
        val parts = path.split('/')
        require(parts.size <= MAX_DEPTH) { "Path is too deep" }
        require(parts.all { it.isNotEmpty() && it != "." && it != ".." && it.length <= 255 && it == it.trim() }) {
            "Path contains an invalid segment"
        }
        return parts
    }

    fun requireReadable(path: String, allowSecrets: Boolean = false) {
        val parts = segments(path)
        if (!allowSecrets) require(parts.none(::isSecretName)) { "Reading secret or key files is blocked" }
    }

    fun isSecretName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower == ".env" || lower.startsWith(".env.") || lower.endsWith(".env") ||
            lower == ".ssh" || lower == ".aws" || lower == ".gnupg" ||
            lower == "credentials" || lower == "credentials.json" || lower == "secrets.json" ||
            lower == "secrets.yaml" || lower == "secrets.yml" || lower == "local.properties" ||
            lower == "key.properties" || lower.startsWith("id_rsa") || lower.startsWith("id_ed25519") ||
            lower.startsWith("id_ecdsa") || lower.endsWith(".key") || lower.endsWith(".pem") ||
            lower.endsWith(".p12") || lower.endsWith(".pfx") || lower.endsWith(".jks") || lower.endsWith(".keystore")
    }
}

package ai.ashura.app.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathPolicyTest {
    @Test fun acceptsWorkspaceRelativePaths() {
        assertEquals(listOf("src", "Main.kt"), PathPolicy.segments("src/Main.kt"))
        assertEquals(emptyList<String>(), PathPolicy.segments("", allowRoot = true))
    }

    @Test fun rejectsTraversalAndAbsolutePaths() {
        listOf("../escape", "src/../../escape", "/root", "C:/secret", "src\\file", "src//file", "./file", "%2e%2e/file")
            .forEach { path -> assertFalse("accepted $path", runCatching { PathPolicy.segments(path).isNotEmpty() }.getOrDefault(false)) }
    }

    @Test fun blocksCommonCredentialsAndPrivateKeys() {
        listOf(".env", ".env.local", "config/api.key", "id_rsa", "cert.pem", "signing.jks", ".aws/credentials")
            .forEach { path -> assertTrue("readable $path", runCatching { PathPolicy.requireReadable(path); true }.getOrDefault(false).not()) }
        assertEquals(listOf("src", "main.kt"), PathPolicy.segments("src/main.kt"))
    }
}

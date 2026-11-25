package ai.ashura.app.engine

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
    private fun engine() = AgentEngine(
        workspace = object : WorkspaceTools {
            override suspend fun execute(name: String, arguments: org.json.JSONObject): String = ""
            override fun definitions() = org.json.JSONArray()
            override fun description() = ""
        },
        onEvent = {}
    )

    @Test fun httpsIsAlwaysAllowed() {
        val e = engine()
        assertTrue(e.endpointAllowed("https://openrouter.ai/api/v1".toHttpUrl()))
        assertTrue(e.endpointAllowed("https://api.openai.com/v1".toHttpUrl()))
    }

    @Test fun loopbackHttpIsAllowed() {
        val e = engine()
        assertTrue(e.endpointAllowed("http://127.0.0.1:1234/v1".toHttpUrl()))
        assertTrue(e.endpointAllowed("http://localhost:8080/v1".toHttpUrl()))
    }

    @Test fun privateLanHttpIsAllowed() {
        val e = engine()
        assertTrue(e.endpointAllowed("http://192.168.1.50:1234/v1".toHttpUrl()))
        assertTrue(e.endpointAllowed("http://10.0.0.5:8000/v1".toHttpUrl()))
        assertTrue(e.endpointAllowed("http://172.20.3.4:1234/v1".toHttpUrl()))
        assertTrue(e.endpointAllowed("http://mybox.local:1234/v1".toHttpUrl()))
    }

    @Test fun publicHttpIsRejected() {
        val e = engine()
        assertFalse(e.endpointAllowed("http://example.com/v1".toHttpUrl()))
        assertFalse(e.endpointAllowed("http://8.8.8.8/v1".toHttpUrl()))
        assertFalse(e.endpointAllowed("http://172.32.0.1/v1".toHttpUrl()))
        assertFalse(e.endpointAllowed("http://192.169.0.1/v1".toHttpUrl()))
    }
}

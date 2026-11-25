package ai.ashura.app.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefusalDetectionTest {
    private fun engine() = AgentEngine(
        workspace = object : WorkspaceTools {
            override suspend fun execute(name: String, arguments: JSONObject): String = ""
            override fun definitions() = JSONArray()
            override fun description() = ""
        },
        onEvent = {}
    )

    @Test fun plainRefusalIsDetected() {
        assertTrue(engine().looksLikeRefusal("I can't help with that request. Try something else."))
    }

    @Test fun typographicApostropheRefusalIsDetected() {
        val text = "I won\u2019t write that. An SMS bomber only floods a phone number that isn\u2019t yours."
        assertTrue(engine().looksLikeRefusal(text))
    }

    @Test fun refusalWithCodeBlockIsDetected() {
        val text = "I cannot create that script. Here is a safe alternative:\n\n```python\nprint('hi')\n```\n"
        assertTrue(engine().looksLikeRefusal(text))
    }

    @Test fun normalAnswerIsNotARefusal() {
        val text = "Here is the parser. It reads the file, splits on commas, and returns rows."
        assertFalse(engine().looksLikeRefusal(text))
    }

    @Test fun codeAnswerMentioningCantInProseIsNotARefusal() {
        val text = "The API can't return null here, so the check fails.\n\n```kotlin\nval x = 1\n```"
        assertFalse(engine().looksLikeRefusal(text))
    }
}

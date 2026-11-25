package ai.ashura.app

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

@Composable
internal fun ReportResponse(content: String) {
    if (BuildConfig.AI_REPORT_URL.isEmpty()) return
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember(content) { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    TextButton(onClick = { dialog = true }, enabled = !sent) { Text(if (sent) "Report sent" else "Report response") }
    if (dialog) AlertDialog(onDismissRequest = { if (!sending) dialog = false },
        title = { Text("Report this response?") },
        text = { Text(if (failure) "Delivery failed. Try again." else "Send up to 8000 characters of this answer to the developer for review. Your other messages are excluded.") },
        confirmButton = { TextButton(enabled = !sending, onClick = {
            sending = true
            scope.launch {
                val delivered = withContext(Dispatchers.IO) {
                    runCatching {
                        val connection = URL(BuildConfig.AI_REPORT_URL).openConnection() as HttpURLConnection
                        try {
                            connection.instanceFollowRedirects = false
                            connection.requestMethod = "POST"
                            connection.connectTimeout = 10000
                            connection.readTimeout = 10000
                            connection.doOutput = true
                            connection.setRequestProperty("Content-Type", "application/json")
                            connection.outputStream.use { it.write(JSONObject().put("response", content.take(8000)).toString().toByteArray()) }
                            connection.responseCode in 200..299
                        } finally { connection.disconnect() }
                    }.getOrDefault(false)
                }
                sending = false
                sent = delivered
                failure = !delivered
                dialog = !delivered
            }
        }) { Text("Send report") } },
        dismissButton = { TextButton(enabled = !sending, onClick = { dialog = false }) { Text("Cancel") } })
}

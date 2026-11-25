package ai.ashura.app.storage

import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream

/** SSH connection to Termux (or any host) used to run decompilers on the device. */
data class SshConfig(
    val host: String = "127.0.0.1",
    val port: Int = 8022,
    val user: String = "u0_a",
    val password: String = "",
    val privateKey: String = "",
    val knownHost: String = ""
) {
    val ready: Boolean get() = host.isNotBlank() && port in 1..65535 && user.isNotBlank()
}

class SshClient(private val config: SshConfig, private val context: android.content.Context) {

    data class Result(val stdout: String, val stderr: String, val exit: Int, val millis: Long)

    private fun connect(): Session {
        val jsch = JSch()
        if (config.knownHost.isNotBlank()) {
            config.knownHost.byteInputStream().use { jsch.setKnownHosts(it) }
        } else {
            val pins = context.getSharedPreferences("ssh_host_keys", android.content.Context.MODE_PRIVATE)
            val repository = jsch.hostKeyRepository
            jsch.setHostKeyRepository(object : HostKeyRepository by repository {
                override fun check(host: String, key: ByteArray): Int = synchronized(pins) {
                    val encoded = android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
                    when (pins.getString(host, null)) {
                        encoded -> HostKeyRepository.OK
                        null -> if (pins.edit().putString(host, encoded).commit()) HostKeyRepository.OK
                                else HostKeyRepository.NOT_INCLUDED
                        else -> HostKeyRepository.CHANGED
                    }
                }
            })
        }
        if (config.privateKey.isNotBlank()) {
            val key = config.privateKey.trim()
            jsch.addIdentity("ashura-termux", key.toByteArray(Charsets.UTF_8), null, null)
        }
        val session = jsch.getSession(config.user, config.host, config.port)
        if (config.password.isNotBlank()) session.setPassword(config.password)
        val props = java.util.Properties()
        props["StrictHostKeyChecking"] = "yes"
        props["PreferredAuthentications"] = "publickey,password,keyboard-interactive"
        session.setConfig(props)
        session.timeout = 15_000
        session.connect(15_000)
        return session
    }

    fun exec(command: String, timeoutMs: Int = 120_000): Result {
        val started = System.currentTimeMillis()
        var session: Session? = null
        try {
            session = connect()
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.setInputStream(null)
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            channel.outputStream = out
            channel.setErrStream(err)
            channel.connect(10_000)
            val deadline = started + timeoutMs
            while (!channel.isClosed) {
                if (System.currentTimeMillis() > deadline) {
                    channel.disconnect()
                    return Result(out.toString("UTF-8"), err.toString("UTF-8") + "\n[timeout after ${timeoutMs}ms]", -1,
                        System.currentTimeMillis() - started)
                }
                Thread.sleep(80)
            }
            return Result(out.toString("UTF-8"), err.toString("UTF-8"), channel.exitStatus, System.currentTimeMillis() - started)
        } finally {
            runCatching { session?.disconnect() }
        }
    }

    /** True when an executable is on PATH inside the remote shell. */
    fun has(tool: String): Boolean = exec("command -v $tool >/dev/null 2>&1; echo \$?").stdout.trim().endsWith("0")
}

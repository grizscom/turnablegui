package ru.pnx.turnablegui

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val DEFAULT_WATCHDOG_INTERVAL_MS = 10_000L

enum class TurnableConnectionState {
    STOPPED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class TurnableStatus(
    val state: TurnableConnectionState,
    val title: String,
    val gatewayLine: String,
    val responseLine: String,
    val healthyLine: String,
    val pid: Int?,
    val lastError: String?
)

object TurnableProcess {
    private val logLock = Any()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var childPid: Int? = null

    @Volatile
    private var connectionState: TurnableConnectionState = TurnableConnectionState.STOPPED

    @Volatile
    private var lastStateChangeAt: Long = System.currentTimeMillis()

    @Volatile
    private var lastHealthyAt: Long = 0L

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var lastGateway: String? = null

    @Volatile
    private var lastListenAddress: String = ""

    @Volatile
    private var lastConfigText: String = ""

    @Volatile
    private var lastVerbose: Boolean = true

    @Volatile
    private var showVerboseLog: Boolean = true

    @Volatile
    private var lastRestartAt: Long = 0L

    @Volatile
    private var lastRttMs: Long? = null

    fun isRunning(): Boolean {
        return process?.isAlive == true
    }

    fun setShowVerboseLog(value: Boolean) {
        showVerboseLog = value
    }

    @Synchronized
    fun start(
        context: Context,
        listenAddress: String,
        configText: String,
        verbose: Boolean
    ): String {
        val appContext = context.applicationContext

        if (isRunning()) {
            return "Turnable уже запущен"
        }

        val binary = File(appContext.applicationInfo.nativeLibraryDir, "libturnable.so")
        val logFile = getLogFile(appContext)
        val pidFile = getPidFile(appContext)

        lastListenAddress = listenAddress
        lastConfigText = configText
        lastVerbose = verbose
        showVerboseLog = verbose
        lastGateway = extractGateway(configText)

        setState(TurnableConnectionState.CONNECTING)
        lastError = null
        lastHealthyAt = 0L
        lastRttMs = null
        childPid = null
        pidFile.delete()

        appendLog(logFile, "Starting Turnable")
        appendLog(logFile, "Binary: ${binary.absolutePath}")
        appendLog(logFile, "Listen: $listenAddress")

        lastGateway?.let {
            appendLog(logFile, "Gateway from config: $it")
        }

        if (!binary.exists()) {
            val msg = "Бинарник не найден: ${binary.absolutePath}"
            lastError = msg
            setState(TurnableConnectionState.ERROR)
            appendLog(logFile, msg)
            return msg
        }

        val args = buildArgs(
            context = appContext,
            binary = binary,
            listenAddress = listenAddress,
            configText = configText
        )

        appendLog(logFile, "Command: ${redactCommandForLog(args)}")

        val shellCommand = buildShellExecCommand(
            pidFile = pidFile,
            args = args
        )

        val builder = ProcessBuilder("/system/bin/sh", "-c", shellCommand)
            .directory(appContext.filesDir)
            .redirectErrorStream(true)

        builder.environment()["HOME"] = appContext.filesDir.absolutePath
        builder.environment()["TMPDIR"] = appContext.cacheDir.absolutePath
        builder.environment()["GODEBUG"] = "netdns=cgo"

        process = builder.start()

        val currentProcess = process ?: return "Не удалось создать процесс"
        childPid = waitForPidFile(pidFile, timeoutMs = 1_000L)

        Thread {
            try {
                readProcessOutputToLog(
                    inputStream = currentProcess.inputStream,
                    logFile = logFile
                )
            } catch (e: Exception) {
                appendLog(logFile, "Log reader stopped: ${e.message}")
            }
        }.apply { name = "TurnableLogReader" }.start()

        startWatchdog(
            context = appContext,
            watchedProcess = currentProcess
        )

        val pidPart = childPid?.let { ", PID=$it" } ?: ""
        return "Turnable запущен$pidPart"
    }

    @Synchronized
    fun stop(context: Context): String {
        val appContext = context.applicationContext
        val logFile = getLogFile(appContext)

        val p = process
        if (p == null || !p.isAlive) {
            process = null
            childPid = null
            setState(TurnableConnectionState.STOPPED)
            appendLog(logFile, "Stop requested, but Turnable is not running")
            return "Turnable не запущен"
        }

        appendLog(logFile, "Stopping Turnable")
        stopProcessOnly(appContext, logFile)
        setState(TurnableConnectionState.STOPPED)

        return "Turnable остановлен"
    }

    @Synchronized
    fun restart(context: Context, reason: String): String {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val minRestartMs = SettingsStore.loadMinRestartIntervalSec(appContext) * 1000L

        if (now - lastRestartAt < minRestartMs) {
            return "Restart suppressed: too soon"
        }

        lastRestartAt = now

        val logFile = getLogFile(appContext)
        appendLog(logFile, "Watchdog restart requested: $reason")

        val listen = lastListenAddress.ifBlank { SettingsStore.loadListenAddress(appContext) }
        val config = lastConfigText.ifBlank { SettingsStore.loadConfigText(appContext) }
        val verbose = lastVerbose

        if (listen.isBlank() || config.isBlank()) {
            val msg = "Watchdog restart skipped: no saved start parameters"
            appendLog(logFile, msg)
            return msg
        }

        stopProcessOnly(appContext, logFile)
        Thread.sleep(1_200L)

        return start(
            context = appContext,
            listenAddress = listen,
            configText = config,
            verbose = verbose
        )
    }

    fun readLog(context: Context, maxBytes: Int = 64 * 1024): String {
        val file = getLogFile(context.applicationContext)
        if (!file.exists()) return "Лога пока нет"

        val bytes = file.readBytes()
        val start = (bytes.size - maxBytes).coerceAtLeast(0)

        val raw = bytes.copyOfRange(start, bytes.size).toString(Charsets.UTF_8)
        val cleaned = cleanTurnableLog(raw)

        return if (showVerboseLog) {
            cleaned
        } else {
            filterVerboseLines(cleaned)
        }
    }

    fun clearLog(context: Context) {
        synchronized(logLock) {
            getLogFile(context.applicationContext).writeText("")
        }
    }

    fun rawLogForShare(context: Context, maxBytes: Int = 256 * 1024): String {
        val file = getLogFile(context.applicationContext)
        if (!file.exists()) return "Лога пока нет"

        val bytes = file.readBytes()
        val start = (bytes.size - maxBytes).coerceAtLeast(0)
        return cleanTurnableLog(bytes.copyOfRange(start, bytes.size).toString(Charsets.UTF_8))
    }

    fun statusText(): String {
        val snapshot = statusSnapshot()
        return "${snapshot.title}: ${snapshot.gatewayLine}, ${snapshot.responseLine}, ${snapshot.healthyLine}"
    }

    fun notificationText(): String {
        val snapshot = statusSnapshot()

        return when (snapshot.state) {
            TurnableConnectionState.STOPPED -> "Turnable остановлен"
            TurnableConnectionState.CONNECTING -> "Turnable подключается..."
            TurnableConnectionState.CONNECTED -> {
                val response = snapshot.responseLine.removePrefix("Server response: ")
                val healthy = snapshot.healthyLine.removePrefix("Healthy: ")
                "Turnable подключен · $response · healthy $healthy"
            }
            TurnableConnectionState.RECONNECTING -> {
                val healthy = snapshot.healthyLine.removePrefix("Healthy: ")
                "Turnable переподключается · healthy $healthy"
            }
            TurnableConnectionState.ERROR -> "Turnable ошибка"
        }
    }

    fun statusSnapshot(): TurnableStatus {
        val pid = childPid

        val state = if (process?.isAlive == true) {
            connectionState
        } else {
            if (connectionState == TurnableConnectionState.STOPPED) {
                TurnableConnectionState.STOPPED
            } else {
                TurnableConnectionState.ERROR
            }
        }

        val title = when (state) {
            TurnableConnectionState.STOPPED -> "Disconnected"
            TurnableConnectionState.CONNECTING -> "Connecting..."
            TurnableConnectionState.CONNECTED -> "Connected"
            TurnableConnectionState.RECONNECTING -> "Reconnecting..."
            TurnableConnectionState.ERROR -> "Error"
        }

        val gatewayLine = when {
            state == TurnableConnectionState.STOPPED -> "Gateway: stopped"
            lastGateway.isNullOrBlank() -> "Gateway: неизвестен"
            else -> "Gateway: $lastGateway"
        }

        val responseLine = when (state) {
            TurnableConnectionState.CONNECTED,
            TurnableConnectionState.RECONNECTING -> {
                val rtt = lastRttMs
                if (rtt != null) {
                    "Server response: ${rtt} ms"
                } else {
                    "Server response: waiting..."
                }
            }
            TurnableConnectionState.CONNECTING -> "Server response: waiting..."
            TurnableConnectionState.ERROR -> "Server response: unavailable"
            TurnableConnectionState.STOPPED -> "Server response: stopped"
        }

        val healthyLine = when {
            state == TurnableConnectionState.STOPPED -> "Healthy: stopped"
            lastHealthyAt > 0L -> {
                val seconds = ((System.currentTimeMillis() - lastHealthyAt) / 1000L).coerceAtLeast(0)
                "Healthy: ${seconds}s ago"
            }
            else -> "Healthy: waiting..."
        }

        return TurnableStatus(
            state = state,
            title = title,
            gatewayLine = gatewayLine,
            responseLine = responseLine,
            healthyLine = healthyLine,
            pid = pid,
            lastError = lastError
        )
    }

    private fun stopProcessOnly(context: Context, logFile: File) {
        val p = process
        val pid = childPid

        if (pid != null) {
            runCatching {
                ProcessBuilder("/system/bin/kill", pid.toString())
                    .start()
                    .waitFor(700, TimeUnit.MILLISECONDS)
            }
        }

        if (p != null && p.isAlive) {
            p.destroy()

            try {
                if (!p.waitFor(1_500L, TimeUnit.MILLISECONDS)) {
                    appendLog(logFile, "Process still alive, killing forcibly")
                    p.destroyForcibly()
                    p.waitFor(1_500L, TimeUnit.MILLISECONDS)
                }
            } catch (_: Exception) {
                p.destroyForcibly()
            }
        }

        process = null
        childPid = null
        getPidFile(context).delete()
    }

    private fun startWatchdog(
        context: Context,
        watchedProcess: Process
    ) {
        Thread {
            while (process === watchedProcess) {
                try {
                    Thread.sleep(DEFAULT_WATCHDOG_INTERVAL_MS)

                    val current = process
                    if (current !== watchedProcess) {
                        return@Thread
                    }

                    if (!SettingsStore.loadWatchdogEnabled(context)) {
                        continue
                    }

                    val now = System.currentTimeMillis()

                    if (!watchedProcess.isAlive) {
                        restart(context, "process died")
                        return@Thread
                    }

                    val reason = when (connectionState) {
                        TurnableConnectionState.CONNECTING -> {
                            if (now - lastStateChangeAt > SettingsStore.loadConnectTimeoutSec(context) * 1000L) {
                                "connect timeout"
                            } else {
                                null
                            }
                        }

                        TurnableConnectionState.RECONNECTING -> {
                            if (now - lastStateChangeAt > SettingsStore.loadReconnectTimeoutSec(context) * 1000L) {
                                "reconnect timeout: ${lastError ?: "unknown"}"
                            } else {
                                null
                            }
                        }

                        TurnableConnectionState.ERROR -> {
                            if (now - lastStateChangeAt > SettingsStore.loadErrorTimeoutSec(context) * 1000L) {
                                "error timeout: ${lastError ?: "unknown"}"
                            } else {
                                null
                            }
                        }

                        TurnableConnectionState.CONNECTED -> {
                            if (lastHealthyAt > 0L && now - lastHealthyAt > SettingsStore.loadHealthTimeoutSec(context) * 1000L) {
                                "tinymux health timeout"
                            } else {
                                null
                            }
                        }

                        TurnableConnectionState.STOPPED -> null
                    }

                    if (reason != null) {
                        restart(context, reason)
                        return@Thread
                    }
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (e: Exception) {
                    val logFile = getLogFile(context)
                    appendLog(logFile, "Watchdog error: ${e.message}")
                    return@Thread
                }
            }
        }.apply { name = "TurnableWatchdog" }.start()
    }

    private fun readProcessOutputToLog(
        inputStream: InputStream,
        logFile: File
    ) {
        val buffer = ByteArray(4096)
        val pendingLine = StringBuilder()

        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0) break

            val chunk = String(buffer, 0, read, Charsets.UTF_8)
            val cleaned = cleanTurnableLog(chunk)

            pendingLine.append(cleaned)

            while (true) {
                val newlineIndex = pendingLine.indexOf("\n")
                if (newlineIndex < 0) break

                val line = pendingLine.substring(0, newlineIndex).trim()
                pendingLine.delete(0, newlineIndex + 1)

                if (line.isNotBlank()) {
                    updateStateFromLogLine(line)
                    appendLog(logFile, line)
                }
            }
        }

        val rest = pendingLine.toString().trim()
        if (rest.isNotBlank()) {
            updateStateFromLogLine(rest)
            appendLog(logFile, rest)
        }
    }

    private fun updateStateFromLogLine(line: String) {
        val lower = line.lowercase(Locale.US)
        val now = System.currentTimeMillis()

        if (lower.contains("tinymux client health ok")) {
            lastRttMs = extractRttMs(line)
        }

        when {
            lower.contains("tinymux client health ok") ||
                lower.contains("turnable client started") ||
                lower.contains("relay client session connected") ||
                lower.contains("rtp client handshake completed") ||
                lower.contains("peer online") ||
                lower.contains("local udp listener started") ||
                lower.contains("channel opened") -> {
                lastHealthyAt = now
                lastError = null
                setState(TurnableConnectionState.CONNECTED)
            }

            lower.contains("tinymux client pong timeout") ||
                lower.contains("minymux client pong timeout") ||
                lower.contains("starting full reconnect") ||
                lower.contains("reconnect failed") ||
                lower.contains("session died") ||
                lower.contains("signaling websocket dial failed") -> {
                lastError = line
                setState(TurnableConnectionState.RECONNECTING)
            }

            lower.contains("failed to start vpn client") ||
                lower.contains("network is unreachable") ||
                lower.contains("context deadline exceeded") ||
                lower.contains("handshake error") -> {
                lastError = line

                if (connectionState == TurnableConnectionState.CONNECTED) {
                    setState(TurnableConnectionState.RECONNECTING)
                } else {
                    setState(TurnableConnectionState.ERROR)
                }
            }
        }
    }

    private fun setState(newState: TurnableConnectionState) {
        if (connectionState != newState) {
            connectionState = newState
            lastStateChangeAt = System.currentTimeMillis()
        }
    }

    private fun buildArgs(
        context: Context,
        binary: File,
        listenAddress: String,
        configText: String
    ): List<String> {
        val cleanConfig = configText.trim()

        val args = mutableListOf<String>()
        args += binary.absolutePath
        args += "client"
        args += "-l"
        args += listenAddress
        args += "-V"
        args += "-i"

        if (cleanConfig.startsWith("{")) {
            val configFile = File(context.filesDir, "client-config.json")
            configFile.writeText(cleanConfig)
            args += "-c"
            args += configFile.absolutePath
        } else {
            args += cleanConfig
        }

        return args
    }

    private fun buildShellExecCommand(
        pidFile: File,
        args: List<String>
    ): String {
        val command = args.joinToString(" ") { shellQuote(it) }
        return "echo \$\$ > ${shellQuote(pidFile.absolutePath)}; exec $command"
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun waitForPidFile(
        pidFile: File,
        timeoutMs: Long
    ): Int? {
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val pid = readPidFile(pidFile)
            if (pid != null && pid > 0) {
                return pid
            }

            Thread.sleep(50L)
        }

        return readPidFile(pidFile)
    }

    private fun readPidFile(pidFile: File): Int? {
        return try {
            if (!pidFile.exists()) return null
            pidFile.readText().trim().toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun extractGateway(configText: String): String? {
        val raw = Regex("[?&]gateway=([^&#]+)")
            .find(configText)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null

        return try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
    }

    private fun extractRttMs(line: String): Long? {
        return Regex("rtt_ms=(\\d+)")
            .find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
    }

    private fun cleanTurnableLog(raw: String): String {
        return raw
            .replace(Regex("\u001B\\[[0-?]*[ -/]*[@-~]"), "")
            .replace(Regex("\u001B\\].*?(?:\u0007|\u001B\\\\)"), "")
            .replace(Regex("\\[(?:0|1|2|3[0-9]|4[0-9]|9[0-9])m"), "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")
    }

    private fun filterVerboseLines(log: String): String {
        return log
            .lineSequence()
            .filterNot { line ->
                line.contains("[DEBUG]") ||
                    line.contains("go package net:") ||
                    line.contains("Command:") ||
                    line.contains("vk browser profile") ||
                    line.contains("captcha check values")
            }
            .joinToString("\n")
    }

    private fun redactCommandForLog(args: List<String>): String {
        return args.map { arg ->
            when {
                arg.startsWith("turnable://") -> "turnable://***redacted***"
                arg.length > 160 && (arg.contains("pub_key=") || arg.contains("gateway=")) -> "***redacted-config-url***"
                else -> arg
            }
        }.joinToString(" ")
    }

    private fun redactSensitiveLine(line: String): String {
        var redacted = line

        redacted = redacted.replace(
            Regex("turnable://\\S+"),
            "turnable://***redacted***"
        )

        redacted = redacted.replace(
            Regex("(?i)(token|access_token|auth|session|password|secret|key)=([^\\s&]+)"),
            "$1=***redacted***"
        )

        redacted = redacted.replace(
            Regex("(?i)(pub_key=)([^\\s&]+)"),
            "$1***redacted***"
        )

        return redacted
    }

    private fun getLogFile(context: Context): File {
        return File(context.filesDir, "turnable.log")
    }

    private fun getPidFile(context: Context): File {
        return File(context.filesDir, "turnable.pid")
    }

    private fun appendLog(file: File, line: String) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val safeLine = redactSensitiveLine(line)

        synchronized(logLock) {
            FileOutputStream(file, true).bufferedWriter().use { writer ->
                writer.append("[$time] ")
                writer.append(safeLine)
                writer.newLine()
            }
        }
    }
}

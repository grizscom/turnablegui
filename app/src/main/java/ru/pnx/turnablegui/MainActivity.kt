package ru.pnx.turnablegui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.app.PendingIntent

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Если уведомления запрещены, foreground service всё равно виден в системном task manager.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                TurnableScreen(
                    onNeedNotificationPermission = {
                        requestNotificationPermissionIfNeeded()
                    }
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun TurnableScreen(
    onNeedNotificationPermission: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var listenAddress by rememberSaveable {
        mutableStateOf(SettingsStore.loadListenAddress(context))
    }

    var configText by rememberSaveable {
        mutableStateOf(SettingsStore.loadConfigText(context))
    }

    var verbose by rememberSaveable {
        mutableStateOf(SettingsStore.loadVerbose(context))
    }

    var autoScrollLog by rememberSaveable {
        mutableStateOf(SettingsStore.loadAutoScrollLog(context))
    }

    var openVpnAutostart by rememberSaveable {
        mutableStateOf(SettingsStore.loadOpenVpnAutostart(context))
    }

    var openVpnPackage by rememberSaveable {
        mutableStateOf(SettingsStore.loadOpenVpnPackage(context))
    }

    var openVpnProfileName by rememberSaveable {
        mutableStateOf(SettingsStore.loadOpenVpnProfileName(context))
    }

    var autoStartOnOpen by rememberSaveable {
        mutableStateOf(SettingsStore.loadAutoStartOnOpen(context))
    }

    var autoStartOnBoot by rememberSaveable {
        mutableStateOf(SettingsStore.loadAutoStartOnBoot(context))
    }

    var watchdogEnabled by rememberSaveable {
        mutableStateOf(SettingsStore.loadWatchdogEnabled(context))
    }

    var connectTimeout by rememberSaveable {
        mutableStateOf(SettingsStore.loadConnectTimeoutSec(context).toString())
    }

    var reconnectTimeout by rememberSaveable {
        mutableStateOf(SettingsStore.loadReconnectTimeoutSec(context).toString())
    }

    var errorTimeout by rememberSaveable {
        mutableStateOf(SettingsStore.loadErrorTimeoutSec(context).toString())
    }

    var healthTimeout by rememberSaveable {
        mutableStateOf(SettingsStore.loadHealthTimeoutSec(context).toString())
    }

    var minRestartInterval by rememberSaveable {
        mutableStateOf(SettingsStore.loadMinRestartIntervalSec(context).toString())
    }

    var currentProfile by rememberSaveable {
        mutableIntStateOf(SettingsStore.loadCurrentProfile(context))
    }

    var profileName by rememberSaveable {
        mutableStateOf(SettingsStore.loadProfileName(context, currentProfile))
    }

    var logText by remember {
        mutableStateOf("")
    }

    var connectionTitle by rememberSaveable {
        mutableStateOf(TurnableProcess.statusSnapshot().title)
    }

    var lastErrorText by rememberSaveable {
        mutableStateOf("")
    }

    var connectionGatewayLine by rememberSaveable {
        mutableStateOf(TurnableProcess.statusSnapshot().gatewayLine)
    }

    var connectionResponseLine by rememberSaveable {
        mutableStateOf(TurnableProcess.statusSnapshot().responseLine)
    }

    var connectionHealthyLine by rememberSaveable {
        mutableStateOf(TurnableProcess.statusSnapshot().healthyLine)
    }

    val pageScrollState = rememberScrollState()
    val logScrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        onNeedNotificationPermission()
        TurnableProcess.setShowVerboseLog(verbose)

        if (autoStartOnOpen && !TurnableProcess.isRunning() && configText.trim().isNotBlank()) {
            TurnableService.start(
                context = context,
                listenAddress = listenAddress.trim(),
                configText = configText.trim(),
                verbose = verbose
            )
        }

        while (true) {
            val snapshot = TurnableProcess.statusSnapshot()

            connectionTitle = snapshot.title
            connectionGatewayLine = snapshot.gatewayLine
            connectionResponseLine = snapshot.responseLine
            connectionHealthyLine = snapshot.healthyLine
            lastErrorText = snapshot.lastError ?: ""

            logText = TurnableProcess.readLog(context)

            delay(if (TurnableProcess.isRunning()) 2_500L else 5_000L)
        }
    }

    LaunchedEffect(logText, autoScrollLog) {
        if (autoScrollLog && logText.isNotBlank()) {
            delay(100)
            logScrollState.animateScrollTo(logScrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(pageScrollState)
            .imePadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Turnable Android Wrap GUI",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LOG",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(
                        text = if (autoScrollLog) "autoscroll on" else "autoscroll off",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                SelectionContainer {
                    Text(
                        text = logText.ifBlank { "Лог пуст" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .verticalScroll(logScrollState),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = connectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = connectionGatewayLine,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = connectionResponseLine,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = connectionHealthyLine,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (lastErrorText.isNotBlank() && connectionTitle != "Connected") {
                    Text(
                        text = "Last error: $lastErrorText",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    if (configText.trim().isEmpty()) {
                        toast(context, "Нужен config URL или JSON")
                        return@Button
                    }

                    saveMainSettings(
                        context = context,
                        listenAddress = listenAddress,
                        configText = configText,
                        verbose = verbose,
                        autoScrollLog = autoScrollLog,
                        autoStartOnOpen = autoStartOnOpen,
                        autoStartOnBoot = autoStartOnBoot,
                        watchdogEnabled = watchdogEnabled,
                        connectTimeout = connectTimeout,
                        reconnectTimeout = reconnectTimeout,
                        errorTimeout = errorTimeout,
                        healthTimeout = healthTimeout,
                        minRestartInterval = minRestartInterval
                    )

                    TurnableProcess.setShowVerboseLog(verbose)
                    TurnableProcess.clearLog(context)
                    logText = ""

                    TurnableService.start(
                        context = context,
                        listenAddress = listenAddress.trim(),
                        configText = configText.trim(),
                        verbose = verbose
                    )

                    connectionTitle = "Connecting..."
                    connectionGatewayLine = "Gateway: parsing..."
                    connectionResponseLine = "Server response: waiting..."
                    connectionHealthyLine = "Healthy: waiting..."
                    lastErrorText = ""
                }
            ) {
                Text("Старт")
            }

            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    TurnableService.stop(context)

                    connectionTitle = "Disconnected"
                    connectionGatewayLine = "Gateway: stopped"
                    connectionResponseLine = "Server response: stopped"
                    connectionHealthyLine = "Healthy: stopped"
                    lastErrorText = ""
                }
            ) {
                Text("Стоп")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    logText = TurnableProcess.readLog(context)
                }
            ) {
                Text("Обновить лог")
            }

            TextButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    TurnableProcess.clearLog(context)
                    logText = ""
                    toast(context, "Лог очищен")
                }
            ) {
                Text("Очистить")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    copyLogToClipboard(context)
                }
            ) {
                Text("Копировать")
            }

            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    shareLog(context)
                }
            ) {
                Text("Поделиться")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Профили",
                    style = MaterialTheme.typography.titleLarge
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (profile in 1..SettingsStore.PROFILE_COUNT) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentProfile = profile

                                SettingsStore.loadProfileIntoMain(context, profile)

                                listenAddress = SettingsStore.loadListenAddress(context)
                                configText = SettingsStore.loadConfigText(context)
                                profileName = SettingsStore.loadProfileName(context, profile)

                                openVpnAutostart = SettingsStore.loadProfileOpenVpnAutostart(context, profile)
                                openVpnPackage = SettingsStore.loadProfileOpenVpnPackage(context, profile)
                                openVpnProfileName = SettingsStore.loadProfileOpenVpnProfileName(context, profile)

                                toast(context, "Загружен профиль $profile")
                            }
                        ) {
                            Text(if (currentProfile == profile) "[$profile]" else "$profile")
                        }
                    }
                }

                OutlinedTextField(
                    value = profileName,
                    onValueChange = {
                        profileName = it
                        SettingsStore.saveProfileName(context, currentProfile, it)
                    },
                    label = { Text("Название профиля") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        SettingsStore.saveProfile(
                            context = context,
                            profile = currentProfile,
                            name = profileName,
                            listen = listenAddress.trim(),
                            config = configText.trim()
                        )

                        SettingsStore.saveProfileOpenVpnAutostart(
                            context = context,
                            profile = currentProfile,
                            value = openVpnAutostart
                        )

                        SettingsStore.saveProfileOpenVpnPackage(
                            context = context,
                            profile = currentProfile,
                            value = openVpnPackage
                        )

                        SettingsStore.saveProfileOpenVpnProfileName(
                            context = context,
                            profile = currentProfile,
                            value = openVpnProfileName
                        )

                        SettingsStore.saveCurrentProfile(context, currentProfile)

                        toast(context, "Профиль $currentProfile сохранён")
                    }
                ) {
                    Text("Сохранить текущие настройки в профиль")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Настройки",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = listenAddress,
                    onValueChange = {
                        listenAddress = it
                        SettingsStore.saveListenAddress(context, it)
                    },
                    label = { Text("Listen address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = configText,
                    onValueChange = {
                        configText = it
                        SettingsStore.saveConfigText(context, it)
                    },
                    label = { Text("Turnable config URL или JSON") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )

                SettingCheckbox(
                    checked = verbose,
                    text = "Показывать DEBUG-лог",
                    onCheckedChange = {
                        verbose = it
                        SettingsStore.saveVerbose(context, it)
                        TurnableProcess.setShowVerboseLog(it)
                        logText = TurnableProcess.readLog(context)
                    }
                )

                SettingCheckbox(
                    checked = autoScrollLog,
                    text = "Автопрокрутка лога",
                    onCheckedChange = {
                        autoScrollLog = it
                        SettingsStore.saveAutoScrollLog(context, it)
                    }
                )

                SettingCheckbox(
                    checked = openVpnAutostart,
                    text = "Auto-start OpenVPN after Turnable connected",
                    onCheckedChange = {
                        openVpnAutostart = it
                        SettingsStore.saveProfileOpenVpnAutostart(context, currentProfile, it)
                    }
                )

                OutlinedTextField(
                    value = openVpnPackage,
                    onValueChange = {
                        openVpnPackage = it
                        SettingsStore.saveProfileOpenVpnPackage(context, currentProfile, it)
                    },
                    label = { Text("OpenVPN package") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = openVpnProfileName,
                    onValueChange = {
                        openVpnProfileName = it
                        SettingsStore.saveProfileOpenVpnProfileName(context, currentProfile, it)
                    },
                    label = { Text("OpenVPN profile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val result = OpenVpnController.connectProfile(
                            context = context,
                            packageName = openVpnPackage,
                            profileName = openVpnProfileName
                        )

                        lastErrorText = result
                    }
                ) {
                    Text("Test OpenVPN profile start")
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val result = OpenVpnController.disconnect(
                            context = context,
                            packageName = openVpnPackage
                        )

                        lastErrorText = result
                    }
                ) {
                    Text("Test OpenVPN disconnect")
                }

                SettingCheckbox(
                    checked = autoStartOnOpen,
                    text = "Автостарт при открытии приложения",
                    onCheckedChange = {
                        autoStartOnOpen = it
                        SettingsStore.saveAutoStartOnOpen(context, it)
                    }
                )

                SettingCheckbox(
                    checked = autoStartOnBoot,
                    text = "Автостарт после перезагрузки телефона",
                    onCheckedChange = {
                        autoStartOnBoot = it
                        SettingsStore.saveAutoStartOnBoot(context, it)
                    }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Watchdog",
                    style = MaterialTheme.typography.titleLarge
                )

                SettingCheckbox(
                    checked = watchdogEnabled,
                    text = "Watchdog включён",
                    onCheckedChange = {
                        watchdogEnabled = it
                        SettingsStore.saveWatchdogEnabled(context, it)
                    }
                )

                TimeoutField(
                    value = connectTimeout,
                    label = "Connect timeout, sec",
                    onValueChange = {
                        connectTimeout = it
                        it.toIntOrNull()?.let { seconds ->
                            SettingsStore.saveConnectTimeoutSec(context, seconds)
                        }
                    }
                )

                TimeoutField(
                    value = reconnectTimeout,
                    label = "Reconnect timeout, sec",
                    onValueChange = {
                        reconnectTimeout = it
                        it.toIntOrNull()?.let { seconds ->
                            SettingsStore.saveReconnectTimeoutSec(context, seconds)
                        }
                    }
                )

                TimeoutField(
                    value = errorTimeout,
                    label = "Error timeout, sec",
                    onValueChange = {
                        errorTimeout = it
                        it.toIntOrNull()?.let { seconds ->
                            SettingsStore.saveErrorTimeoutSec(context, seconds)
                        }
                    }
                )

                TimeoutField(
                    value = healthTimeout,
                    label = "Health timeout, sec",
                    onValueChange = {
                        healthTimeout = it
                        it.toIntOrNull()?.let { seconds ->
                            SettingsStore.saveHealthTimeoutSec(context, seconds)
                        }
                    }
                )

                TimeoutField(
                    value = minRestartInterval,
                    label = "Min restart interval, sec",
                    onValueChange = {
                        minRestartInterval = it
                        it.toIntOrNull()?.let { seconds ->
                            SettingsStore.saveMinRestartIntervalSec(context, seconds)
                        }
                    }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Важно",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Исключи Turnable GUI из OpenVPN/WireGuard/NekoBox VPN-маршрута, иначе получится петля: OpenVPN → Turnable → OpenVPN.",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { openAppSettings(context) }
                    ) {
                        Text("Настройки приложения")
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { openBatterySettings(context) }
                    ) {
                        Text("Батарея")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Package: ${context.packageName}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Version: ${appVersionName(context)}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Core: embedded Turnable binary from jniLibs",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text(
            text = "Standalone APK без Termux. Запускает встроенный Turnable как foreground service.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SettingCheckbox(
    checked: Boolean,
    text: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )

        Text(text)
    }
}

@Composable
private fun TimeoutField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                onValueChange(newValue)
            }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun saveMainSettings(
    context: Context,
    listenAddress: String,
    configText: String,
    verbose: Boolean,
    autoScrollLog: Boolean,
    autoStartOnOpen: Boolean,
    autoStartOnBoot: Boolean,
    watchdogEnabled: Boolean,
    connectTimeout: String,
    reconnectTimeout: String,
    errorTimeout: String,
    healthTimeout: String,
    minRestartInterval: String
) {
    SettingsStore.saveListenAddress(context, listenAddress.trim())
    SettingsStore.saveConfigText(context, configText.trim())
    SettingsStore.saveVerbose(context, verbose)
    SettingsStore.saveAutoScrollLog(context, autoScrollLog)
    SettingsStore.saveAutoStartOnOpen(context, autoStartOnOpen)
    SettingsStore.saveAutoStartOnBoot(context, autoStartOnBoot)
    SettingsStore.saveWatchdogEnabled(context, watchdogEnabled)

    connectTimeout.toIntOrNull()?.let { SettingsStore.saveConnectTimeoutSec(context, it) }
    reconnectTimeout.toIntOrNull()?.let { SettingsStore.saveReconnectTimeoutSec(context, it) }
    errorTimeout.toIntOrNull()?.let { SettingsStore.saveErrorTimeoutSec(context, it) }
    healthTimeout.toIntOrNull()?.let { SettingsStore.saveHealthTimeoutSec(context, it) }
    minRestartInterval.toIntOrNull()?.let { SettingsStore.saveMinRestartIntervalSec(context, it) }
}

private fun copyLogToClipboard(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText("Turnable log", TurnableProcess.rawLogForShare(context))
    )
    toast(context, "Лог скопирован")
}

private fun shareLog(context: Context) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Turnable GUI log")
        putExtra(Intent.EXTRA_TEXT, TurnableProcess.rawLogForShare(context))
    }

    context.startActivity(Intent.createChooser(sendIntent, "Поделиться логом"))
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(intent)
}

private fun openBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        context.startActivity(intent)
    }.onFailure {
        openAppSettings(context)
    }
}

private fun appVersionName(context: Context): String {
    return try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

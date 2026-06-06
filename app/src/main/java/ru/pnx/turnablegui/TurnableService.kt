package ru.pnx.turnablegui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper


class TurnableService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()

        when (intent?.action) {
            ACTION_START -> {
                val listen = intent.getStringExtra(EXTRA_LISTEN) ?: SettingsStore.loadListenAddress(this)
                val config = intent.getStringExtra(EXTRA_CONFIG) ?: SettingsStore.loadConfigText(this)
                val verbose = intent.getBooleanExtra(EXTRA_VERBOSE, SettingsStore.loadVerbose(this))

                val result = TurnableProcess.start(
                    context = this,
                    listenAddress = listen,
                    configText = config,
                    verbose = verbose
                )

                updateNotification(result)
                startNotificationTicker()
            }

            ACTION_STOP -> {
                stopNotificationTicker()
                val result = TurnableProcess.stop(this)
                updateNotification(result)
                stopSelf()
            }

            else -> {
                updateNotification(TurnableProcess.notificationText())
                if (TurnableProcess.isRunning()) {
                    startNotificationTicker()
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopNotificationTicker()
        TurnableProcess.stop(this)
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = buildNotification(TurnableProcess.notificationText())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Turnable",
            NotificationManager.IMPORTANCE_LOW
        )

        channel.description = "Turnable tunnel foreground service"

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        } ?: Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val stopIntent = Intent(this, TurnableService::class.java).apply {
            action = ACTION_STOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            openAppIntent,
            pendingIntentFlags
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            1002,
            stopIntent,
            pendingIntentFlags
        )

        val sessionStartedAt = TurnableProcess.sessionStartedAtWallMs()
        val hasActiveSession = sessionStartedAt > 0L

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "СТОП",
                    stopPendingIntent
                ).build()
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(hasActiveSession)
            .setWhen(if (hasActiveSession) sessionStartedAt else System.currentTimeMillis())
            .setUsesChronometer(hasActiveSession)
            .build()
    }

    private fun startNotificationTicker() {
        if (notificationTickerRunning) return
        notificationTickerRunning = true
        handler.post(notificationTicker)
    }

    private fun stopNotificationTicker() {
        notificationTickerRunning = false
        handler.removeCallbacks(notificationTicker)
    }

    companion object {
        private const val CHANNEL_ID = "turnable_service"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "ru.pnx.turnablegui.action.START"
        private const val ACTION_STOP = "ru.pnx.turnablegui.action.STOP"

        private const val EXTRA_LISTEN = "listen"
        private const val EXTRA_CONFIG = "config"
        private const val EXTRA_VERBOSE = "verbose"

        fun start(
            context: Context,
            listenAddress: String,
            configText: String,
            verbose: Boolean
        ) {
            val intent = Intent(context, TurnableService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LISTEN, listenAddress)
                putExtra(EXTRA_CONFIG, configText)
                putExtra(EXTRA_VERBOSE, verbose)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TurnableService::class.java).apply {
                action = ACTION_STOP
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var notificationTickerRunning = false

    private val notificationTicker = object : Runnable {
        override fun run() {
            if (!notificationTickerRunning) return

            maybeStartOpenVpnProfile()
            updateNotification(TurnableProcess.notificationText())

            if (TurnableProcess.isRunning()) {
                handler.postDelayed(this, 2_000L)
            } else {
                notificationTickerRunning = false
                stopSelf()
            }
        }
    }

    private fun maybeStartOpenVpnProfile() {
        val autostart = SettingsStore.loadOpenVpnAutostart(this)
        if (!autostart) return

        if (!TurnableProcess.shouldStartOpenVpnNow()) return

        val openVpnPackage = SettingsStore.loadOpenVpnPackage(this)
        val openVpnProfile = SettingsStore.loadOpenVpnProfileName(this)

        val result = OpenVpnController.connectProfile(
            context = this,
            packageName = openVpnPackage,
            profileName = openVpnProfile
        )

        android.util.Log.i("TurnableGUI", result)
    }
}

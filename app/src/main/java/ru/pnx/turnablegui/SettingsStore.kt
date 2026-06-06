package ru.pnx.turnablegui

import android.content.Context

object SettingsStore {
    private const val PREFS_NAME = "turnable_settings"

    private const val KEY_LISTEN_ADDRESS = "listen_address"
    private const val KEY_CONFIG_TEXT = "config_text"
    private const val KEY_VERBOSE = "verbose"
    private const val KEY_AUTO_SCROLL_LOG = "auto_scroll_log"
    private const val KEY_AUTO_START_ON_OPEN = "auto_start_on_open"
    private const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
    private const val KEY_WATCHDOG_ENABLED = "watchdog_enabled"
    private const val KEY_CONNECT_TIMEOUT_SEC = "connect_timeout_sec"
    private const val KEY_RECONNECT_TIMEOUT_SEC = "reconnect_timeout_sec"
    private const val KEY_ERROR_TIMEOUT_SEC = "error_timeout_sec"
    private const val KEY_HEALTH_TIMEOUT_SEC = "health_timeout_sec"
    private const val KEY_MIN_RESTART_INTERVAL_SEC = "min_restart_interval_sec"
    private const val KEY_CURRENT_PROFILE = "current_profile"

    private const val KEY_OPENVPN_AUTOSTART = "openvpn_autostart"
    private const val KEY_OPENVPN_PACKAGE = "openvpn_package"
    private const val KEY_OPENVPN_PROFILE_NAME = "openvpn_profile_name"

    const val PROFILE_COUNT = 3

    fun loadListenAddress(context: Context): String {
        return prefs(context).getString(KEY_LISTEN_ADDRESS, "127.0.0.1:5080")
            ?: "127.0.0.1:5080"
    }

    fun saveListenAddress(context: Context, value: String) {
        prefs(context).edit()
            .putString(KEY_LISTEN_ADDRESS, value)
            .apply()
    }

    fun loadConfigText(context: Context): String {
        return prefs(context).getString(KEY_CONFIG_TEXT, "") ?: ""
    }

    fun saveConfigText(context: Context, value: String) {
        prefs(context).edit()
            .putString(KEY_CONFIG_TEXT, value)
            .apply()
    }

    fun loadVerbose(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_VERBOSE, true)
    }

    fun saveVerbose(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_VERBOSE, value)
            .apply()
    }

    fun loadAutoScrollLog(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_SCROLL_LOG, true)
    }

    fun saveAutoScrollLog(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_AUTO_SCROLL_LOG, value)
            .apply()
    }

    fun loadAutoStartOnOpen(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_START_ON_OPEN, false)
    }

    fun saveAutoStartOnOpen(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_AUTO_START_ON_OPEN, value)
            .apply()
    }

    fun loadAutoStartOnBoot(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_START_ON_BOOT, false)
    }

    fun saveAutoStartOnBoot(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_AUTO_START_ON_BOOT, value)
            .apply()
    }

    fun loadWatchdogEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_WATCHDOG_ENABLED, true)
    }

    fun saveWatchdogEnabled(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_WATCHDOG_ENABLED, value)
            .apply()
    }

    fun loadConnectTimeoutSec(context: Context): Int {
        return prefs(context).getInt(KEY_CONNECT_TIMEOUT_SEC, 45).coerceIn(15, 600)
    }

    fun saveConnectTimeoutSec(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_CONNECT_TIMEOUT_SEC, value.coerceIn(15, 600))
            .apply()
    }

    fun loadReconnectTimeoutSec(context: Context): Int {
        return prefs(context).getInt(KEY_RECONNECT_TIMEOUT_SEC, 60).coerceIn(15, 600)
    }

    fun saveReconnectTimeoutSec(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_RECONNECT_TIMEOUT_SEC, value.coerceIn(15, 600))
            .apply()
    }

    fun loadErrorTimeoutSec(context: Context): Int {
        return prefs(context).getInt(KEY_ERROR_TIMEOUT_SEC, 45).coerceIn(15, 600)
    }

    fun saveErrorTimeoutSec(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_ERROR_TIMEOUT_SEC, value.coerceIn(15, 600))
            .apply()
    }

    fun loadHealthTimeoutSec(context: Context): Int {
        return prefs(context).getInt(KEY_HEALTH_TIMEOUT_SEC, 35).coerceIn(15, 600)
    }

    fun saveHealthTimeoutSec(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_HEALTH_TIMEOUT_SEC, value.coerceIn(15, 600))
            .apply()
    }

    fun loadMinRestartIntervalSec(context: Context): Int {
        return prefs(context).getInt(KEY_MIN_RESTART_INTERVAL_SEC, 15).coerceIn(5, 600)
    }

    fun saveMinRestartIntervalSec(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_MIN_RESTART_INTERVAL_SEC, value.coerceIn(5, 600))
            .apply()
    }

    fun loadCurrentProfile(context: Context): Int {
        return prefs(context).getInt(KEY_CURRENT_PROFILE, 1).coerceIn(1, PROFILE_COUNT)
    }

    fun saveCurrentProfile(context: Context, profile: Int) {
        prefs(context).edit()
            .putInt(KEY_CURRENT_PROFILE, profile.coerceIn(1, PROFILE_COUNT))
            .apply()
    }

    fun loadProfileName(context: Context, profile: Int): String {
        val p = profile.coerceIn(1, PROFILE_COUNT)
        return prefs(context).getString("profile_${p}_name", "Профиль $p") ?: "Профиль $p"
    }

    fun saveProfileName(context: Context, profile: Int, name: String) {
        val p = profile.coerceIn(1, PROFILE_COUNT)
        prefs(context).edit()
            .putString("profile_${p}_name", name.ifBlank { "Профиль $p" })
            .apply()
    }

    fun loadProfileListenAddress(context: Context, profile: Int): String {
        val p = profile.coerceIn(1, PROFILE_COUNT)
        return prefs(context).getString("profile_${p}_listen", loadListenAddress(context))
            ?: loadListenAddress(context)
    }

    fun saveProfileListenAddress(context: Context, profile: Int, value: String) {
        val p = profile.coerceIn(1, PROFILE_COUNT)
        prefs(context).edit()
            .putString("profile_${p}_listen", value)
            .apply()
    }

    fun loadProfileConfigText(context: Context, profile: Int): String {
        val p = profile.coerceIn(1, PROFILE_COUNT)
        return prefs(context).getString("profile_${p}_config", "") ?: ""
    }

    fun saveProfileConfigText(context: Context, profile: Int, value: String) {
        val p = profile.coerceIn(1, PROFILE_COUNT)
        prefs(context).edit()
            .putString("profile_${p}_config", value)
            .apply()
    }

    fun saveProfile(context: Context, profile: Int, name: String, listen: String, config: String) {
        val p = profile.coerceIn(1, PROFILE_COUNT)
        prefs(context).edit()
            .putString("profile_${p}_name", name.ifBlank { "Профиль $p" })
            .putString("profile_${p}_listen", listen)
            .putString("profile_${p}_config", config)
            .apply()
    }

    fun loadProfileIntoMain(context: Context, profile: Int) {
        val p = profile.coerceIn(1, PROFILE_COUNT)
        saveCurrentProfile(context, p)
        saveListenAddress(context, loadProfileListenAddress(context, p))
        saveConfigText(context, loadProfileConfigText(context, p))
    }

    fun loadOpenVpnAutostart(context: Context): Boolean {
        return loadProfileOpenVpnAutostart(
            context = context,
            profile = loadCurrentProfile(context)
        )
    }

    fun saveOpenVpnAutostart(context: Context, value: Boolean) {
        saveProfileOpenVpnAutostart(
            context = context,
            profile = loadCurrentProfile(context),
            value = value
        )
    }

    fun loadOpenVpnPackage(context: Context): String {
        return loadProfileOpenVpnPackage(
            context = context,
            profile = loadCurrentProfile(context)
        )
    }

    fun saveOpenVpnPackage(context: Context, value: String) {
        saveProfileOpenVpnPackage(
            context = context,
            profile = loadCurrentProfile(context),
            value = value
        )
    }

    fun loadOpenVpnProfileName(context: Context): String {
        return loadProfileOpenVpnProfileName(
            context = context,
            profile = loadCurrentProfile(context)
        )
    }

    fun saveOpenVpnProfileName(context: Context, value: String) {
        saveProfileOpenVpnProfileName(
            context = context,
            profile = loadCurrentProfile(context),
            value = value
        )
    }

    fun loadProfileOpenVpnAutostart(context: Context, profile: Int): Boolean {
        val p = profile.coerceIn(1, PROFILE_COUNT)

        return prefs(context).getBoolean(
            "profile_${p}_openvpn_autostart",
            prefs(context).getBoolean(KEY_OPENVPN_AUTOSTART, false)
        )
    }

    fun saveProfileOpenVpnAutostart(context: Context, profile: Int, value: Boolean) {
        val p = profile.coerceIn(1, PROFILE_COUNT)

        prefs(context).edit()
            .putBoolean("profile_${p}_openvpn_autostart", value)
            .apply()
    }

    fun loadProfileOpenVpnPackage(context: Context, profile: Int): String {
        val p = profile.coerceIn(1, PROFILE_COUNT)

        return prefs(context).getString(
            "profile_${p}_openvpn_package",
            prefs(context).getString(KEY_OPENVPN_PACKAGE, "de.blinkt.openvpn")
        ) ?: "de.blinkt.openvpn"
    }

    fun saveProfileOpenVpnPackage(context: Context, profile: Int, value: String) {
        val p = profile.coerceIn(1, PROFILE_COUNT)

        prefs(context).edit()
            .putString("profile_${p}_openvpn_package", value.ifBlank { "de.blinkt.openvpn" })
            .apply()
    }

    fun loadProfileOpenVpnProfileName(context: Context, profile: Int): String {
        val p = profile.coerceIn(1, PROFILE_COUNT)

        return prefs(context).getString(
            "profile_${p}_openvpn_profile_name",
            prefs(context).getString(KEY_OPENVPN_PROFILE_NAME, "")
        ) ?: ""
    }

    fun saveProfileOpenVpnProfileName(context: Context, profile: Int, value: String) {
        val p = profile.coerceIn(1, PROFILE_COUNT)

        prefs(context).edit()
            .putString("profile_${p}_openvpn_profile_name", value)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

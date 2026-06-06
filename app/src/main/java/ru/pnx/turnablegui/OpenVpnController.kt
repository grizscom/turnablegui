package ru.pnx.turnablegui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

object OpenVpnController {
    private const val DEFAULT_OPENVPN_PACKAGE = "de.blinkt.openvpn"
    private const val CONNECT_CLASS = "de.blinkt.openvpn.api.ConnectVPN"
    private const val DISCONNECT_CLASS = "de.blinkt.openvpn.api.DisconnectVPN"

    private const val EXTRA_PROFILE_NAME = "de.blinkt.openvpn.api.profileName"

    fun connectProfile(
        context: Context,
        packageName: String,
        profileName: String
    ): String {
        val cleanPackage = packageName.trim().ifBlank { DEFAULT_OPENVPN_PACKAGE }
        val cleanProfile = profileName.trim()

        if (cleanProfile.isBlank()) {
            return "OpenVPN profile name is empty"
        }

        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClassName(cleanPackage, CONNECT_CLASS)
                putExtra(EXTRA_PROFILE_NAME, cleanProfile)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            "OpenVPN start requested: $cleanProfile"
        } catch (e: ActivityNotFoundException) {
            "OpenVPN ConnectVPN activity not found: ${e.message}"
        } catch (e: Exception) {
            "OpenVPN start failed: ${e.message}"
        }
    }

    fun disconnect(
        context: Context,
        packageName: String
    ): String {
        val cleanPackage = packageName.trim().ifBlank { DEFAULT_OPENVPN_PACKAGE }

        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClassName(cleanPackage, DISCONNECT_CLASS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            "OpenVPN disconnect requested"
        } catch (e: ActivityNotFoundException) {
            "OpenVPN DisconnectVPN activity not found: ${e.message}"
        } catch (e: Exception) {
            "OpenVPN disconnect failed: ${e.message}"
        }
    }
}
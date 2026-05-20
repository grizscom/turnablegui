# Turnable Android Wrap GUI

Standalone Android GUI wrapper for [Turnable](https://github.com/TheAirBlow/Turnable).

Special thanks to TheAirBlow, the author of Turnable, and to the authors of the parent projects.

The app runs the Turnable Android binary as a foreground service and provides a simple UI for:

- starting/stopping Turnable;
- storing Turnable config URL;
- viewing logs;
- watchdog restart;
- status monitoring;
- boot/autostart options;
- debug log filtering;
- multiple profiles;
- notification status;
- battery/settings shortcuts.

## Status

First experimental release: `v0.1.0`.

## Important

This is not a full VPN client. Turnable acts as a relay/transport layer.

Typical usage:

```text
OpenVPN / WireGuard / NekoBox
        v
127.0.0.1:5080
        v
Turnable Android Wrap GUI
        v
Turnable relay/gateway
```

Make sure to exclude this app from your Android VPN client routing.

If Turnable Android Wrap GUI traffic goes through the VPN that itself depends on Turnable, a routing loop may happen.

Recommended local listen address:

```text
127.0.0.1:5080
```

or another loopback port, for example:

```text
127.0.0.1:14222
```

## Build flow

```text
Build requirements
v
Turnable source checkout
v
Apply Turnable tinymux health patch
v
Build Turnable core
v
Build APK
```

## Build requirements

- Windows
- Android Studio
- Android SDK
- Android NDK
- Go
- Git
- PowerShell

## Turnable source checkout

The Turnable source code is not committed into this repository.

Before building the Android binary, clone the upstream Turnable repository into `third_party/Turnable`:

```powershell
mkdir third_party -Force
git clone https://github.com/TheAirBlow/Turnable.git ".\third_party\Turnable"
```

If the directory already exists and you want to update Turnable:

```powershell
git -C ".\third_party\Turnable" pull --ff-only
```

## Build Turnable core

After cloning Turnable, apply the minimal tinymux health log patch used by the Android watchdog:

```powershell
powershell -ExecutionPolicy Bypass -File ".\scripts\patch-turnable-tinymux-health.ps1"
```

Then build the Android native binaries:

```powershell
powershell -ExecutionPolicy Bypass -File ".\scripts\build-turnable-android.ps1"
```

The build script creates:

```text
app/src/main/jniLibs/arm64-v8a/libturnable.so
app/src/main/jniLibs/armeabi-v7a/libturnable.so
```

These generated binaries are not committed to git and must be rebuilt locally before compiling the APK.

## Build APK

Debug build:

```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
```

Release build should be signed with your own Android signing key:

```text
Android Studio > Build > Generate Signed Bundle / APK > APK
```

Do not commit signing keys, passwords, keystore files, generated APK files, logs, or local config files.

## Notes for VPN clients

When using OpenVPN, WireGuard, NekoBox, or another Android VPN client, exclude `Turnable Android Wrap GUI` from VPN routing.

Otherwise, Turnable outbound traffic may be routed through the VPN tunnel that depends on Turnable itself.

## License

This project is released under the GNU General Public License v2.0.

Turnable is also licensed under the GNU General Public License v2.0.

See the `LICENSE` file for details.
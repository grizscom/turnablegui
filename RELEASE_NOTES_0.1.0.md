# Turnable Android Wrap GUI v0.1.0

First experimental release.

## Features

- Standalone Android GUI without Termux.
- Runs Turnable as Android foreground service.
- Start/stop controls.
- Saved config URL.
- Saved listen address.
- Multiple profiles.
- Live log viewer.
- Debug log filtering.
- Auto-scroll log option.
- Watchdog restart logic.
- Status card:
  - Gateway
  - Server response RTT
  - Healthy age
- Notification with connection status.
- Notification tap opens the app.
- Boot receiver for optional autostart.
- Battery/settings shortcuts.
- Sensitive command URL masking in logs.

## Notes

This app is not a complete VPN client. It provides a GUI wrapper around Turnable transport.

For OpenVPN/WireGuard/NekoBox usage, exclude this app from VPN routing to avoid traffic loops.
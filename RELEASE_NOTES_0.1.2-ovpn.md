# Turnable Android Wrap GUI v0.1.2-ovpn

OpenVPN autostart experimental release.

## Changes

- Added OpenVPN for Android profile autostart after successful Turnable connection.
- Added per-profile OpenVPN settings:
  - OpenVPN package name
  - OpenVPN profile name
  - Auto-start OpenVPN after Turnable connected
- Added test buttons:
  - Test OpenVPN profile start
  - Test OpenVPN disconnect
- OpenVPN profile settings are stored separately for each Turnable profile.
- Version bumped to `0.1.2-ovpn`.

## Notes

This release uses OpenVPN for Android external activity API.

Default package:

de.blinkt.openvpn

Make sure the OpenVPN profile name exactly matches the profile name in OpenVPN for Android.

Turnable Android Wrap GUI must be excluded from VPN routing, otherwise a routing loop may happen.

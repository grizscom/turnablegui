# Turnable Android Wrap GUI v0.1.4-ovpn

OpenVPN autostart build with the latest Turnable core and manual VK check flow.

## Changes

- Updated embedded Turnable build target to upstream `0.5.1`.
- Added manual VK check WebView flow from main `v0.1.3`.
- Kept OpenVPN profile autostart and per-profile OpenVPN settings.
- Fixed status/navigation bar icon contrast on light UI.
- Kept compact notification text: `On · Delay ... · Healthy ...`.

## Notes

- OpenVPN autostart remains experimental.
- `Turnable GUI` should be excluded from VPN routing to avoid routing loops.

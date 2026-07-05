# Turnable Android Wrap GUI v0.1.3

Manual VK captcha fallback release.

## Changes

- Added in-app WebView for manual VK captcha solving.
- Detects Turnable `manual captcha solve required` log events and shows the captcha UI automatically.
- Injects the Turnable captcha userscript from the local `127.0.0.1:1984` helper server.
- Keeps browser/guide/userscript fallback buttons for troubleshooting.
- Version bumped to `0.1.3`.
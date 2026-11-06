# JP5S test line

[**Download Grapefruit Settings 1.0 · JP5S beta 2**](https://raw.githubusercontent.com/hayliewordsman/grapefruitmusic/codex/jp5s-grapefruit-settings/downloads/jp5s/Grapefruit-Settings-1.0-jp5s-beta2.apk)

Beta 2 adds an entirely in-app Wi-Fi selector, password dialogs, saved networks,
disconnect/forget, and connection details. Wi-Fi no longer opens the crashing
OEM page. Copy/Cut/Paste/Select all remain available in Jelly Bean dialogs. Back
uses drawn arrows and returns to the actual parent page, preserving scroll.
It installs as an update over beta 1 and keeps existing app preferences.

Install normally beside the existing Settings app. Keep stock Settings installed.
This APK modifies the frontend only and is signed with the same public AOSP test
certificate as the supplied JP5S frontend. Android 4.2.2 / API 17 remains the
minimum. No SettingsProvider APK is included. No F-Droid publication is configured.

Includes all four Launcher themes, Grapefruit Dark by default, a new icon and
themed in-app icons/switches, universal theme controls and the Jelly Bean text
selection/editing fix. Native OEM pages keep their existing layouts with theme,
icon and switch changes. Device testing on the JP5S remains necessary.

[Build, scope, universal themes and signing details](../../settings/README.md).
[APK checksum](SHA256SUMS). [Signing verification](signature.txt).

The **JP5S theme companions** Actions job adds Audio, TV and Launcher universal
theme builds here, signed using the existing Grapefruit app key. Existing app
releases need these reader updates before they can follow the universal theme.
Earlier debug-signed Launcher betas require their original signing key for an
in-place update; the permanently signed build cannot overwrite them.

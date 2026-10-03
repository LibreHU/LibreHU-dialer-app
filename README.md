# LibreHU Dialer

An automotive phone UI for LibreHU head units, inspired by Android Auto's interaction patterns and visual hierarchy. It uses LibreHU's own visual identity; it is not an Android Auto client or a Google product.

## Current status

**UI prototype — not connected to real telephony.** The current screen is a visual and interaction scaffold with clearly labelled demo contacts. The Call buttons only open a preview screen and do not place or receive calls.

### Included in the first scaffold

- Landscape-first, car-sized interface with high-contrast text and large touch targets.
- Favorites, recent calls, contacts search and numeric keypad.
- Preview in-call screen.
- Light / dark appearance and accent color synchronized with LibreHU Launcher.
- Launcher-family app icon and Material icons using the active launcher accent.
- Fallback to Android's current night mode when the launcher theme provider is unavailable.
- Android 9 / API 28 minimum target and Jetpack Compose.
- GitHub Actions build artifact for the debug APK.

## Shared appearance

The launcher on the `ivi` branch is the source of truth for the effective theme. Its read-only provider, `content://org.librehu.launcher.theme/theme`, exposes `dark` and the effective accent ARGB; it also sends `org.librehu.action.THEME_CHANGED` broadcasts. The dialer observes both, so it follows automatic day/night changes (including the launcher's headlight/time logic) and user-selected launcher accents without duplicating that logic. When the provider is unavailable, the dialer follows Android's current night configuration and a standard blue accent.

The palette follows the shared LibreHU car palette: black / graphite surfaces in dark mode, light grey / white surfaces in light mode, Google-style high contrast text, and the launcher's current accent. The dialer does not try to change system-wide night mode itself.

## Architecture

Keep UI state independent of the phone transport. Proposed layers:

- `ui/`: Compose screens and car-sized reusable components.
- `contacts/`: Contacts Provider access, permissions and normalized contacts.
- `history/`: Call log access where the Android build grants it.
- `telecom/`: real call control and call state.
- `bluetooth/`: device discovery / connection status only if the head-unit Bluetooth stack exposes a supported API.
- `headunit/`: optional LibreHU-service integration; do not use it as a substitute for HFP/Telecom.

Do not assume that Android's public `BluetoothHeadset` APIs control a Bluetooth phone connected to the head unit. On the UJC201, first inspect the original `ivi-btservice.apk`, its manifest, exported services, permissions, AIDL and HFP call-control path. Integrate through a supported interface if one exists. Avoid private API guesses and avoid opening the MCU UART from the dialer.

## Next milestones

1. Build and run the UI on an emulator and the UJC201.
2. Inspect the original Bluetooth service and document the real HFP control path.
3. Replace demo contacts with Contacts Provider data after runtime permission handling.
4. Add actual call history only if the platform exposes it.
5. Implement incoming/outgoing/active call state and audio routing against the confirmed Bluetooth/Telecom interface.
6. Add regression tests for disconnects, missed calls, rotation and display sizes.

## Build

Requires JDK 17 and Android SDK platform/build tools for API 37. GitHub Actions runs:

```sh
gradle assembleDebug
```

The workflow uploads `LibreHU-Dialer-debug`. No release APK is published yet.

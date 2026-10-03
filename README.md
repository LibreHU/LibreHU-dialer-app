# LibreHU Dialer

An automotive phone UI for LibreHU head units, inspired by Android Auto's interaction patterns and visual hierarchy. It uses LibreHU's own visual identity; it is not an Android Auto client or a Google product.

## Current status

**UI prototype — not connected to real telephony.** The current screen is a visual and interaction scaffold with clearly labelled demo contacts. The Call buttons only open a preview screen and do not place or receive calls.

### Included in the first scaffold

- Landscape-first dark interface with high-contrast accent and large touch targets.
- Favorites, recent calls, contacts search and numeric keypad.
- Preview in-call screen.
- Android 9 / API 28 minimum target and Jetpack Compose.
- GitHub Actions build artifact for the debug APK.

## Architecture

Keep UI state independent of the phone transport. Proposed layers:

- `ui/`: Compose screens and car-sized reusable components.
- `contacts/`: Contacts Provider access, permissions and normalized contacts.
- `history/`: Call log access where the Android build grants it.
- `telecom/`: real call control and call state.
- `bluetooth/`: device discovery / connection status only if the head-unit Bluetooth stack exposes a supported API.
- `headunit/`: optional LibreHU-service integration; do not use it as a substitute for HFP/Telecom.

Do not assume that Android's public `BluetoothHeadset` APIs control a Bluetooth phone connected to the head unit. On the UJC201, first inspect the existing `ivi-btservice.apk`, its manifest, exported services, permissions, AIDL and HFP call-control path. Integrate through a supported interface if one exists. Avoid private API guesses and avoid opening the MCU UART from the dialer.

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

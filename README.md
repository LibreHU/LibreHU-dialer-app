# LibreHU Dialer

An automotive phone UI for LibreHU head units, inspired by Android Auto's interaction patterns and visual hierarchy. It uses LibreHU's own visual identity; it is not an Android Auto client or a Google product.

## Current status

**Early integration stage.** The `ivi` branch binds to the stock `com.jancar.btservice` Binder service and sends call, hang-up, answer/reject and microphone-mute commands. Contacts and recents are still demo data; live call-state integers have not yet been mapped, and this branch must be tested on the UJC201 before being treated as functional.

### Included in the first scaffold

- Landscape-first, car-sized interface with high-contrast text and large touch targets.
- Favorites, recent calls, contacts search and numeric keypad.
- In-call command screen driven by Jancar callback events (raw status values until mapped).
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

Do not assume that Android's public `BluetoothHeadset` APIs control a Bluetooth phone connected to the head unit. On the UJC201, the stock `com.jancar.btservice.bluetooth.BluetoothService` is exported and exposes the `com.jancar.btservice.action.bluetooth` binding action. The `ivi` branch binds to this service and uses verified Binder transaction IDs for a small initial command set. It intentionally displays raw call/connection status codes until their meanings are confirmed on-device. Avoid private API guesses and never open the MCU UART from the dialer.

## Next milestones

1. Validate service binding and command behavior on the stock UJC201.
2. Map the live `IBluetoothCallback` call/connection status values against the original Jancar UI.
3. Verify outgoing, answer, reject, hang-up and microphone-mute commands with a paired phone.
4. Replace demo contacts with the Jancar phonebook API/provider after confirming its data format and access rules.
5. Add real call history and recovery after service disconnects.
6. Add regression tests for disconnects, missed calls, rotation and display sizes.

## Build

Requires JDK 17 and Android SDK platform/build tools for API 37. GitHub Actions runs:

```sh
gradle assembleDebug
```

The workflow uploads `LibreHU-Dialer-debug`. No release APK is published yet.

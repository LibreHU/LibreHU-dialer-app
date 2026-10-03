# LibreHU architecture notes

This document records the integration boundaries found while comparing the LibreHU repositories and their integration branches.

## Repository integration patterns

| Repository | Integration branches | Observed delta from `main` | Implication |
|---|---|---|---|
| `LibreHU-Launcher-App` | `ivi`, `librehu-service` | 6 and 5 commits ahead respectively; each adds a separate head-unit bridge | Keep UI independent from the hardware backend; do not merge both implementations into UI code |
| `LibreHU-FM-app` | `ivi`, `librehu-service` | 6 and 5 commits ahead; separate radio / antenna bridges | Follow the bridge pattern, but phone control belongs to the Bluetooth service, not the MCU service |
| `LibreHU-widget-app` | `ivi`, `librehu-service` | 3 commits ahead each; source implementations differ | Use normalized app models; expose unavailable data as unknown |
| `LibreHU-BtnRemap-app` | `ivi`, `librehu-service` | 1 commit ahead each; key source and event handling differ | Do not listen to MCU serial directly from the dialer |
| `ViPER4Android-LibreHU` | `vehicle-audio`, `librehu-service` | 4 and 6 commits ahead; direct Jancar clients vs AIDL client and vehicle-audio screen | Prefer the LibreHU service for vehicle/audio controls, but it is not the Bluetooth hands-free controller |
| `MCU-tools-app` | `main-LOS` | Same commit as `main` in the comparison performed | Treat it as an identical ref until a real diff appears |
| `LibreHU-service` | `main` | AIDL API and low-level MCU/audio transport | Owns the MCU UART and ROHM audio DSP; not phone HFP call control |
| `android_device_alps_ac8257_demo` | recovery tree | TWRP / device support and recovery tooling | Keep recovery-specific code out of the dialer |

### Cross-repository issue

The `ILibreHuService.aidl` and callback files are copied into several app repositories. The copies are currently the integration mechanism, but they can drift when the API changes. The dialer should not copy this AIDL unless it genuinely needs a LibreHU-service function; HFP call control is a different boundary.

## Shared appearance contract

The `LibreHU-Launcher-App` `ivi` branch owns the shared visual theme:

- `ThemeSettings` stores `AUTO`, `LIGHT` and `DARK` modes and the selected accent.
- `ThemeController` resolves AUTO using head-unit headlights when available, otherwise the time-of-day fallback (19:00–07:00), then publishes the effective appearance.
- `ThemeProvider` exposes a read-only row at `content://org.librehu.launcher.theme/theme`: `dark` (0/1) and accent ARGB. It also broadcasts `org.librehu.action.THEME_CHANGED` with `dark` and `accent` extras.
- `CarPalette` defines the shared dark/light surface and text colors; `Accent` defines the paired dark/light tones for blue, teal, green, yellow, orange, red, pink and purple.
- `LibreHU-BtnRemap-app` already implements the provider/broadcast follower pattern. The dialer uses the same contract, rather than inventing an independent automatic mode or hardcoding its own lime palette.
- The FM app shares the same blue/navy circle icon language; the dialer icon now follows that family with a phone glyph.

The dialer observes the provider and theme broadcast while in the foreground. If the launcher is not installed or its provider cannot be read, it falls back to Android's current night configuration and the default blue accent. This keeps the dialer usable as a standalone app while making launcher-driven AUTO and accent changes appear in the dialer.

## Bluetooth / telephony findings

The previously analysed `ivi-btservice.apk` identifies itself as `com.jancar.btservice` version 3.0.0. It contains a persistent `BluetoothService` and exports a Binder service with action:

```
com.jancar.btservice.action.bluetooth
```

The Binder interface `IBluetooth` exposes methods for pairing and power, A2DP, HFP call control (including `callPhone`, `hangPhone`, `requestHFPConnect`, DTMF and audio transfer), contacts/call logs and microphone control. Related callback interfaces include `IBluetoothCallback`, `IBluetoothExecCallback` and `IBluetoothVCardCallback`.

The HFP path involves Android's Bluetooth Headset Client / Telecom components, including `BluetoothHeadsetClientCall`, `HfpClientProfile`, `HfpClientConnectionService`, `InCallService` and `TelecomManager`. PBAP/phonebook integration also uses AutoChips broadcasts and Android Contacts / CallLog providers.

### Implementation rule

1. The `ivi` branch binds explicitly to `com.jancar.btservice.bluetooth.BluetoothService` using `com.jancar.btservice.action.bluetooth`.
2. `backend/jancar/JancarBluetoothClient.kt` owns Binder calls and callback handling; Compose screens do not call Binder methods directly.
3. The initial implementation uses transaction IDs extracted from the vendor `IBluetooth.Stub`: current phone name (52), call (23), hang up (25), reject (26), answer/listen (27), DTMF (31), microphone mute (32), listener registration (38/39) and Bluetooth power query (70).
4. Callback descriptors and transaction IDs are extracted from the APK. Call and connection status integers remain raw until validated on a live UJC201; do not infer state names from the integer alone.
5. Contacts/history integration is not implemented yet; demo contact data remains in the UI until the vendor provider/API format is confirmed.
6. Use `LibreHU-service` only for optional vehicle state or audio-DSP controls. Never open `/dev/ttyS1` from the dialer; `LibreHU-service` is the single owner of the MCU UART.
7. A successful Binder transaction confirms command dispatch, not successful call setup or an established call.

**Validation still required:** run the branch on the stock UJC201 and compare callback events with the original Jancar UI. Command semantics, status values and callback timing must be verified on-device.

## UI target

The current Compose UI binds to the Jancar service for initial call commands and raw call/connection events. Contacts and call history are still demo data; the next milestone is on-device validation, then replacing the demo repository without redesigning the UI.

The goal is to reproduce Android Auto's familiar phone workflow (favorites, recents, contacts, keypad, and call controls) with LibreHU branding and large landscape touch targets. This is a native head-unit UI, not an Android Auto projection app and not an implementation of Google's Android for Cars App Library.

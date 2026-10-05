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

1. Bind to the exported `com.jancar.btservice` Binder service and establish its exact method signatures and callback protocol from the APK before invoking it.
2. Keep a `BluetoothPhoneRepository` / `JancarBluetoothClient` behind an interface; the Compose screens must not call Binder methods directly.
3. Use the service for call setup / answer / hangup, call state, contact synchronization and audio transfer where supported.
4. Use `LibreHU-service` only for optional vehicle state or audio-DSP controls.
5. Never open `/dev/ttyS1` from the dialer. `LibreHU-service` is the single owner of the MCU UART; competing readers steal frames.
6. Display disconnected / unsupported states honestly. Never simulate a real active call after a Binder failure.

**Important:** exact `IBluetooth` AIDL signatures and callback registration must be confirmed from the APK/decompiled source before implementing method calls. Method names alone are not enough to safely reconstruct Binder transaction codes.

## Phone backends

`phone/PhoneModel.kt` defines `PhoneBackend` (link state, call list, dial / answer / reject / hang up / hold / swap /
DTMF / mute / audio route). Each branch provides `phone/PhoneBackends.kt`:

- `main`: `TelecomBackend` + `DialerInCallService` (the app is Android's default phone app; HFP client calls are
  Telecom calls), phone link from the HFP client profile (BluetoothProfile 16).
- `ivi`: `JancarBackend` over the `IBluetooth` Binder of `com.jancar.btservice`.
- `librehu-service`: `LibreHuBackend` over `ILibreHuBluetooth`.

The Compose screens only see `PhoneBackend`; contacts and call history always come from Android's providers.

The goal is to reproduce Android Auto's familiar phone workflow (favorites, recents, contacts, keypad, and call controls) with LibreHU branding and large landscape touch targets. This is a native head-unit UI, not an Android Auto projection app and not an implementation of Google's Android for Cars App Library.

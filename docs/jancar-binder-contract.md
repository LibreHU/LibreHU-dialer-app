# Jancar Binder contract (UJC201 stock ROM)

This contract was extracted from the stock `ivi-btservice.apk`. It is specific to the Jancar service shipped on the UJC201 and must not be assumed compatible with other firmware versions.

## Service binding

- Package: `com.jancar.btservice`
- Component: `com.jancar.btservice.bluetooth.BluetoothService`
- Intent action: `com.jancar.btservice.action.bluetooth`
- Binder interface descriptor: `com.jancar.btservice.bluetooth.IBluetooth`

## IBluetooth transaction IDs used by the initial `ivi` backend

| Transaction | Method | Signature |
|---:|---|---|
| 23 | `callPhone` | `String, IBluetoothExecCallback` |
| 25 | `hangPhone` | `IBluetoothExecCallback` |
| 26 | `rejectPhone` | `IBluetoothExecCallback` |
| 27 | `listenPhone` | `IBluetoothExecCallback` |
| 31 | `requestDTMF` | `int, IBluetoothExecCallback` |
| 32 | `muteMic` | `boolean, IBluetoothExecCallback` |
| 38 | `requestBluetoothListener` | `IBluetoothCallback` |
| 39 | `unrequestBluetoothListener` | `IBluetoothCallback` |
| 52 | `getCurrentDeviceName` | `IBluetoothExecCallback` |
| 70 | `isPowerOn` | `boolean` return |

## Callback transactions

Descriptor: `com.jancar.btservice.bluetooth.IBluetoothCallback`

| Transaction | Callback | Arguments |
|---:|---|---|
| 1 | `onConnectStatus` | `int, String, String` |
| 2 | `onCallStatus` | `int, String, String` |
| 3 | `onVoiceChange` | `int` |
| 4 | `onA2DPConnectStatus` | `int, boolean` |
| 5 | `onBtMusicId3Info` | `String, String, String, long` |
| 6 | `onBtBatteryValue` | `int` |
| 7 | `onBtSignalValue` | `int` |
| 8 | `onPowerStatus` | `boolean` |

Descriptor: `com.jancar.btservice.bluetooth.IBluetoothExecCallback`

| Transaction | Callback | Arguments |
|---:|---|---|
| 1 | `onSuccess` | `String` |
| 2 | `onFailure` | `int` |

## Known limitations

- The integer meanings for connection, call, voice, A2DP, error, battery and signal events are not mapped yet.
- A successful Binder transaction confirms dispatch, not successful call setup or an established call.
- The current dialer still uses demo contacts and call history.
- The callback and transaction protocol must be revalidated if the vendor service APK changes.
- Runtime validation on the stock UJC201 is required before treating the integration as production-ready.

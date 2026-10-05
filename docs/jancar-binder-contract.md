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
| 29 | `transferCall` | `IBluetoothExecCallback` (toggles the call audio car / phone) |
| 59 | `threePartyCallCtrl` | `int action, IBluetoothExecCallback` |
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

## Values (from the decompiled stock APK)

- `onConnectStatus`: 0 disconnected, 1 connected, 2 connecting, 4 pairing (`SettingUtil`).
- `onCallStatus` (`IVIBluetooth.CallStatus`): 0 normal, 1 incoming, 2 outgoing, 3 hang-up, 4 talking, 5 second
  incoming call, 6 held, 7 second outgoing call, 8 two calls (talking), 9 second call ended. Built by
  `DataUtil.toIVICallState` from the HFP client call state (0 active → 4, 1/6 held → 6, 2/3 dialing/alerting → 2,
  4 incoming → 1, 5 waiting → 5, 7 terminated → 3).
- `onVoiceChange`: 1 call audio in the car (SCO connected), 0 on the phone.
- `requestDTMF(int)`: the character code (`'0'`…`'9'`, `'*'`, `'#'`, `'+'`).
- `threePartyCallCtrl(action)`: 0 end the second call, 1 end the current call and answer, 2 answer the waiting call
  (holds the current one), 3 merge, 4 swap.
- Exec callback errors: 2 no device, 3 bad DTMF key, 10 not in a call, 12 unknown action.

Note: btservice's `CallUtil` drives `android.telecom.Call` objects: on the stock ROM Jancar is the Telecom phone app,
which is why this branch does not register an InCallService.

## Known limitations

- Battery and signal ranges are passed through as reported.
- A successful Binder transaction confirms dispatch, not successful call setup or an established call.
- The callback and transaction protocol must be revalidated if the vendor service APK changes.
- Runtime validation on the stock UJC201 is required before treating the integration as production-ready.

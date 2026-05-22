# Munim Bluetooth Example

This React Native app is a physical-device smoke tester for `munim-bluetooth`.
It exercises the Nitro module as both a BLE peripheral and BLE central, and it
also exposes the iOS/iPadOS Apple Multipeer test controls.

## What The Smoke Test Covers

- Requests Bluetooth permissions and prints platform capability flags.
- Configures a test GATT service with read, write, write-without-response,
  notify, and descriptor support.
- Advertises as `MunimBT-ios` or `MunimBT-android`.
- Scans for the test service and shows nearby peers in the Devices section.
- Connects to a peer, discovers services, reads the characteristic, writes a
  new value, subscribes to notifications, reads RSSI, and requests MTU on
  Android.
- Starts Apple Multipeer Connectivity on iOS/iPadOS and shows peer state and
  message events.

## Run It

Install dependencies from the repository root first:

```sh
npm install
```

Install iOS pods when testing iOS:

```sh
cd example
npm run pod
```

Start Metro from the example app:

```sh
cd example
npm run start
```

In another terminal, build and install on Android:

```sh
cd example
npm run android
```

Build and install on iOS:

```sh
cd example
npm run ios
```

For physical iOS devices, open `example/ios/MunimBluetoothExample.xcworkspace`
in Xcode or run `xcodebuild` with your device destination and signing team.

## Two-Device BLE Smoke Test

Use one iOS/iPadOS device and one Android device for the broadest coverage.

1. Install and launch the app on both devices.
2. Accept Bluetooth permission prompts on both devices.
3. On iOS/iPadOS, also accept Local Network prompts when testing Multipeer.
4. Leave both apps on the main screen. Each device advertises its local test
   service and scans for peers.
5. The Android app should discover `MunimBT-ios`, connect, run the GATT smoke
   sequence, and show `1 peer BLE GATT smoke test(s) passed`.

The Android scan callback may report an iOS advertiser by local name without
including the service UUID payload. The example treats `MunimBT-ios` and
`MunimBT-android` as valid smoke-test peers, then verifies the real GATT service
after connecting. Production apps should treat local names as discovery hints
only and validate service/characteristic UUIDs after connection.

## Useful Device Commands

Install a previously built Android debug APK directly:

```sh
adb install -r -d android/app/build/outputs/apk/debug/app-debug.apk
```

Forward Android Metro traffic:

```sh
adb reverse tcp:8081 tcp:8081
```

Launch the Android example:

```sh
adb shell monkey -p com.munimbluetoothexample -c android.intent.category.LAUNCHER 1
```

Filter Android logs for the example:

```sh
adb logcat ReactNativeJS:I HybridMunimBluetooth:D BluetoothGatt:D BluetoothGattServer:D '*:S'
```

List connected iOS/iPadOS devices:

```sh
xcrun xctrace list devices
```

Check whether the physical iOS app process is still running:

```sh
xcrun devicectl device info processes --device <device-id> | rg MunimBluetoothExample
```

## Expected Pass Signals

The main screen should show:

- `Enabled` for Bluetooth state.
- `central=true` and `peripheral=true` in capabilities on BLE-capable devices.
- A peer row such as `MunimBT-ios` or `MunimBT-android`.
- `1 peer BLE GATT smoke test(s) passed` after the Android-to-iOS GATT run.

The logs should include successful service discovery, characteristic read,
characteristic write, notification subscription, RSSI read, and connected-device
count messages.

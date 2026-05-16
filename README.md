<!-- Banner Image -->

<p align="center">
  <a href="https://github.com/munimtechnologies/munim-bluetooth">
    <img alt="Munim Technologies Bluetooth" height="128" src="./.github/resources/banner.png?v=3">
    <h1 align="center">munim-bluetooth</h1>
  </a>
</p>

<p align="center">
   <a aria-label="Package version" href="https://www.npmjs.com/package/munim-bluetooth" target="_blank">
    <img alt="Package version" src="https://img.shields.io/npm/v/munim-bluetooth.svg?style=flat-square&label=Version&labelColor=000000&color=0066CC" />
  </a>
  <a aria-label="Package is free to use" href="https://github.com/munimtechnologies/munim-bluetooth/blob/main/LICENSE" target="_blank">
    <img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-success.svg?style=flat-square&color=33CC12" target="_blank" />
  </a>
  <a aria-label="package downloads" href="https://www.npmtrends.com/munim-bluetooth" target="_blank">
    <img alt="Downloads" src="https://img.shields.io/npm/dm/munim-bluetooth.svg?style=flat-square&labelColor=gray&color=33CC12&label=Downloads" />
  </a>
  <a aria-label="total package downloads" href="https://www.npmjs.com/package/munim-bluetooth" target="_blank">
    <img alt="Total Downloads" src="https://img.shields.io/npm/dt/munim-bluetooth.svg?style=flat-square&labelColor=gray&color=0066CC&label=Total%20Downloads" />
  </a>
</p>

<p align="center">
  <a aria-label="try with expo" href="https://docs.expo.dev/"><b>Works with Expo</b></a>
&ensp;•&ensp;
  <a aria-label="documentation" href="https://github.com/munimtechnologies/munim-bluetooth#readme">Read the Documentation</a>
&ensp;•&ensp;
  <a aria-label="report issues" href="https://github.com/munimtechnologies/munim-bluetooth/issues">Report Issues</a>
</p>

<h6 align="center">Follow Munim Technologies</h6>
<p align="center">
  <a aria-label="Follow Munim Technologies on GitHub" href="https://github.com/munimtechnologies" target="_blank">
    <img alt="Munim Technologies on GitHub" src="https://img.shields.io/badge/GitHub-222222?style=for-the-badge&logo=github&logoColor=white" target="_blank" />
  </a>&nbsp;
  <a aria-label="Follow Munim Technologies on LinkedIn" href="https://linkedin.com/in/sheehanmunim" target="_blank">
    <img alt="Munim Technologies on LinkedIn" src="https://img.shields.io/badge/LinkedIn-0077B5?style=for-the-badge&logo=linkedin&logoColor=white" target="_blank" />
  </a>&nbsp;
  <a aria-label="Visit Munim Technologies Website" href="https://munimtech.com" target="_blank">
    <img alt="Munim Technologies Website" src="https://img.shields.io/badge/Website-0066CC?style=for-the-badge&logo=globe&logoColor=white" target="_blank" />
  </a>
</p>

## Introduction

**munim-bluetooth** is a comprehensive React Native Bluetooth library for BLE central/peripheral work, Android-only Classic Bluetooth APIs, LE L2CAP channels where the OS exposes them, and Apple Multipeer Connectivity for iOS/iPadOS peer messaging. It lets your React Native app advertise services, scan, connect, read, write, subscribe, exchange nearby peer messages, and check platform capabilities before using optional APIs.

**Fully compatible with Expo!** Works seamlessly with both Expo managed and bare workflows.

**Built with React Native's Nitro modules architecture** for high performance and reliability.

**Note**: Bluetooth is heavily platform-gated. This library exposes the features that iOS and Android make available to third-party apps, and it reports unsupported OS-level capabilities through `getCapabilities()` or explicit unsupported errors instead of silently pretending they work.

## Table of contents

- [📚 Documentation](#-documentation)
- [🚀 Features](#-features)
- [Platform Support Matrix](#platform-support-matrix)
- [📦 Installation](#-installation)
- [Device-to-Device Messaging](#device-to-device-messaging)
- [Background and Terminated Behavior](#background-and-terminated-behavior)
- [⚡ Quick Start](#-quick-start)
- [🔧 API Reference](#-api-reference)
- [📖 Usage Examples](#-usage-examples)
- [🔍 Troubleshooting](#-troubleshooting)
- [👏 Contributing](#-contributing)
- [📄 License](#-license)

## 📚 Documentation

<p>Learn about building BLE apps <a aria-label="documentation" href="https://github.com/munimtechnologies/munim-bluetooth#readme">in our documentation!</a></p>

- [Getting Started](#-installation)
- [API Reference](#-api-reference)
- [Usage Examples](#-usage-examples)
- [Troubleshooting](#-troubleshooting)

## 🚀 Features

### Peripheral Mode

- 🔵 **BLE Peripheral Mode**: Transform your React Native app into a BLE peripheral device
- 📡 **Service Advertising**: Advertise custom GATT services with multiple characteristics
- 🔄 **Real-time Communication**: Support for read, write, and notify operations
- ✅ **Platform-Aware BLE Advertising**: Use service UUIDs and local names cross-platform, plus Android advertising payload data where the OS allows it
- 🔧 **Dynamic Updates**: Update advertising data while advertising is active

### Central Mode

- 🔍 **Device Scanning**: Scan for BLE devices with filtering options
- 🔗 **Device Connection**: Connect and disconnect from BLE devices
- 📊 **GATT Operations**: Discover services, read/write characteristics
- 🔔 **Notifications**: Subscribe to characteristic notifications/indications
- 📶 **RSSI Monitoring**: Read signal strength for connected devices

### Additional Features

- 📱 **Cross-platform**: Works on both iOS and Android
- 🧭 **Capability Reporting**: `getCapabilities()` reports platform and hardware support before you call optional APIs
- 🕸️ **Apple Multipeer Transport**: iOS/iPadOS devices can discover, invite, and message nearby peers with Apple's Multipeer Connectivity
- 🧵 **LE L2CAP Channels**: Stream payloads over LE Credit Based Channels on supported iOS and Android versions
- 🔌 **Android Classic Bluetooth**: Android RFCOMM client/server messaging for SPP-style devices
- 🎯 **TypeScript Support**: Full TypeScript definitions included
- ⚡ **High Performance**: Built with React Native's Nitro modules architecture
- 🚀 **Expo Compatible**: Works seamlessly with Expo managed and bare workflows
- 🔐 **Permission Handling**: Built-in permission request helpers

## Platform Support Matrix

| Capability | iOS | Android | Notes |
| --- | --- | --- | --- |
| Peripheral advertising | ✅ | ✅ | iOS only allows CoreBluetooth-supported advertising keys such as local name and service UUIDs. Android splits primary advertising data and scan response data to stay within BLE size limits. |
| Peripheral GATT services | ✅ | ✅ | Read and write requests are handled natively on both platforms. Included services are wired when supplied in `setServices()`. |
| Peripheral notify/indicate subscriptions | ✅ | ✅ | Subscribe/unsubscribe events are emitted when centrals change CCC state. |
| Central scan | ✅ | ✅ | Android scan failures emit `scanFailed`. |
| Central connect/disconnect | ✅ | ✅ | `connect()` has a native 15 second timeout. |
| Central service discovery | ✅ | ✅ | Emits `servicesDiscovered` in addition to resolving the Promise. Native timeout rejects if callbacks do not arrive. |
| Central characteristic read | ✅ | ✅ | Resolves with hex-encoded values. Native timeout rejects if callbacks do not arrive. |
| Central characteristic write | ✅ | ✅ | Supports `write` and `writeWithoutResponse`. With-response writes have native timeout protection. |
| Central descriptor read/write | ✅ | ✅ | Uses `readDescriptor()` and `writeDescriptor()` with hex-encoded values. Native timeout rejects if callbacks do not arrive. |
| Central notify/indicate subscription | ✅ | ✅ | Values emit through `characteristicValueChanged`. |
| RSSI read | ✅ | ✅ | Resolves with dBm. |
| ATT MTU request | ❌ | ✅ | Android supports `requestMTU()`. iOS negotiates ATT MTU internally and does not expose a public setter. |
| BLE PHY read/preference | ❌ | ✅ | Android 8+ supports `readPhy()` and `setPreferredPhy()` when hardware allows it. |
| Pairing/bond state | ❌ | ✅ | Android supports bond state and starts/removes bonds. iOS handles pairing automatically and does not expose bond management through CoreBluetooth. |
| Extended advertising | ❌ | ✅ | Android 8+ supports `startExtendedAdvertising()` on hardware with LE extended advertising. iOS does not expose BLE extended advertising. |
| BLE L2CAP channel streams | ✅ | ✅ | iOS uses CoreBluetooth LE Credit Based Channels. Android requires Android 10+ for LE CoC sockets. |
| Classic Bluetooth RFCOMM | ❌ | ✅ | Android supports discovery, SPP-style RFCOMM client connections, server/listener sockets, disconnect, write, and receive events. iOS apps cannot use public Classic Bluetooth RFCOMM APIs. |
| Apple Multipeer Connectivity | ✅ | ❌ | iOS/iPadOS devices can discover peers, auto-invite/accept sessions, and exchange encrypted messages. Android cannot join Apple's Multipeer sessions; use BLE/GATT for iOS-to-Android. |

Call `getCapabilities()` at runtime when you need optional behavior. Platform support can still vary by OS version, hardware, permissions, and app background state.

## 📦 Installation

### React Native CLI

```bash
npm install munim-bluetooth react-native-nitro-modules
# or
yarn add munim-bluetooth react-native-nitro-modules
```

### Expo

```bash
npx expo install munim-bluetooth react-native-nitro-modules
```

> **Note**: This library requires Expo SDK 50+ and works with both managed and bare workflows. To support Nitro modules, you need React Native version v0.78.0 or higher.
>
> **Important**: This package requires a native development build in Expo. It does not work in Expo Go. After installing, run `npx expo run:ios`, `npx expo run:android`, or create a development build with EAS.

### iOS Setup

For iOS, the library is automatically linked. However, you need to add the following to your `Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app uses Bluetooth for BLE communication</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app uses Bluetooth to create a peripheral device</string>
<key>NSLocalNetworkUsageDescription</key>
<string>This app uses the local network to discover and communicate with nearby peer devices</string>
<key>NSBonjourServices</key>
<array>
  <string>_munim-mesh._tcp</string>
</array>
```

**For Expo projects**, add these permissions to your `app.json`:

```json
{
  "expo": {
    "ios": {
      "infoPlist": {
        "NSBluetoothAlwaysUsageDescription": "This app uses Bluetooth for BLE communication",
        "NSBluetoothPeripheralUsageDescription": "This app uses Bluetooth to create a peripheral device",
        "NSLocalNetworkUsageDescription": "This app uses the local network to discover and communicate with nearby peer devices",
        "NSBonjourServices": ["_munim-mesh._tcp"]
      }
    }
  }
}
```

With the included Expo config plugin, the default `munim-mesh` Multipeer service is declared automatically. For custom service types:

```json
{
  "expo": {
    "plugins": [
      [
        "munim-bluetooth",
        {
          "multipeerServiceTypes": ["anonmesh", "munim-mesh"],
          "localNetworkUsageDescription": "This app discovers nearby private wallet peers."
        }
      ]
    ]
  }
}
```

### Android Setup

For Android, add the following permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

**For Expo projects**, add these permissions to your `app.json`:

```json
{
  "expo": {
    "android": {
      "permissions": [
        "android.permission.BLUETOOTH",
        "android.permission.BLUETOOTH_ADMIN",
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION"
      ]
    }
  }
}
```

## Device-to-Device Messaging

For phone-to-phone apps, treat advertising as discovery and GATT as the reliable message channel:

- A peripheral advertises one or more service UUIDs, defines writable/readable/notifiable characteristics with `setServices()`, and listens for `peripheralReadRequest`, `peripheralWriteRequest`, `peripheralSubscribed`, and `peripheralUnsubscribed`.
- A central scans for that service, connects, discovers services, reads or writes the characteristic, and subscribes for notifications through `characteristicValueChanged`.
- Multiple nearby centrals can connect, write, and subscribe at the same time. Track peers by `deviceId` on the central side and `centralId` on the peripheral side.

Advertising payload caveat: Android can advertise manufacturer data, service data, TX power, appearance, and related fields subject to BLE payload size and hardware limits. iOS public CoreBluetooth peripheral advertising only exposes local name and service UUIDs, so arbitrary relay bytes should go in a GATT characteristic for iOS-to-iOS and iOS-to-Android communication. If you need a tiny discovery hint on iOS, encode it into your advertised service UUID choice or local name with the normal privacy and size tradeoffs.

### Apple Multipeer Connectivity

For iOS-to-iOS or iPadOS-to-iOS communication, `startMultipeerSession()` exposes Apple's Multipeer Connectivity as a higher-level peer transport. It advertises and browses using a Bonjour service type, auto-invites discovered peers by default, accepts incoming invitations by default, and sends hex-encoded payloads to one peer or all connected peers.

```typescript
import {
  addEventListener,
  sendMultipeerMessage,
  startMultipeerSession,
  stopMultipeerSession,
} from 'munim-bluetooth'

startMultipeerSession({
  serviceType: 'munim-mesh',
  displayName: 'Sheehan iPhone',
  discoveryInfo: [{ key: 'role', value: 'wallet-peer' }],
  autoInvite: true,
  autoAcceptInvitations: true,
  encryptionPreference: 'required',
})

addEventListener('multipeerPeerStateChanged', async (peer) => {
  if (peer.state === 'connected') {
    await sendMultipeerMessage('68656c6c6f', [peer.id], true)
  }
})

addEventListener('multipeerMessageReceived', ({ displayName, value }) => {
  console.log('message from', displayName, value)
})

// Later:
stopMultipeerSession()
```

Multipeer service types must be 1-15 lowercase letters/numbers/hyphens, and the matching Bonjour entry must be declared in iOS `Info.plist` as `_<serviceType>._tcp` (for example `_munim-mesh._tcp`). The Expo config plugin adds `_munim-mesh._tcp` by default and accepts a `multipeerServiceTypes` option for custom service types.

## Background and Terminated Behavior

`startBackgroundSession()` starts a best-effort BLE session for apps that need nearby communication after the user leaves the app.

| State | iOS | Android |
| --- | --- | --- |
| App in background or suspended | Supported when `UIBackgroundModes` includes `bluetooth-central` and/or `bluetooth-peripheral`. The Expo config plugin adds both by default. | Supported through a foreground service with `connectedDevice` service type and a user-visible notification. |
| App terminated by the system | Best-effort CoreBluetooth state restoration is enabled when background modes are present. The package uses restoration identifiers and emits `backgroundSessionRestored` when CoreBluetooth restores central/peripheral state. On iOS 26 and later, Apple's Bluetooth relaunch rules require AccessorySetupKit eligibility for background relaunch, so arbitrary phone-to-phone BLE mesh apps should not depend on terminated-state relaunch. | The foreground service persists its session config and uses `START_STICKY`; if the process is recreated, it restores scan, advertising, and a native GATT characteristic store from the services configured with `setServices()`. |
| User force-quits / force-stops the app | Not supported by iOS for ongoing app-owned BLE work. The user has explicitly stopped the app. | Not supported after Android force stop. The OS prevents the app from running again until the user opens it or another allowed user/system action starts it. |

Background sessions are for keeping discovery and small GATT messages alive. They do not make JavaScript execute indefinitely. If the process is alive, normal JS events such as `peripheralWriteRequest` and `characteristicValueChanged` continue. After a system restart, iOS may relaunch the app only when Apple's current CoreBluetooth restoration rules allow it; Android restores native scan/advertise/GATT state in the foreground service, and app-specific business logic should reconcile state when the app opens again.

```typescript
import {
  setServices,
  startBackgroundSession,
  stopBackgroundSession,
} from 'munim-bluetooth'

setServices([
  {
    uuid: SERVICE_UUID,
    characteristics: [
      {
        uuid: CHARACTERISTIC_UUID,
        properties: ['read', 'write', 'writeWithoutResponse', 'notify'],
        value: '70696e67',
      },
    ],
  },
])

startBackgroundSession({
  serviceUUIDs: [SERVICE_UUID],
  localName: 'MunimPeer',
  scanMode: 'lowPower',
  androidNotificationTitle: 'Nearby mode',
  androidNotificationText: 'Keeping Bluetooth available nearby',
})

// Later:
stopBackgroundSession()
```

## ⚡ Quick Start

### Basic Usage - Peripheral Mode

```typescript
import { startAdvertising, stopAdvertising, setServices } from 'munim-bluetooth'

// Start advertising with basic options
startAdvertising({
  serviceUUIDs: ['180D', '180F'],
  localName: 'My Device',
})

// Set GATT services
setServices([
  {
    uuid: '180D',
    characteristics: [
      {
        uuid: '2A37',
        properties: ['read', 'write', 'writeWithoutResponse', 'notify'],
        value: '48656c6c6f20576f726c64',
      },
    ],
  },
])

// Stop advertising
stopAdvertising()
```

### Basic Usage - Central Mode

```typescript
import {
  addDeviceFoundListener,
  isBluetoothEnabled,
  requestBluetoothPermission,
  startScan,
  stopScan,
  connect,
  discoverServices,
  readCharacteristic,
  subscribeToCharacteristic,
} from 'munim-bluetooth'

const hasPermission = await requestBluetoothPermission()
if (!hasPermission) {
  throw new Error('Bluetooth permission was not granted')
}

const enabled = await isBluetoothEnabled()
if (!enabled) {
  throw new Error('Bluetooth is turned off')
}

const removeDeviceFoundListener = addDeviceFoundListener((device) => {
  console.log('Found device:', device.id, device.name)
})

startScan({
  serviceUUIDs: ['180D'],
  allowDuplicates: false,
  scanMode: 'balanced',
})

// Later, after choosing a discovered device ID:
await connect('device-id-here')
const services = await discoverServices('device-id-here')
const value = await readCharacteristic('device-id-here', '180D', '2A37')
subscribeToCharacteristic('device-id-here', '180D', '2A37')

// Cleanup when finished scanning
stopScan()
removeDeviceFoundListener()
```

### Peripheral Write and Subscribe Events

```typescript
import {
  addEventListener,
  setServices,
  startAdvertising,
  updateCharacteristicValue,
} from 'munim-bluetooth'

const SERVICE_UUID = '71f271d0-8f4c-4c4d-8a2d-6f3a9497b41d'
const CHARACTERISTIC_UUID = '4ad4a6d2-3f4a-477c-9832-5e0d8f7654d8'

setServices([
  {
    uuid: SERVICE_UUID,
    characteristics: [
      {
        uuid: CHARACTERISTIC_UUID,
        properties: ['read', 'write', 'writeWithoutResponse', 'notify'],
        value: '70696e67',
      },
    ],
  },
])

addEventListener('peripheralWriteRequest', ({ centralId, value }) => {
  console.log('Peer wrote', centralId, value)
  updateCharacteristicValue(SERVICE_UUID, CHARACTERISTIC_UUID, value, true)
})

addEventListener('peripheralSubscribed', ({ centralId }) => {
  console.log('Peer subscribed', centralId)
  updateCharacteristicValue(SERVICE_UUID, CHARACTERISTIC_UUID, '706f6e67', true)
})

startAdvertising({
  serviceUUIDs: [SERVICE_UUID],
  localName: 'MunimPeer',
})
```

### Advanced Usage with Android Advertising Data Types

```typescript
import {
  startAdvertising,
  updateAdvertisingData,
  getAdvertisingData,
  type AdvertisingDataTypes,
} from 'munim-bluetooth'

// Android advertising data configuration. iOS peripheral advertising only
// broadcasts service UUIDs/local name through public CoreBluetooth APIs.
const advertisingData: AdvertisingDataTypes = {
  // 0x01 - Flags (LE General Discoverable Mode, BR/EDR Not Supported)
  flags: 0x06,

  // 0x02-0x07 - Service UUIDs
  completeServiceUUIDs16: ['180D', '180F'],
  incompleteServiceUUIDs128: ['0000180D-0000-1000-8000-00805F9B34FB'],

  // 0x08-0x09 - Local Name
  completeLocalName: 'My Smart Device',
  shortenedLocalName: 'SmartDev',

  // 0x0A - Tx Power Level
  txPowerLevel: -12,

  // 0x14-0x15 - Service Solicitation
  serviceSolicitationUUIDs16: ['180D'],
  serviceSolicitationUUIDs128: ['0000180D-0000-1000-8000-00805F9B34FB'],

  // 0x16, 0x20, 0x21 - Service Data
  serviceData16: [
    { uuid: '180D', data: '0102030405' },
    { uuid: '180F', data: '060708090A' },
  ],
  serviceData32: [
    { uuid: '0000180D-0000-1000-8000-00805F9B34FB', data: '0B0C0D0E0F' },
  ],

  // 0x19 - Appearance (partial support)
  appearance: 0x03c0, // Generic Watch

  // 0x1F - Service Solicitation (32-bit)
  serviceSolicitationUUIDs32: ['0000180D'],

  // 0xFF - Manufacturer Specific Data
  manufacturerData: '4C000215FDA50693A4E24FB1AFCFC6EB0764782500010001C5',
}

// Start advertising with Android payload data and cross-platform service UUIDs.
startAdvertising({
  serviceUUIDs: ['180D', '180F'],
  advertisingData: advertisingData,
})

// Update advertising data dynamically
updateAdvertisingData({
  flags: 0x04,
  completeLocalName: 'Updated Device Name',
  txPowerLevel: -8,
})

// Get current advertising data
const currentData = await getAdvertisingData()
console.log('Current advertising data:', currentData)
```

## 🔧 API Reference

### Peripheral Functions

#### `startAdvertising(options)`

Starts BLE advertising with the specified options.

**Parameters:**

- `options` (object):
  - `serviceUUIDs` (string[]): Array of service UUIDs to advertise
  - `localName?` (string): Device name (legacy support)
  - `manufacturerData?` (string): Manufacturer data in hex format (legacy Android advertising support)
  - `advertisingData?` (AdvertisingDataTypes): Platform-aware advertising data. Android can advertise payload fields; iOS advertises local name and service UUIDs.

#### `updateAdvertisingData(advertisingData)`

Updates the advertising data while advertising is active.

**Parameters:**

- `advertisingData` (AdvertisingDataTypes): New advertising data

#### `getAdvertisingData()`

Returns a Promise that resolves to the current advertising data.

**Returns:** Promise<AdvertisingDataTypes>

#### `stopAdvertising()`

Stops BLE advertising.

#### `setServices(services)`

Sets GATT services and characteristics.

**Parameters:**

- `services` (array): Array of service objects

#### `updateCharacteristicValue(serviceUUID, characteristicUUID, value, notify)`

Updates a local peripheral characteristic value. When `notify` is `true`, the new hex-encoded value is pushed to subscribed centrals using notify/indicate where the characteristic supports it.

### Central Functions

#### `isBluetoothEnabled()`

Checks if Bluetooth is enabled on the device.

**Returns:** Promise<boolean>

#### `requestBluetoothPermission()`

Requests Bluetooth permissions (Android) or checks authorization status (iOS).

**Returns:** Promise<boolean>

#### `getCapabilities()`

Returns the platform/device Bluetooth feature set.

**Returns:** Promise<BluetoothCapabilities>

#### `startBackgroundSession(options)`

Starts a best-effort background BLE session. Android starts a foreground service that restores scan, advertising, and configured GATT services after normal process recreation. iOS keeps CoreBluetooth managers configured with state restoration identifiers when Bluetooth background modes are present; terminated-state relaunch is still subject to Apple's CoreBluetooth relaunch rules, including the iOS 26 AccessorySetupKit restriction.

**Parameters:**

- `serviceUUIDs` (string[]): Service UUIDs to advertise and scan for.
- `localName?` (string): Local name to advertise where supported.
- `allowDuplicates?` (boolean): Whether scan callbacks may repeat the same device.
- `scanMode?` ('lowPower' | 'balanced' | 'lowLatency'): Android scan mode preference.
- `androidNotificationChannelId?`, `androidNotificationChannelName?`, `androidNotificationTitle?`, `androidNotificationText?`: Android foreground service notification options.

#### `stopBackgroundSession()`

Stops the active background BLE session and clears persisted Android service restore state.

#### `startMultipeerSession(options)`

Starts Apple Multipeer Connectivity discovery and messaging on iOS/iPadOS.

**Parameters:**

- `serviceType` (string): Bonjour service type, 1-15 lowercase letters/numbers/hyphens.
- `displayName?` (string): Name shown to nearby peers.
- `discoveryInfo?` (`{ key: string; value: string }[]`): Small discovery metadata.
- `autoInvite?` (boolean): Automatically invite discovered peers. Defaults to `true`.
- `autoAcceptInvitations?` (boolean): Automatically accept incoming invitations. Defaults to `true`.
- `inviteTimeout?` (number): Invitation timeout in seconds. Defaults to `30`.
- `encryptionPreference?` (`'none' | 'optional' | 'required'`): Defaults to `required`.

#### `stopMultipeerSession()`

Stops the local Multipeer advertiser, browser, and session.

#### `inviteMultipeerPeer(peerId)`

Invites a discovered Multipeer peer when `autoInvite` is disabled or you want manual control.

#### `getMultipeerPeers()`

Returns discovered and connected Multipeer peers for the active runtime session.

#### `sendMultipeerMessage(value, peerIds?, reliable?)`

Sends a hex-encoded payload to connected Multipeer peers. Omit `peerIds` to broadcast to every connected peer. `reliable` defaults to `true`.

#### `startScan(options?)`

Starts scanning for BLE devices.

**Parameters:**

- `options?` (object):
  - `serviceUUIDs?` (string[]): Filter by service UUIDs
  - `allowDuplicates?` (boolean): Allow duplicate scan results
  - `scanMode?` ('lowPower' | 'balanced' | 'lowLatency'): Scan mode

#### `stopScan()`

Stops scanning for BLE devices.

#### `connect(deviceId)`

Connects to a BLE device.

**Parameters:**

- `deviceId` (string): The unique identifier of the device

**Returns:** Promise<void>. The promise rejects if the native connection does not complete within 15 seconds.

#### `disconnect(deviceId)`

Disconnects from a BLE device.

**Parameters:**

- `deviceId` (string): The unique identifier of the device

#### `discoverServices(deviceId)`

Discovers GATT services for a connected device.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device

**Returns:** Promise<GATTService[]>. The promise rejects if native service discovery does not complete within 15 seconds.

#### `readCharacteristic(deviceId, serviceUUID, characteristicUUID)`

Reads a characteristic value from a connected device.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device
- `serviceUUID` (string): The UUID of the service
- `characteristicUUID` (string): The UUID of the characteristic

**Returns:** Promise<CharacteristicValue>. The promise rejects if the native read callback does not arrive within 15 seconds.

#### `readDescriptor(deviceId, serviceUUID, characteristicUUID, descriptorUUID)`

Reads a descriptor value from a connected device.

**Returns:** Promise<DescriptorValue>. The promise rejects if descriptor discovery/read callbacks do not arrive within 15 seconds.

#### `writeCharacteristic(deviceId, serviceUUID, characteristicUUID, value, writeType?)`

Writes a value to a characteristic on a connected device.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device
- `serviceUUID` (string): The UUID of the service
- `characteristicUUID` (string): The UUID of the characteristic
- `value` (string): The value to write (hex string)
- `writeType?` ('write' | 'writeWithoutResponse'): Write type

**Returns:** Promise<void>. With-response writes reject if the native write callback does not arrive within 15 seconds.

#### `writeDescriptor(deviceId, serviceUUID, characteristicUUID, descriptorUUID, value)`

Writes a descriptor value to a connected device.

**Returns:** Promise<void>. The promise rejects if descriptor discovery/write callbacks do not arrive within 15 seconds.

#### `subscribeToCharacteristic(deviceId, serviceUUID, characteristicUUID)`

Subscribes to notifications/indications from a characteristic.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device
- `serviceUUID` (string): The UUID of the service
- `characteristicUUID` (string): The UUID of the characteristic

#### `unsubscribeFromCharacteristic(deviceId, serviceUUID, characteristicUUID)`

Unsubscribes from notifications/indications from a characteristic.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device
- `serviceUUID` (string): The UUID of the service
- `characteristicUUID` (string): The UUID of the characteristic

### Events

Use `addEventListener(eventName, callback)` for BLE status and data events.

| Event | Payload |
| --- | --- |
| `deviceFound` | Discovered BLE device payload: `{ id, name?, localName?, rssi?, serviceUUIDs?, serviceData?, manufacturerData?, txPowerLevel?, isConnectable?, advertisingData? }`. |
| `onDeviceFound`, `scanResult` | Legacy aliases for `deviceFound`. |
| `scanFailed` | `{ errorCode, message }` on Android scan callback failure. |
| `advertisingStarted` | Empty payload when advertising starts. |
| `advertisingStartFailed` | Android: `{ errorCode, message }`; iOS: `{ error }`. |
| `classicDeviceFound` | Android Classic discovery result: `{ id, name, bondState }`. |
| `classicScanFailed`, `classicScanFinished` | Android Classic discovery status events. |
| `classicConnected`, `classicDisconnected` | Android Classic RFCOMM connection status: `{ deviceId }`. |
| `classicConnectionReceived` | Android Classic RFCOMM inbound connection: `{ deviceId }`. |
| `classicServerStarted`, `classicServerStopped` | Android Classic RFCOMM listener status. |
| `classicDataReceived` | Android Classic RFCOMM data: `{ deviceId, value }`. |
| `deviceConnected` | `{ deviceId }` |
| `deviceDisconnected` | `{ deviceId }` |
| `servicesDiscovered` | `{ deviceId, services }` |
| `characteristicValueChanged` | `{ deviceId, serviceUUID, characteristicUUID, value }` |
| `l2capChannelPublished`, `l2capChannelUnpublished` | Local LE L2CAP channel lifecycle status. |
| `l2capChannelOpened`, `l2capChannelClosed` | LE L2CAP stream lifecycle status. |
| `l2capChannelPublishFailed`, `l2capChannelOpenFailed` | LE L2CAP failure status. |
| `l2capDataReceived` | LE L2CAP stream data: `{ channelId, psm, deviceId, value }`. |
| `rssiUpdated` | `{ deviceId, rssi }` |
| `peripheralReadRequest` | `{ centralId, serviceUUID, characteristicUUID, value }` |
| `peripheralWriteRequest` | `{ centralId, serviceUUID, characteristicUUID, value }` |
| `peripheralSubscribed` | `{ centralId, serviceUUID, characteristicUUID }` |
| `peripheralUnsubscribed` | `{ centralId, serviceUUID, characteristicUUID }` |
| `backgroundSessionStarted` | `{ platform, serviceUUIDs?, localName? }` |
| `backgroundSessionStopped` | `{ platform }` |
| `backgroundSessionRestored` | `{ platform, role?, isScanning?, isAdvertising?, serviceUUIDs?, deviceIds? }` |
| `backgroundSessionStartFailed` | `{ platform, error }` |
| `multipeerStarted`, `multipeerStopped`, `multipeerStartFailed` | Apple Multipeer lifecycle status. |
| `multipeerPeerFound`, `multipeerPeerLost`, `multipeerPeerStateChanged` | Apple Multipeer peer discovery and connection state. |
| `multipeerMessageReceived` | Apple Multipeer data: `{ peerId, displayName, value }`. |

#### `getConnectedDevices()`

Gets list of currently connected devices.

**Returns:** Promise<string[]>

#### `readRSSI(deviceId)`

Reads RSSI (signal strength) for a connected device.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device

**Returns:** Promise<number>

#### `requestMTU(deviceId, mtu)`

Requests an ATT MTU on Android. iOS rejects with an unsupported error because CoreBluetooth negotiates MTU internally.

**Returns:** Promise<number>

#### `setPreferredPhy(deviceId, txPhy, rxPhy, phyOption?)`

Sets preferred BLE PHY on Android 8+ when hardware supports it. iOS rejects with an unsupported error.

**Returns:** Promise<void>

#### `readPhy(deviceId)`

Reads the current BLE PHY on Android 8+ when hardware supports it. iOS rejects with an unsupported error.

**Returns:** Promise<PhyStatus>

#### `getBondState(deviceId)`

Returns Android bond state. iOS resolves to `unsupported`.

**Returns:** Promise<BondState>

#### `createBond(deviceId)`

Starts Android pairing/bonding. iOS rejects with an unsupported error.

**Returns:** Promise<BondState>

#### `removeBond(deviceId)`

Removes an Android bond when the OS exposes that operation. iOS rejects with an unsupported error.

**Returns:** Promise<BondState>

#### `startExtendedAdvertising(options)`

Starts an Android BLE extended advertising set on Android 8+ hardware that supports LE extended advertising. iOS rejects with an unsupported error.

**Returns:** Promise<string>

#### `stopExtendedAdvertising(advertisingId)`

Stops an Android BLE extended advertising set.

#### `publishL2CAPChannel()`, `openL2CAPChannel()`, `sendL2CAPData()`

Opens BLE L2CAP channel streams. iOS uses CoreBluetooth LE Credit Based Channels. Android requires Android 10+.

#### `startClassicScan()`, `connectClassic()`, `startClassicServer()`, `writeClassic()`

Android Classic Bluetooth RFCOMM discovery, client connection, server listener, write, disconnect, and receive events. iOS rejects with explicit unsupported errors because public iOS APIs do not expose arbitrary Classic RFCOMM.

### Types

#### `AdvertisingDataTypes`

Platform-aware interface for BLE advertising data types. Android can advertise these payload fields when hardware and payload size allow it. iOS can scan many of these fields from other peripherals, but iOS peripheral advertising is limited to local name and service UUIDs.

```typescript
interface AdvertisingDataTypes {
  // 0x01 - Flags
  flags?: number

  // 0x02-0x07 - Service UUIDs
  incompleteServiceUUIDs16?: string[]
  completeServiceUUIDs16?: string[]
  incompleteServiceUUIDs32?: string[]
  completeServiceUUIDs32?: string[]
  incompleteServiceUUIDs128?: string[]
  completeServiceUUIDs128?: string[]

  // 0x08-0x09 - Local Name
  shortenedLocalName?: string
  completeLocalName?: string

  // 0x0A - Tx Power Level
  txPowerLevel?: number

  // 0x14-0x15 - Service Solicitation
  serviceSolicitationUUIDs16?: string[]
  serviceSolicitationUUIDs128?: string[]

  // 0x16, 0x20, 0x21 - Service Data
  serviceData16?: Array<{
    uuid: string
    data: string
  }>
  serviceData32?: Array<{
    uuid: string
    data: string
  }>
  serviceData128?: Array<{
    uuid: string
    data: string
  }>

  // 0x19 - Appearance
  appearance?: number

  // 0x1F - Service Solicitation (32-bit)
  serviceSolicitationUUIDs32?: string[]

  // 0xFF - Manufacturer Specific Data
  manufacturerData?: string
}
```

## Supported BLE Advertising Data Types

| Hex              | Type Name                     | Description                     | Advertise Support | Example                                           |
| ---------------- | ----------------------------- | ------------------------------- | ----------------- | ------------------------------------------------- |
| 0x01             | Flags                         | Basic device capabilities       | Android           | `flags: 0x06`                                     |
| 0x02-0x07        | Service UUIDs                 | Service UUIDs offered           | iOS + Android     | `completeServiceUUIDs16: ['180D']`                |
| 0x08-0x09        | Local Name                    | Device name                     | iOS + Android     | `completeLocalName: 'My Device'`                  |
| 0x0A             | Tx Power Level                | Transmit power in dBm           | Android           | `txPowerLevel: -12`                               |
| 0x14-0x15        | Service Solicitation          | Services being sought           | Android           | `serviceSolicitationUUIDs16: ['180D']`            |
| 0x16, 0x20, 0x21 | Service Data                  | Data associated with services   | Android           | `serviceData16: [{uuid: '180D', data: '010203'}]` |
| 0x19             | Appearance                    | Appearance category             | Android           | `appearance: 0x03C0`                              |
| 0x1F             | Service Solicitation (32-bit) | 32-bit services being solicited | Android           | `serviceSolicitationUUIDs32: ['0000180D']`        |
| 0xFF             | Manufacturer Specific Data    | Vendor-defined data             | Android           | `manufacturerData: '4748494A4B4C4D4E'`            |

**Note**: This library focuses on reliable phone-to-phone BLE behavior exposed by public iOS and Android APIs. Bluetooth Mesh, LE Audio broadcast/isoc streams, and arbitrary iOS advertising payloads are not exposed by the mobile OS APIs this package can use.

## 📖 Usage Examples

### Health Device Example

```typescript
import { startAdvertising, setServices } from 'munim-bluetooth'

// Health device advertising. The payload fields inside advertisingData are
// advertised on Android; iOS uses serviceUUIDs/localName for advertising.
startAdvertising({
  serviceUUIDs: ['180D', '180F'], // Heart Rate, Battery Service
  advertisingData: {
    flags: 0x06, // LE General Discoverable Mode, BR/EDR Not Supported
    completeLocalName: 'Health Monitor',
    appearance: 0x03c0, // Generic Watch
    txPowerLevel: -8,
    manufacturerData: '0102030405', // Custom health data
    serviceData16: [
      { uuid: '180D', data: '6400' }, // Heart rate: 100 bpm
      { uuid: '180F', data: '64' }, // Battery: 100%
    ],
  },
})

// Set up GATT services
setServices([
  {
    uuid: '180D', // Heart Rate Service
    characteristics: [
      {
        uuid: '2A37', // Heart Rate Measurement
        properties: ['read', 'notify'],
        value: '6400', // 100 bpm
      },
    ],
  },
  {
    uuid: '180F', // Battery Service
    characteristics: [
      {
        uuid: '2A19', // Battery Level
        properties: ['read', 'notify'],
        value: '64', // 100%
      },
    ],
  },
])
```

### Smart Home Device Example

```typescript
import { startAdvertising, updateAdvertisingData } from 'munim-bluetooth'

// Smart home device. The payload fields inside advertisingData are advertised
// on Android; for iOS peers, put live state in GATT characteristics.
startAdvertising({
  serviceUUIDs: ['1812', '180F'], // HID, Battery Service
  advertisingData: {
    flags: 0x04, // LE General Discoverable Mode
    completeLocalName: 'Smart Light Bulb',
    appearance: 0x03c1, // Generic Light Fixture
    manufacturerData: '0102030405', // Custom light data
    serviceData16: [
      { uuid: '1812', data: '01' }, // HID: Keyboard
      { uuid: '180F', data: '64' }, // Battery: 100%
    ],
  },
})

// Update advertising data when light state changes
updateAdvertisingData({
  manufacturerData: '0102030406', // Updated light data
  serviceData16: [
    { uuid: '1812', data: '02' }, // HID: Mouse
    { uuid: '180F', data: '50' }, // Battery: 80%
  ],
})
```

### Basic Peripheral Setup

```js
import React, { useEffect } from 'react'
import { Text } from 'react-native'
import {
  startAdvertising,
  stopAdvertising,
  setServices,
} from 'munim-bluetooth'

const MyPeripheral = () => {
  useEffect(() => {
    // Configure services
    setServices([
      {
        uuid: '1800', // Generic Access Service
        characteristics: [
          {
            uuid: '2a00', // Device Name
            properties: ['read'],
            value: 'MyDevice',
          },
          {
            uuid: '2a01', // Appearance
            properties: ['read'],
            value: '0x03C0', // Generic Computer
          },
        ],
      },
      {
        uuid: '1801', // Generic Attribute Service
        characteristics: [
          {
            uuid: '2a05', // Service Changed
            properties: ['indicate'],
          },
        ],
      },
    ])

    // Start advertising
    startAdvertising({
      serviceUUIDs: ['1800', '1801'],
      localName: 'MyReactNativePeripheral',
    })

    // Cleanup on unmount
    return () => {
      stopAdvertising()
    }
  }, [])

  return <Text>Peripheral is running...</Text>
}
```

### Device Scanner Example

```js
import React, { useState, useEffect } from 'react'
import { Text, View } from 'react-native'
import {
  addDeviceFoundListener,
  addEventListener,
  disconnect,
  isBluetoothEnabled,
  requestBluetoothPermission,
  startScan,
  stopScan,
  connect,
  discoverServices,
  readCharacteristic,
  subscribeToCharacteristic,
} from 'munim-bluetooth'

const DeviceScanner = () => {
  const [devices, setDevices] = useState([])
  const [connectedDevice, setConnectedDevice] = useState(null)

  useEffect(() => {
    let removeDeviceFoundListener = () => {}
    let removeCharacteristicListener = () => {}

    const start = async () => {
      const hasPermission = await requestBluetoothPermission()
      if (!hasPermission) {
        return
      }

      const enabled = await isBluetoothEnabled()
      if (!enabled) {
        return
      }

      removeDeviceFoundListener = addDeviceFoundListener((device) => {
        setDevices((currentDevices) => {
          const alreadyExists = currentDevices.some(
            (currentDevice) => currentDevice.id === device.id
          )

          if (alreadyExists) {
            return currentDevices
          }

          return [...currentDevices, device]
        })
      })

      removeCharacteristicListener = addEventListener(
        'characteristicValueChanged',
        (event) => {
          console.log('Characteristic changed:', event)
        }
      )

      startScan({
        serviceUUIDs: ['180D'], // Filter by Heart Rate service
        allowDuplicates: false,
        scanMode: 'balanced',
      })
    }

    void start()

    // Cleanup
    return () => {
      stopScan()
      removeDeviceFoundListener()
      removeCharacteristicListener()

      if (connectedDevice) {
        disconnect(connectedDevice)
      }
    }
  }, [connectedDevice])

  const handleConnect = async (deviceId) => {
    await connect(deviceId)
    setConnectedDevice(deviceId)

    // Discover services
    const services = await discoverServices(deviceId)
    console.log('Services:', services)

    // Read a characteristic
    const value = await readCharacteristic(deviceId, '180D', '2A37')
    console.log('Heart Rate:', value)

    // Subscribe to notifications
    subscribeToCharacteristic(deviceId, '180D', '2A37')
  }

  return (
    <View>
      <Text>Found {devices.length} devices</Text>
      {/* Render device list */}
    </View>
  )
}
```

## 🔍 Troubleshooting

### Common Issues

1. **Permission Denied**: Ensure you have the necessary Bluetooth permissions in your app
2. **Advertising Not Starting**: Check that Bluetooth is enabled on the device
3. **Services Not Visible**: Verify that your service UUIDs are properly formatted
4. **Scanning Not Working**: On Android 6.0+, ensure location permissions are granted
5. **Connection Fails**: Verify the device is in range and advertising

### Expo-Specific Issues

1. **Development Build Required**: This library requires a development build in Expo. Use `npx expo run:ios`, `npx expo run:android`, or an EAS development build. Expo Go is not supported.
2. **Permissions Not Working**: Make sure you've added the permissions to your `app.json` as shown in the setup section
3. **Build Errors**: Ensure you're using Expo SDK 50+ and have the latest Expo CLI
4. **Nitro Modules**: Make sure you have `react-native-nitro-modules` installed and configured

### Debug Mode

Enable debug logging by setting the following environment variable:

```bash
export REACT_NATIVE_BLUETOOTH_DEBUG=1
```

## 👏 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details on how to submit pull requests, report issues, and contribute to the project.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<img alt="Star the Munim Technologies repo on GitHub to support the project" src="https://user-images.githubusercontent.com/9664363/185428788-d762fd5d-97b3-4f59-8db7-f72405be9677.gif" width="50%">

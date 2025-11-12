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

**munim-bluetooth** is a comprehensive React Native library for all your Bluetooth Low Energy (BLE) needs, supporting both peripheral and central roles. This library allows your React Native app to act as a BLE peripheral (advertising services and characteristics) or as a BLE central (scanning, connecting, and communicating with devices).

**Fully compatible with Expo!** Works seamlessly with both Expo managed and bare workflows.

**Built with React Native's Nitro modules architecture** for high performance and reliability.

**Note**: This library focuses on reliability and platform compatibility. It supports the core BLE features that work consistently across both Android and iOS platforms.

## Table of contents

- [📚 Documentation](#-documentation)
- [🚀 Features](#-features)
- [📦 Installation](#-installation)
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
- ✅ **Platform-Supported BLE Advertising**: Support for core BLE advertising data types that work reliably on both platforms
- 🔧 **Dynamic Updates**: Update advertising data while advertising is active

### Central Mode

- 🔍 **Device Scanning**: Scan for BLE devices with filtering options
- 🔗 **Device Connection**: Connect and disconnect from BLE devices
- 📊 **GATT Operations**: Discover services, read/write characteristics
- 🔔 **Notifications**: Subscribe to characteristic notifications/indications
- 📶 **RSSI Monitoring**: Read signal strength for connected devices

### Additional Features

- 📱 **Cross-platform**: Works on both iOS and Android
- 🎯 **TypeScript Support**: Full TypeScript definitions included
- ⚡ **High Performance**: Built with React Native's Nitro modules architecture
- 🚀 **Expo Compatible**: Works seamlessly with Expo managed and bare workflows
- 🔐 **Permission Handling**: Built-in permission request helpers

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

### iOS Setup

For iOS, the library is automatically linked. However, you need to add the following to your `Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app uses Bluetooth for BLE communication</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app uses Bluetooth to create a peripheral device</string>
```

**For Expo projects**, add these permissions to your `app.json`:

```json
{
  "expo": {
    "ios": {
      "infoPlist": {
        "NSBluetoothAlwaysUsageDescription": "This app uses Bluetooth for BLE communication",
        "NSBluetoothPeripheralUsageDescription": "This app uses Bluetooth to create a peripheral device"
      }
    }
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

## ⚡ Quick Start

### Basic Usage - Peripheral Mode

```typescript
import { startAdvertising, stopAdvertising, setServices } from 'munim-bluetooth'

// Start advertising with basic options
startAdvertising({
  serviceUUIDs: ['180D', '180F'],
  localName: 'My Device',
  manufacturerData: '0102030405',
})

// Set GATT services
setServices([
  {
    uuid: '180D',
    characteristics: [
      {
        uuid: '2A37',
        properties: ['read', 'notify'],
        value: 'Hello World',
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
  startScan,
  stopScan,
  connect,
  discoverServices,
  readCharacteristic,
  subscribeToCharacteristic,
} from 'munim-bluetooth'

// Start scanning
startScan({
  serviceUUIDs: ['180D'],
  allowDuplicates: false,
  scanMode: 'balanced',
})

// Connect to a device (deviceId from deviceFound event)
await connect('device-id-here')

// Discover services
const services = await discoverServices('device-id-here')

// Read a characteristic
const value = await readCharacteristic('device-id-here', '180D', '2A37')

// Subscribe to notifications
subscribeToCharacteristic('device-id-here', '180D', '2A37')
```

### Advanced Usage with Supported Advertising Data Types

```typescript
import {
  startAdvertising,
  updateAdvertisingData,
  getAdvertisingData,
  type AdvertisingDataTypes,
} from 'munim-bluetooth'

// Platform-supported advertising data configuration
const advertisingData: AdvertisingDataTypes = {
  // 0x01 - Flags (LE General Discoverable Mode, BR/EDR Not Supported)
  flags: 0x06,

  // 0x02-0x07 - Service UUIDs (fully supported)
  completeServiceUUIDs16: ['180D', '180F'],
  incompleteServiceUUIDs128: ['0000180D-0000-1000-8000-00805F9B34FB'],

  // 0x08-0x09 - Local Name (fully supported)
  completeLocalName: 'My Smart Device',
  shortenedLocalName: 'SmartDev',

  // 0x0A - Tx Power Level (fully supported)
  txPowerLevel: -12,

  // 0x14-0x15 - Service Solicitation (fully supported)
  serviceSolicitationUUIDs16: ['180D'],
  serviceSolicitationUUIDs128: ['0000180D-0000-1000-8000-00805F9B34FB'],

  // 0x16, 0x20, 0x21 - Service Data (fully supported)
  serviceData16: [
    { uuid: '180D', data: '0102030405' },
    { uuid: '180F', data: '060708090A' },
  ],
  serviceData32: [
    { uuid: '0000180D-0000-1000-8000-00805F9B34FB', data: '0B0C0D0E0F' },
  ],

  // 0x19 - Appearance (partial support)
  appearance: 0x03c0, // Generic Watch

  // 0x1F - Service Solicitation (32-bit) (fully supported)
  serviceSolicitationUUIDs32: ['0000180D'],

  // 0xFF - Manufacturer Specific Data (fully supported)
  manufacturerData: '4C000215FDA50693A4E24FB1AFCFC6EB0764782500010001C5',
}

// Start advertising with supported data
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
  - `manufacturerData?` (string): Manufacturer data in hex format (legacy support)
  - `advertisingData?` (AdvertisingDataTypes): Platform-supported advertising data

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

### Central Functions

#### `isBluetoothEnabled()`

Checks if Bluetooth is enabled on the device.

**Returns:** Promise<boolean>

#### `requestBluetoothPermission()`

Requests Bluetooth permissions (Android) or checks authorization status (iOS).

**Returns:** Promise<boolean>

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

**Returns:** Promise<void>

#### `disconnect(deviceId)`

Disconnects from a BLE device.

**Parameters:**

- `deviceId` (string): The unique identifier of the device

#### `discoverServices(deviceId)`

Discovers GATT services for a connected device.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device

**Returns:** Promise<GATTService[]>

#### `readCharacteristic(deviceId, serviceUUID, characteristicUUID)`

Reads a characteristic value from a connected device.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device
- `serviceUUID` (string): The UUID of the service
- `characteristicUUID` (string): The UUID of the characteristic

**Returns:** Promise<CharacteristicValue>

#### `writeCharacteristic(deviceId, serviceUUID, characteristicUUID, value, writeType?)`

Writes a value to a characteristic on a connected device.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device
- `serviceUUID` (string): The UUID of the service
- `characteristicUUID` (string): The UUID of the characteristic
- `value` (string): The value to write (hex string)
- `writeType?` ('write' | 'writeWithoutResponse'): Write type

**Returns:** Promise<void>

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

#### `getConnectedDevices()`

Gets list of currently connected devices.

**Returns:** Promise<string[]>

#### `readRSSI(deviceId)`

Reads RSSI (signal strength) for a connected device.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device

**Returns:** Promise<number>

### Types

#### `AdvertisingDataTypes`

Platform-supported interface for BLE advertising data types:

```typescript
interface AdvertisingDataTypes {
  // 0x01 - Flags (partial support)
  flags?: number

  // 0x02-0x07 - Service UUIDs (fully supported)
  incompleteServiceUUIDs16?: string[]
  completeServiceUUIDs16?: string[]
  incompleteServiceUUIDs32?: string[]
  completeServiceUUIDs32?: string[]
  incompleteServiceUUIDs128?: string[]
  completeServiceUUIDs128?: string[]

  // 0x08-0x09 - Local Name (fully supported)
  shortenedLocalName?: string
  completeLocalName?: string

  // 0x0A - Tx Power Level (fully supported)
  txPowerLevel?: number

  // 0x14-0x15 - Service Solicitation (fully supported)
  serviceSolicitationUUIDs16?: string[]
  serviceSolicitationUUIDs128?: string[]

  // 0x16, 0x20, 0x21 - Service Data (fully supported)
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

  // 0x19 - Appearance (partial support)
  appearance?: number

  // 0x1F - Service Solicitation (32-bit) (fully supported)
  serviceSolicitationUUIDs32?: string[]

  // 0xFF - Manufacturer Specific Data (fully supported)
  manufacturerData?: string
}
```

## Supported BLE Advertising Data Types

| Hex              | Type Name                     | Description                     | Support Level | Example                                           |
| ---------------- | ----------------------------- | ------------------------------- | ------------- | ------------------------------------------------- |
| 0x01             | Flags                         | Basic device capabilities       | Partial       | `flags: 0x06`                                     |
| 0x02-0x07        | Service UUIDs                 | Service UUIDs offered           | Full          | `completeServiceUUIDs16: ['180D']`                |
| 0x08-0x09        | Local Name                    | Device name                     | Full          | `completeLocalName: 'My Device'`                  |
| 0x0A             | Tx Power Level                | Transmit power in dBm           | Full          | `txPowerLevel: -12`                               |
| 0x14-0x15        | Service Solicitation          | Services being sought           | Full          | `serviceSolicitationUUIDs16: ['180D']`            |
| 0x16, 0x20, 0x21 | Service Data                  | Data associated with services   | Full          | `serviceData16: [{uuid: '180D', data: '010203'}]` |
| 0x19             | Appearance                    | Appearance category             | Partial       | `appearance: 0x03C0`                              |
| 0x1F             | Service Solicitation (32-bit) | 32-bit services being solicited | Full          | `serviceSolicitationUUIDs32: ['0000180D']`        |
| 0xFF             | Manufacturer Specific Data    | Vendor-defined data             | Full          | `manufacturerData: '4748494A4B4C4D4E'`            |

**Note**: This library focuses on reliability and platform compatibility. Advanced BLE features like mesh networking, LE Audio, indoor positioning, etc., are not supported due to platform limitations.

## 📖 Usage Examples

### Health Device Example

```typescript
import { startAdvertising, setServices } from 'munim-bluetooth'

// Health device advertising
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

// Smart home device
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
import {
  startAdvertising,
  stopAdvertising,
  setServices,
  addListener,
  removeListeners,
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
      removeListeners('connectionStateChanged')
    }
  }, [])

  return <Text>Peripheral is running...</Text>
}
```

### Device Scanner Example

```js
import React, { useState, useEffect } from 'react'
import {
  startScan,
  stopScan,
  connect,
  discoverServices,
  readCharacteristic,
  subscribeToCharacteristic,
  addListener,
} from 'munim-bluetooth'

const DeviceScanner = () => {
  const [devices, setDevices] = useState([])
  const [connectedDevice, setConnectedDevice] = useState(null)

  useEffect(() => {
    // Start scanning
    startScan({
      serviceUUIDs: ['180D'], // Filter by Heart Rate service
      allowDuplicates: false,
      scanMode: 'balanced',
    })

    // Listen for discovered devices
    addListener('deviceFound')
    // Handle deviceFound events to update devices state

    // Cleanup
    return () => {
      stopScan()
      if (connectedDevice) {
        disconnect(connectedDevice)
      }
    }
  }, [])

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

    // Listen for value changes
    addListener('characteristicValueChanged')
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

1. **Development Build Required**: This library requires a development build in Expo. Use `npx expo run:ios` or `npx expo run:android`
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
